# Agent Crawler 后端

基于 **Spring Boot + LangChain4j** 的对话 API，内置参考 [Kazumi](https://github.com/Predidit/Kazumi) 规则引擎的定向资源爬虫（视频 / 链接 / 图片）。

## 快速启动

```bash
mvn spring-boot:run
```

服务默认监听 `http://localhost:8000`。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/conversations` | 创建会话 |
| `POST` | `/api/v1/chat/stream` | 发送消息，SSE 流式返回 |

接口规范见 `docs/2026-09-03_接口文档_1.md`。前后端联调步骤见 `docs/2026-09-07_联调指南.md`。

## 架构

```
src/main/java/com/agentcrawler/
├── api/                 # REST / SSE 控制器
├── streaming/           # 通用 SSE 回复框架（text/link/image/done/error）
├── agent/langchain/     # LangChain4j Agent + @Tool 爬虫工具
├── crawler/
│   ├── engine/          # Kazumi 风格 RuleEngine（XPath 搜索 + 章节解析）
│   ├── media/           # 视频 m3u8/mp4、图片、链接提取
│   ├── plugin/          # 站点规则注册（plugins/*.json）
│   └── service/         # 定向爬取编排
├── service/             # 会话 / 对话业务
└── store/               # 内存会话存储
```

### 爬虫规则（Kazumi 兼容）

站点规则放在 `src/main/resources/plugins/*.json`，字段与 Kazumi 插件一致，例如：

- `searchURL` + `@keyword` 占位符
- `searchList` / `searchName` / `searchResult` XPath 选择器
- `chapterRoads` / `chapterResult` 章节线路解析
- 扩展字段 `searchImage` 用于封面图提取

### LangChain4j Agent 编排

三大积木：

| 组件 | 实现 |
|------|------|
| System Prompt | `AnimeAgent` 接口 `@SystemMessage` |
| ChatMemory | `MessageWindowChatMemory`（每会话独立，默认 10 条） |
| Tool | `ResourceCrawlTools.searchResources(keyword, site)` |

多轮对话：前端在 `POST /api/v1/chat/stream` 请求体中携带同一 `conversation_id`，LangChain4j 通过 `@MemoryId` 自动加载滑动窗口历史，支持「它的第二季有吗？」等指代消歧。

### LangChain4j 工具

`ResourceCrawlTools.searchResources(keyword, site)` 被 Agent 调用：

- `keyword`：搜索关键词（番剧名、季数等）
- `site`：插件名（如 `DM84`）或站点 baseURL，默认 `DM84`

Agent 会将爬取结果转为 SSE 的 `text_delta` / `video` / `link` / `image` 块推送给前端。

### 链接前置解析

用户消息里的 HTTP(S) / magnet 由 Java **确定性提取**（不靠 LLM 抠 URL），再分类嗅探：

- 网页：Jsoup 读 OpenGraph / `<title>`，提炼作品名
- 短链：跟随重定向后重新分类（b23.tv / t.cn 等）
- 视频站：提取 BV 号 / YouTube ID
- 磁力链：解析 InfoHash 与 `dn` 文件名
- 直链：识别 `.m3u8` / `.mp4`

解析成功后自动写入实体黑板，并把结构化事实注入 Agent Prompt。Agent 仍可通过 `inspectLink(url)` 补充解析。

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DEEPSEEK_API_KEY` | DeepSeek API Key（**必填**以启用 LLM Agent） | 空 |
| `DEEPSEEK_BASE_URL` | DeepSeek OpenAI 兼容地址 | `https://api.deepseek.com/v1` |
| `DEEPSEEK_MODEL` | 模型名称 | `deepseek-chat` |

启动前配置（勿将 Key 提交到 Git）：

```bash
cp .env.example .env
# 编辑 .env 填入 DEEPSEEK_API_KEY
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

未配置 `DEEPSEEK_API_KEY` 时，后端降级为启发式检索（仅识别《番剧名》格式，无多轮 LLM）。

## 前端联调

详见 `docs/2026-09-07_联调指南.md`。最短路径：

```env
VITE_USE_MOCK_STREAM=false
```

Vite 代理 `/api` → `http://localhost:8000`。先打通纯文本 SSE，再测发图（先上传再 attachments），链接直接写在 `message` 里即可。

## 测试

```bash
mvn test
```

## 后续扩展

- Playwright 解析需 WebView 的播放页（Kazumi `useWebview` 场景）
- ChatMemory 持久化到 Redis / DB
