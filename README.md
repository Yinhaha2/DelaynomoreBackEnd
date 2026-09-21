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

接口规范：

- 前端选集卡片（**把这一份给前端即可**）：`docs/2026-09-09_接口文档_1.md`
- 完整能力汇总：`docs/2026-09-07_接口文档_1.md`
- 联调步骤：`docs/2026-09-07_联调指南.md`

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
│   ├── webview/         # Playwright Chromium（useWebview 规则）
│   ├── fallback/        # YHDM / SiliSili 专用降级解析（插件拿不到播放地址时）
│   ├── reliability/     # 按上游熔断、单飞、后台嗅探
│   ├── cache/           # 爬虫出口 Caffeine / Redis 缓存
│   └── service/         # 定向爬取编排
├── service/             # 会话 / 对话业务
└── store/               # 内存会话存储
```

### 爬虫规则（Kazumi 兼容）

站点规则放在 `src/main/resources/plugins/*.json`，字段与 Kazumi 插件一致，例如：

- `searchURL` + `@keyword` 占位符
- `searchList` / `searchName` / `searchResult` XPath 选择器
- `chapterRoads` / `chapterResult` 章节线路解析
- 扩展字段 `searchImage` 用于封面图提取（`src` / `data-src` / `srcset`）
- 插件可带 `cookie` 与 `headers`
- `useWebview: true` 时用 Playwright 渲染页面，并拦截网络里的 `.m3u8` / `.mp4`（以及播放器里的 `player_aaaa.url`）

插件先抓；若没有播放地址，再降级到写死选择器的 **YHDM（樱花动漫）** / **SiliSili**，实现参考 [SakuraAnime](https://github.com/670848654/SakuraAnime)。域名可配，站点一改版只动 adapter：

```yaml
agent:
  crawler:
    fallback:
      enabled: true
      yhdm:
        base-url: http://www.iyinghua.io
      silisili:
        base-url: https://www.silisili.link
        cookie: silisili=on
```

环境变量：`AGENT_YHDM_BASE_URL`、`AGENT_SILISILI_BASE_URL`。用户指定「樱花」/「嘶哩嘶哩」时会优先走对应 adapter。

OkHttp 会跟 HTML 的 `meta refresh` 和 “verified successfully” 跳转页（HTTP 3xx 之外的那一类）。播放地址会解开 `?url=` 包装，并识别 `changeplay('...')`。

本机首次使用 WebView 前需要安装 Chromium：

```bash
mvn exec:java -Dexec.args="install chromium"
```

云主机 / IDC 出口访问部分站点可能仍是 HTTP 522（源站对机房 IP 不可达），这和本机家宽打开同一域名不是同一条网络路径。`useWebview` 解决的是 **JS 渲染后的 DOM 与播放地址**，不能改变源站是否对当前出口放行。

可在 `application.yml` 关闭浏览器抓取（单元测试默认关闭，避免 CI 拉 Chromium）：

```yaml
agent:
  crawler:
    webview:
      enabled: false
```

Chromium 不可用时会自动回退到 OkHttp 静态 HTML。

### 熔断、单飞与 Playwright 舱壁（P0）

按上游（`plugin:DM84` / `fallback:YHDM` / `fallback:SiliSili`）隔离：1 分钟内 5 次超时或 5xx 则打开熔断，**用户请求直接跳过该源**，去打其它线路。空搜（这部番没有）不算失败。冷却约 3 分钟后由后台定时任务对站点首页做一次 canary GET，成功才关闭熔断，嗅探不走用户 SSE。

同一 `site + keyword` 的并发检索会合并成一次真实爬取（singleflight，不是缓存）。Playwright 同时最多 2 个页面，挤不进去时回退 OkHttp。

```yaml
agent:
  crawler:
    reliability:
      enabled: true
      open-wait-seconds: 180
      probe-interval-seconds: 30
      playwright-max-concurrent: 2
```

单元测试默认 `reliability.enabled: false`。

### 爬虫出口缓存（P1）

贴在 `ResourceCrawlerService.crawl` 出口，**不进 LLM**。LLM 仍只拿线路摘要；SSE `resource_bundle` 在播放缓存命中时直接下发。

| 层 | Key | TTL | 内容 |
|----|-----|-----|------|
| play | `acrawl:play:{site}:{keyword}` | **2～15 分钟**（默认 10 分钟，硬顶 15 分钟） | 完整 `CrawlResourceResult`（含可播 URL） |
| catalog | `acrawl:catalog:{site}:{keyword}` | **1～12 小时**（默认 8 小时） | 作品名 / 线路名 / 集标题 / 源页面，**无签名直链** |

默认只用进程内 **Caffeine**。多实例时打开 Redis 作 L2；Redis 超时或挂掉只打日志，继续用 Caffeine 或直连爬虫。失败结果不缓存。点播 403 的 `play_refresh` 还没做。

```yaml
agent:
  crawler:
    cache:
      enabled: true
      play-ttl-seconds: 600
      catalog-ttl-seconds: 28800
      redis:
        enabled: false          # 部署 Redis 后改为 true
        host: 127.0.0.1
        port: 6379
```

环境变量：`AGENT_REDIS_ENABLED`、`AGENT_REDIS_HOST`、`AGENT_REDIS_PORT`、`AGENT_REDIS_PASSWORD`。同一组变量也给会话持久化用。

### 会话持久化（meta / 黑板 / ChatMemory）

和爬虫缓存共用 Redis 连接，**换前缀、换寿命**，避免把 7 天会话和 10 分钟播放 URL 写在一起。

| Key | TTL | 内容 |
|-----|-----|------|
| `asess:meta:{conversationId}` | 滑动 **7 天**（可配 1 小时～30 天） | id / title / createdAt / updatedAt |
| `asess:board:{conversationId}` | 同上 | 锁定作品、角色、集数（实体黑板） |
| `asess:mem:{conversationId}` | 同上 | LangChain4j 最近 N 条 ChatMessage |

默认 Redis 关闭时只在进程内。打开 `AGENT_REDIS_ENABLED` 后：重启或第二台 API 仍能接着聊；Redis 挂了降级本地，不堵请求。换题只清黑板，会话 id 和 ChatMemory 保留。爬虫全量 URL（`CrawlResultBuffer`）**不写 Redis**。

```yaml
agent:
  session:
    enabled: true
    ttl-seconds: 604800
    key-prefix: asess
```

### LangChain4j Agent 编排

三大积木：

| 组件 | 实现 |
|------|------|
| System Prompt | `AnimeAgent` 接口 `@SystemMessage` |
| ChatMemory | `MessageWindowChatMemory` + `SessionChatMemoryStore`（默认 4 条，可进 Redis） |
| Tool | `ResourceCrawlTools.searchResources(keyword, site)` |

多轮对话：前端在 `POST /api/v1/chat/stream` 请求体中携带同一 `conversation_id`，LangChain4j 通过 `@MemoryId` 自动加载滑动窗口历史，支持「它的第二季有吗？」等指代消歧。

### LangChain4j 工具

`ResourceCrawlTools.searchResources(keyword, site)` 被 Agent 调用：

- `keyword`：搜索关键词（番剧名、季数等）
- `site`：插件名（如 `DM84`）或站点 baseURL，默认 `DM84`

Agent 会将爬取结果转为 SSE 的 `resource_bundle`（按线路分组的选集）以及兼容用的 `video` / `link` / `image` 块；`text_delta` 只承载短推荐语，不再倾倒播放 URL。

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
| `AGENT_REDIS_ENABLED` | 是否启用 Redis（爬虫缓存 L2 + 会话持久化） | `false` |
| `AGENT_REDIS_HOST` | Redis 地址 | `127.0.0.1` |
| `AGENT_REDIS_PORT` | Redis 端口 | `6379` |
| `AGENT_REDIS_PASSWORD` | Redis 密码，可空 | 空 |
| `AGENT_REDIS_DB` | Redis DB index | `0` |

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

点播 403 的 `play_refresh` 仍未做。
