# Agent Crawler 后端

Agent Crawler 后端 API，提供对话会话管理与 SSE 流式响应。

## 快速启动

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python run.py
```

服务默认监听 `http://localhost:8000`。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/conversations` | 创建会话 |
| `POST` | `/api/v1/chat/stream` | 发送消息，SSE 流式返回 |

接口规范见 `docs/2026-09-03_接口文档_1.md`。

## 项目结构

```
app/
├── agents/          # Agent 处理层（与 HTTP 解耦，可替换实现）
├── api/v1/          # REST / SSE 路由
├── core/            # 公共工具、异常、ID 生成
├── services/        # 业务编排
├── store/           # 会话存储抽象 + 内存实现
└── streaming/       # 通用 SSE 流式回复框架
```

## 架构说明

- **streaming/**：通用 SSE 编码与 `StreamPipeline`，负责将 Agent 产出的内容块转换为符合协议的 SSE 帧。
- **agents/**：定义 `AgentHandler` 抽象；当前内置 `StubAgentHandler` 用于联调，后续可接入真实 Agent / 爬虫。
- **store/**：`ConversationStore` 抽象，当前为内存实现，后续可替换为数据库。

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `AGENT_PORT` | 服务端口 | `8000` |
| `AGENT_HANDLER` | Agent 实现 | `stub` |

## 测试

```bash
pytest
```
