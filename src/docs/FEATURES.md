# SchoolShop 前端功能清单（面试展示版）

> 目标：功能丰富、交互花哨、稳定可演示；**不接入真实微信支付/转账**，统一使用「校园积分」闭环。  
> **接口策略**：只做加法扩展（`/api/campus/*`、`/api/ai/*` 等），不改既有核心路径语义，降低后端对接弃用风险。  
> **已下线**：匿名树洞 / 漂流瓶（个人主体与内容安全审核风险高，前后端均不再实现）。

## 给同伙怎么分发文档

| 文档 | 给谁 | 内容 |
|------|------|------|
| **本文 FEATURES.md** | 后端必读 | 功能列表 + **新增 API 路径** |
| **[TECH-REQUIREMENTS.md](./TECH-REQUIREMENTS.md)** | 后端 / 运维必读 | 难度、厂商/资源、人天、**过审安全与备案** |
| **[PERSONAL-MP-REVIEW.md](./PERSONAL-MP-REVIEW.md)** | 全员 | 个人主体限制、提审话术（别写 demo） |
| [API-BACKEND.md](./API-BACKEND.md) | 后端深入联调 | **完整接口规范 v1.1**（核心业务 + 校园扩展字段） |

**甩给后端最少两份：`FEATURES.md` + `TECH-REQUIREMENTS.md`。**  
提审/主体问题另附 `PERSONAL-MP-REVIEW.md`。

## 核心业务（三大支柱）

| 模块 | 能力 |
|------|------|
| 代办悬赏 | 发布/接单/交付/验收，积分托管与结算；**地图选点** |
| 社区动态 | 发帖/点赞/评论/分类；**海报分享**（需内容安全） |
| 资料集市 | 上架/积分兑换/限时下载链接 |

## 校园玩法

| 页面 | 路径 | 亮点 |
|------|------|------|
| 每日签到 | `/pages/campus/checkin` | 连续天数、7 日日历 |
| 成就徽章 | `/pages/campus/badges` | 稀有度、进度 |
| 校园热榜 | `/pages/campus/ranking` | 三榜 + 领奖台 |
| 活动日历 | `/pages/campus/calendar` | 报名进度 |
| 找搭子 | `/pages/campus/partner` | 学习/干饭/运动/开黑 |
| 课程评价 | `/pages/campus/courses` | 星级、难度条 |
| **校园地图** | `/pages/campus/map` | 自绘点位、路径可视化、选点回传 |
| **AI 课表助手** | `/pages/campus/ai-assistant` | 周课表 + Mock 对话 |
| **智能推荐** | `/pages/campus/smart-feed` | 匹配分 + 反馈埋点 |
| 积分中心 / 收藏 / 设置 | `profile/*` | 等级、深色模式 |

## 新增稳定 API（后端可按路径实现）

```
GET  /api/campus/spots
GET  /api/campus/spots/:id
GET  /api/campus/map/tasks

GET  /api/ai/schedule
GET  /api/ai/schedule/today
POST /api/ai/assistant/chat
GET  /api/ai/feed
POST /api/ai/feed/feedback
```

既有 `/api/tasks`、`/api/posts`、`/api/materials`、`/api/auth/*` **保持不变**。  
悬赏创建可附带可选字段 `fromSpotId`（向后兼容）。

**明确不做 / 已废弃（请勿实现）：**

```
/api/treehole/*
/api/bottles/*
```

## UX / 工程亮点

- `share-poster` 组件 + `utils/poster.js` Canvas 导出
- 骨架屏、主题 Store、Mock 本地持久化（签到/积分）
- 首页智能推荐横滑预览

## 刻意不做

- 微信支付 / 企业转账 / 真实提现
- 匿名树洞 / 漂流瓶（审核与合规风险）
- 依赖腾讯/高德 Key 的原生 map（改用自绘相对坐标，零配置可跑）

## 演示建议（3～4 分钟）

1. 首页 → 智能推荐卡片 → 地图看活跃路径  
2. 发布悬赏点「地图选点」  
3. 动态详情生成海报 / 悬赏海报  
4. AI 课表问「今天有什么课」→ 智能推荐反馈「不感兴趣」  
