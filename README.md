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

接口规范见 `docs/2026-09-03_接口文档_1.md`。

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

### LangChain4j 工具

`ResourceCrawlTools.crawlResources(keyword, site)` 被 Agent 调用：

- `keyword`：搜索关键词（番剧名等）
- `site`：插件名（如 `DM84`）或站点 baseURL

Agent 会将爬取结果转为 SSE 的 `text_delta` / `link` / `image` 块推送给前端。

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `OPENAI_API_KEY` | OpenAI Key（可选，用于 LLM 意图理解） | 空 |
| `OPENAI_BASE_URL` | OpenAI 兼容 API 地址 | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | 模型名称 | `gpt-4o-mini` |

未配置 `OPENAI_API_KEY` 时，后端会使用启发式解析（识别《番剧名》等）直接调用爬虫工具。

## 前端联调

```env
VITE_USE_MOCK_STREAM=false
```

Vite 代理 `/api` → `http://localhost:8000`。

## 测试

```bash
mvn test
```

## 后续扩展

- 接入 Kazumi 更多 API 模式插件（`searchMode=api`）
- Playwright 解析需 WebView 的播放页（Kazumi `useWebview` 场景）
- 持久化会话与爬取结果
