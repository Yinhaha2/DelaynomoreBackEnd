# Agent 视频资源爬取后端

该项目旨在通过自然语言对话，帮助用户在不手动翻站选源的情况下完成番剧资源检索与选集交付。后端以 SSE 流式返回按线路分组的选集卡片与短推荐语，并在源站超时或不可达时自动跳过坏源、改走其它线路。

**技术栈：** Spring Boot、LangChain4j、DeepSeek LLM、Kazumi 规则引擎、Playwright、OkHttp / Jsoup / XPath

## 总体架构

一次对话请求从 `POST /api/v1/chat/stream` 进入，先做会话锚定与确定性链接解析，再交给 LangChain4j Agent 编排工具。爬虫产出的播放 URL **不进 LLM 上下文**：全量结果走 `CrawlResultBuffer` 直推 SSE `resource_bundle`，模型只吃线路/集数摘要并写推荐语。用户消息里贴的 HTTP / magnet 走另一条确定性管线（提取 → 分类 → SSRF 拦截 → 嗅探），解析结果写入会话黑板后再注入 Prompt。

```mermaid
flowchart TB
  Client["客户端"]
  API["ChatController<br/>POST /api/v1/chat/stream"]
  Chat["ChatService"]
  Agent["LangChainStreamingAgent"]

  subgraph pre["请求前置"]
    BB["会话黑板<br/>作品 / 角色 / 集数"]
    Link["确定性链接管线<br/>extract → classify → SSRF → sniff"]
    Mem["ChatMemory 滑动窗口"]
  end

  subgraph orch["Agent 编排"]
    DS["DeepSeek LLM"]
    Tools["searchResources / inspectLink / 识图"]
  end

  subgraph crawl["定向爬取"]
    SF["Singleflight<br/>site + keyword"]
    Cache["Caffeine L1 + Redis L2<br/>acrawl:play 短 TTL"]
    Circuit["SiteCircuitBoard<br/>按上游熔断"]
    Plugins["Kazumi RuleEngine<br/>插件 JSON / XPath"]
    PW["Playwright WebView<br/>拦截 m3u8 / mp4"]
    FB["YHDM / SiliSili 降级"]
  end

  subgraph out["双通道出口"]
    Buf["CrawlResultBuffer<br/>全量结果含播放 URL"]
    Summary["CrawlToolSummary<br/>仅线路名与集数"]
    SSE["SSE<br/>text / resource_bundle / video / link / image / done / error"]
  end

  Redis["Redis 可选<br/>asess:* 会话  ·  acrawl:* 爬虫"]

  Client --> API --> Chat --> Agent
  Agent --> BB
  Agent --> Link
  Agent --> Mem
  Agent --> DS
  DS --> Tools
  Tools --> SF --> Cache
  Cache --> Circuit
  Circuit --> Plugins
  Circuit --> FB
  Plugins --> PW
  Tools --> Buf
  Tools --> Summary
  Buf --> SSE
  Summary --> DS
  DS --> SSE
  SSE --> Client
  BB -.-> Redis
  Mem -.-> Redis
  Cache -.-> Redis
```

两条 URL 路径互不混用：

| 路径 | 入口 | 处理 | 播放 URL 去向 |
|------|------|------|----------------|
| 用户粘贴的链接 / magnet | `LinkInspectorService.inspectMessage` | Java 正则提取 + 分类嗅探 + SSRF 拦截 | **不爬选集**；结构化事实写入黑板并注入 Prompt |
| 站点检索出的选集 | `searchResources` → `ResourceCrawlerService` | 插件 / Playwright / 降级 adapter | 进 `CrawlResultBuffer`，由 SSE `resource_bundle` 直推前端，**禁止灌进 LLM** |

无 `DEEPSEEK_API_KEY` 时 Agent Bean 不装配，同一套爬虫走启发式检索（识别《番剧名》），链路仍可联调。

## 我的工作

### 1. 意图理解与工具编排

基于 LangChain4j 搭建多轮对话 Agent（System Prompt、滑动窗口 ChatMemory、Tool Calling），将「搜番 / 指代消歧 / 看图识番」落到 `searchResources`、`inspectLink` 等工具。

用户消息中的 HTTP(S) / magnet 由 Java **确定性提取**并分类嗅探（网页 OpenGraph、短链跳转、B 站 / YouTube、磁力 InfoHash、m3u8/mp4 直链），解析结果写入会话黑板后再注入 Prompt，避免让大模型直接抠 URL。换题检测会清空黑板；爬虫或识图成功后把作品名同步回黑板，下一轮以锚定 Prompt 开头。

```mermaid
flowchart TD
  Msg["用户消息 + 可选图片附件"]
  BB["SessionBlackboardService.onUserMessage<br/>换题则清空，否则抽作品 / 角色 / 集数"]
  Ext["LinkExtractor<br/>正则切出 HTTP(S) 与 magnet"]

  Msg --> BB
  Msg --> Ext

  Ext --> Kind{"UrlClassifier"}
  Kind -->|magnet| Mag["MagnetLinkParser<br/>InfoHash / 展示名"]
  Kind -->|m3u8 mp4| Stream["直链 STREAM<br/>不发起页面抓取"]
  Kind -->|http| Safe{"LinkSafety<br/>拦截 localhost / 内网 / 非 http"}

  Safe -->|BLOCKED| Block["标记拒绝探测 SSRF"]
  Safe -->|放行| Http["LinkHttpClient 跟随跳转"]
  Http --> Reclass["按 finalUrl 再分类"]
  Reclass --> OG["OpenGraph + VideoSiteParser<br/>B 站 / YouTube / 短链还原"]

  Mag --> Lock
  Stream --> Lock
  OG --> Lock
  Block --> Enrich
  Lock["置信度达标则 blackboard.lockContext"]
  Lock --> Enrich["LinkMessageEnricher<br/>结构化事实前置进 Prompt"]
  Enrich --> Agent["AnimeAgent.chat<br/>LangChain4j Tool Calling"]

  Agent --> T1["searchResources<br/>关键词必须是当前作品名"]
  Agent --> T2["inspectLink<br/>仅补充解析未覆盖 URL"]
  Agent --> T3["识图 Tool<br/>DeepSeek Vision → 锁作品"]
  Agent --> Mem["SessionChatMemoryStore<br/>asess:mem 滑动窗口，不含播放 URL"]
```

数据在这一层的处理要点：

- **提取是确定性的**：`LinkExtractor` 用正则切 URL，避免 LLM 吞掉 query 或把 magnet 截断。
- **嗅探前先 SSRF**：非 http(s)、localhost、链路本地 / 站点本地地址直接 `BLOCKED`，不发请求。
- **模型看到的是事实卡片**：标题、锁定作品、集数线索、站点、InfoHash；真正的选集播放地址不经过这条路径。

### 2. 定向爬取与解析

参考 [Kazumi](https://github.com/Predidit/Kazumi) 实现站点规则引擎，用插件 JSON（`searchURL`、XPath 列表 / 章节选择器、cookie/headers）抓取搜索结果与选集；`useWebview` 站点走 Playwright 拦截 `.m3u8` / `.mp4`。插件拿不到播放地址时降级到 YHDM / SiliSili 专用 adapter。

爬取结果清洗为 `resource_bundle`（标题、线路、集数、可播链接），LLM 只吃摘要，播放 URL 不灌进上下文。同 `site + keyword` 的并发请求在缓存查找外层做单飞合并；播放结果写入短 TTL 缓存（默认 10 分钟，硬顶 15 分钟，适配带 `X-Amz-Expires` 的签名直链），目录快照另存长 TTL（默认 8 小时）。

```mermaid
flowchart TD
  Tool["searchResources(keyword, site)"]
  SF["CrawlSingleflight<br/>合并同 site+keyword 的在途请求"]
  PlayHit{"Caffeine / Redis<br/>acrawl:play 命中?"}

  Tool --> SF --> PlayHit
  PlayHit -->|是| Split
  PlayHit -->|否| Unc["crawlUncoalesced"]

  Unc --> Ded{"站点是否匹配专用 adapter?"}
  Ded -->|是且未熔断| F1["YHDM / SiliSili 先爬"]
  F1 -->|拿到 videos| Split
  F1 -->|没有播放地址| Plug
  Ded -->|否| Plug["crawlPlugins"]

  Plug --> Rules["PluginRegistry 解析插件 JSON"]
  Rules --> Search["RuleEngine.search<br/>searchURL 模板 + XPath 列表"]
  Search --> Chap["queryChapters<br/>线路 / 集数 URL"]
  Chap --> Page["fetchPageDetailed"]

  Page --> WV{"useWebview?"}
  WV -->|是| PW["Playwright 最多 2 路<br/>拦截请求中的 m3u8/mp4"]
  PW -->|槽位耗尽或失败| OK["回退 OkHttp + Jsoup"]
  WV -->|否| OK
  OK --> Media["MediaExtractor<br/>HTML 中的直链 / 图片"]

  Plug -->|仍无 videos| F2["遍历其它 Fallback adapter"]
  F2 --> Split
  Media --> Split
  PW --> Split

  Split["产出 CrawlResourceResult"]
  Split --> Full["CrawlResultBuffer.push<br/>含真实播放 URL"]
  Split --> Sum["CrawlToolSummary JSON<br/>ok / 线路名 / 集数 / 无 URL"]
  Split --> CachePut["putPlay 2–15 min<br/>putCatalog 1–12 h"]

  Full --> SSE["onToolExecuted<br/>CrawlResultEmitter → resource_bundle"]
  Sum --> LLM["交回 DeepSeek<br/>只写 2～3 句推荐，禁止复述链接"]
```

缓存与会话键前缀隔离：爬虫 `acrawl:play` / `acrawl:catalog`，会话 `asess:meta` / `asess:board` / `asess:mem`。Redis 不可用时读写失败留在本地，不让一次缓存故障打垮检索。

### 3. 流式交付与可靠性

实现会话创建与 `POST /api/v1/chat/stream` SSE 推流（`text` / `resource_bundle` / `video` / `link` / `image` / `done` / `error`）。

按上游做熔断（1 分钟内多次超时或 5xx 则跳过该源）、同 `site + keyword` 单飞合并爬取、Playwright 全局最多 2 路并发，挤不进去回退 OkHttp；后台定时对熔断源做 canary 嗅探，探测不走用户请求路径。无 `DEEPSEEK_API_KEY` 时降级为启发式检索，保证链路可联调。

```mermaid
flowchart LR
  subgraph req["用户请求路径"]
    In["streamChat"]
    Gate{"SiteCircuitBoard.allowRequest"}
    In --> Gate
    Gate -->|CLOSED| Crawl["插件 / Fallback 抓取"]
    Gate -->|OPEN| Skip["跳过该上游<br/>用户流量永不探 Half-Open"]
    Crawl --> Class{"UpstreamFailureClassifier"}
    Class -->|超时 / 5xx| Fail["recordFailure"]
    Class -->|空目录或业务空结果| Ok["recordSuccess<br/>空结果不熔断"]
    Fail --> Out
    Ok --> Out
    Skip --> Next["尝试下一插件或降级源"]
    Next --> Out["CrawlResultEmitter"]
  end

  subgraph sse["SSE 帧"]
    Out --> RB["resource_bundle 选集卡片"]
    Out --> V["video / link / image"]
    LLM2["Agent 最终推荐语"] --> T["text 分片"]
    T --> Done["done 含 conversation_id / 可选 title"]
    RB --> Done
    V --> Done
  end

  subgraph bg["后台恢复 不占用用户请求"]
    Sch["UpstreamProbeScheduler"]
    Sch --> Canary["对 OPEN 源发一次 canary GET"]
    Canary -->|成功| Close["recordProbeSuccess → CLOSED"]
    Canary -->|失败| Stay["保持 OPEN"]
  end
```

一次成功搜番在时间线上的数据流：

```mermaid
sequenceDiagram
  participant U as 客户端
  participant C as ChatController
  participant A as StreamingAgent
  participant L as DeepSeek
  participant R as ResourceCrawler
  participant S as SSE

  U->>C: POST /api/v1/chat/stream
  C->>A: streamReply
  A->>A: 黑板锚定 + 链接前置解析
  A->>L: chat 工具循环
  L->>R: searchResources
  R->>R: singleflight + play 缓存
  alt 缓存未命中
    R->>R: 熔断门禁 → 插件 / Playwright / Fallback
    R->>R: 写入 play / catalog 缓存
  end
  R-->>A: 全量结果进 Buffer，摘要回模型
  A->>S: resource_bundle + video
  L-->>A: 短推荐语
  A->>S: text + done
  S-->>U: 流式帧
```

## 快速启动

```bash
cp .env.example .env   # 填入 DEEPSEEK_API_KEY
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run
```

默认 `http://localhost:8000`。首次使用 Playwright WebView 需安装 Chromium：

```bash
mvn exec:java -Dexec.args="install chromium"
```

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/conversations` | 创建会话 |
| `POST` | `/api/v1/chat/stream` | 发送消息，SSE 流式返回 |

未配置 API Key 时仍可联调启发式检索（识别《番剧名》）。单元测试默认关闭 WebView / 熔断嗅探 / Redis。

```bash
mvn test
```

## 文档

| 文档 | 说明 |
|------|------|
| [接口文档](docs/接口文档.md) | SSE 协议、资源块、`resource_bundle` 选集卡片、上传与多模态 |
| [会话标题](docs/会话标题.md) | 首轮 `need_title` / `done.title` |
| [联调指南](docs/联调指南.md) | 启动、三条主路径、常见问题 |

## 目录

```
src/main/java/com/agentcrawler/
├── api/                 # REST / SSE
├── agent/langchain/     # LangChain4j Agent 与 Tool
├── agent/session/       # 会话黑板（作品 / 角色 / 集数锚定）
├── crawler/
│   ├── engine/          # Kazumi 风格规则引擎
│   ├── plugin/          # 站点插件 JSON
│   ├── webview/         # Playwright（useWebview）
│   ├── fallback/        # YHDM / SiliSili 降级
│   ├── reliability/     # 熔断、单飞、后台嗅探
│   └── cache/           # 爬虫出口缓存（播放短 TTL / 目录长 TTL）
├── link/                # URL / magnet 确定性解析
├── streaming/           # SSE 编码
└── store/               # 会话 meta / ChatMemory（可选 Redis）
```

站点规则：`src/main/resources/plugins/*.json`。

## 环境变量

| 变量 | 说明 | 默认 |
|------|------|------|
| `DEEPSEEK_API_KEY` | DeepSeek API Key（启用完整 Agent / 识图） | 空 |
| `DEEPSEEK_BASE_URL` | OpenAI 兼容地址 | `https://api.deepseek.com/v1` |
| `AGENT_PUBLIC_BASE_URL` | 上传图片对外 URL | `http://localhost:8000` |
| `AGENT_REDIS_ENABLED` | Redis（爬虫缓存 L2 + 会话持久化） | `false` |
