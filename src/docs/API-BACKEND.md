# SchoolShop 后端 API 接口规范（开发版）

> **版本**：v1.1（2026-07）  
> **适用**：Spring Boot 3.x + MySQL 8 + Redis + 对象存储（OSS/COS）+ 微信小程序  
> **小程序前端**：`schoolshop/`（uni-app），本文档与前端 `api/` 目录对齐  
> **配套**：功能清单 `FEATURES.md` · 难度/过审 `TECH-REQUIREMENTS.md` · 个人主体 `PERSONAL-MP-REVIEW.md`  
> **重要变更（相对 v1.0）**：
> 1. **取消真实微信支付 / 提现**，统一 **校园积分**（不可兑换人民币）  
> 2. 新增校园扩展：地图点位、签到、徽章、热榜、活动、找搭子、课评、AI 课表/推荐、收藏  
> 3. **不做** 匿名树洞 / 漂流瓶（`/api/treehole/*`、`/api/bottles/*` 禁止实现）

---

## 目录

1. [概述与架构](#一概述与架构)
2. [通用约定](#二通用约定)
3. [数据字典与状态机](#三数据字典与状态机)
4. [接口总览](#四接口总览)
5. [认证与用户](#五认证与用户)
6. [文件上传](#六文件上传)
7. [社区动态（帖子）](#七社区动态帖子)
8. [代办悬赏](#八代办悬赏)
9. [资料集市](#九资料集市)
10. [消息与通知](#十消息与通知)
11. [订单与积分账户](#十一订单与积分账户)
12. [~~微信支付~~（已废弃）](#十二微信支付已废弃)
13. [定时任务](#十三定时任务)
14. [数据库设计参考](#十四数据库设计参考)
15. [管理端 Admin API 概要](#十五管理端-admin-api-概要)
16. [积分 · 签到 · 徽章 · 热榜](#十六积分--签到--徽章--热榜)
17. [校园地图](#十七校园地图)
18. [校园活动日历](#十八校园活动日历)
19. [找搭子](#十九找搭子)
20. [课程评价](#二十课程评价)
21. [AI 课表与智能推荐](#二十一ai-课表与智能推荐)
22. [搜索热词 · 收藏 · 举报](#二十二搜索热词--收藏--举报)
23. [废弃接口清单](#二十三废弃接口清单)

---

## 一、概述与架构

### 1.1 业务模块

| 模块 | 说明 | 核心流程 |
|------|------|----------|
| 基础支撑 | 登录、用户、上传、**积分账户** | 全站底座 |
| 社区互动 | 发帖、点赞、评论 | **内容安全** → 先审后发（或机审） |
| 资料集市 | 实验报告等学习资料 | **积分兑换** → 自动发货 → 预签名下载 |
| 代办悬赏 | 校园互助任务 | **积分冻结** → 接单 → 交付 → 验收结算积分 |
| 校园扩展 | 地图、签到、课表、推荐、找搭子、课评等 | 见第十六章起 |

### 1.2 推荐分层（Spring Boot）

```
controller/     # REST 接口，参数校验
service/        # 业务逻辑、事务
mapper/         # MyBatis-Plus
domain/         # 实体、DTO、VO
common/         # 统一响应、异常、拦截器
integration/    # 微信登录、内容安全、OSS、（可选）大模型
job/            # 定时任务（自动验收等）
```

### 1.3 基础 URL

| 环境 | 示例 |
|------|------|
| 开发 | `http://localhost:8080` |
| 生产 | `https://api.your-domain.com`（须 HTTPS + 合法域名） |

所有业务接口前缀：`/api`  
建议提供：`GET /api/health` → `{ "status": "UP" }`（提审期间保活）

---

## 二、通用约定

### 2.1 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Content-Type` | 是（JSON 接口） | `application/json;charset=UTF-8` |
| `Authorization` | 除白名单外必填 | `Bearer {jwt}` |

**白名单（无需登录）**：

- `POST /api/auth/wx-login`
- `POST /api/pay/notify/wechat`
- `GET /api/posts`（列表，仅返回 status=1）
- `GET /api/posts/{id}`（详情，仅已发布）
- `GET /api/tasks`（列表，仅 status=1）
- `GET /api/tasks/{id}`
- `GET /api/materials`、`GET /api/materials/{id}`

### 2.2 统一响应体

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1715769600000
}
```

### 2.3 业务状态码 `code`

| code | HTTP | 说明 |
|------|------|------|
| 0 | 200 | 成功 |
| 400 | 400 | 参数错误 |
| 401 | 401 | 未登录 / Token 无效 |
| 403 | 403 | 无权限 / 账号封禁 |
| 404 | 404 | 资源不存在 |
| 409 | 409 | 冲突（如抢单失败） |
| 422 | 422 | 业务校验失败（敏感词、未实名等） |
| 500 | 500 | 服务器错误 |

### 2.4 分页

**请求 Query**（所有列表接口统一）：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| page | int | 1 | 页码，≥1 |
| pageSize | int | 10 | 每页条数，建议 10~20，最大 50 |

**响应 `data` 结构**：

```json
{
  "list": [],
  "total": 100,
  "page": 1,
  "pageSize": 10,
  "hasMore": true
}
```

### 2.5 金额 / 积分与时间

- **积分**：一律使用 **整数**（`int`），字段名优先 `points` / `rewardAmount` / `amount`  
  - 前端展示直接显示该整数 +「积分」  
  - **禁止**真实人民币结算、提现、微信支付  
- **兼容说明**：历史文档曾写「分」；现统一视为 **积分整数**，无需再 `/100`  
- **时间**：ISO 8601 字符串，如 `2026-05-16T10:30:00+08:00`（前后端统一一种即可）

### 2.6 全局拦截器要求

1. 解析 JWT，注入 `currentUserId`
2. 写操作前校验 `user.status != 1`（封禁）→ 返回 `403`，message：`账号已被封禁`
3. 发帖、发布悬赏等建议校验 `realNameVerified == true`（可按产品开关）
4. **内容安全**：发帖/评论/找搭子/资料简介/私信文本走微信 `msgSecCheck`（或等价），失败返回 `422`
5. 写接口限流（按 userId / IP）

---

## 三、数据字典与状态机

### 3.1 用户 `user.status`

| 值 | 含义 |
|----|------|
| 0 | 正常 |
| 1 | 封禁 |

### 3.2 帖子 `post.status`

| 值 | 含义 | 小程序可见 |
|----|------|------------|
| 0 | 待审核（疑似违规） | 否 |
| 1 | 已发布 | 是 |
| 2 | 已驳回 | 否（仅作者可见详情，可选） |

### 3.3 资料 `material.status`

| 值 | 含义 |
|----|------|
| 0 | 待审核 |
| 1 | 已上架 |
| 2 | 已驳回 |

### 3.4 悬赏任务 `task.status`

| 值 | 含义 | 可执行操作 |
|----|------|------------|
| 0 | 待确认冻结（可选；推荐创建时直接进 1） | 取消并退回积分 |
| 1 | 招募中 | 接单 |
| 2 | 进行中 | 接单人提交交付 |
| 3 | 待验收 | 雇主确认完工 → 积分结算给接单人 |
| 4 | 已完成 | — |
| 5 | 已取消（解冻退回） | — |

**推荐状态流转（积分版）**：

```
创建并冻结积分 --> 1 --接单--> 2 --交付--> 3 --确认/超时--> 4（接单人到账）
1/0 --取消--> 5（积分退回发布者）
```

> 前端当前：先调积分扣减，再 `POST /api/tasks`。**后端更推荐**：`POST /api/tasks` 内原子完成「校验余额 → 冻结 → 创建任务 status=1」。

### 3.5 订单 `order.status`（积分兑换记录）

| 值 | 前端字段 | 含义 |
|----|----------|------|
| 0 | pending | 处理中（少用） |
| 1 | completed | 已完成 |
| 2 | cancelled | 已取消 |

`order.type`：`material` | `task` | `event` | `redeem`

### 3.6 ~~提现~~（已废弃）

不做真实提现。积分兑换走 `POST /api/points/redeem` / `exchange`，仅扣虚拟积分。

### 3.7 通知 `activity.type`

| type | 说明 | postId |
|------|------|--------|
| FOLLOW | 关注 | null |
| LIKE_POST | 赞帖子 | 有 |
| COMMENT_POST | 评论帖子 | 有 |
| SAVE_POST | 收藏帖子 | 有 |
| SHARE_POST | 分享帖子 | 有 |
| LIKE_COMMENT | 赞评论 | 有 |

### 3.8 帖子分类 `categoryId`

| id | name |
|----|------|
| 1 | 校园生活 |
| 2 | 吐槽 |
| 3 | 表白墙 |
| 4 | 失物招领 |

### 3.9 任务分类 `task.category`

| 值 | 说明 |
|----|------|
| pickup | 代取 |
| errand | 跑腿 |
| study | 学习 |
| other | 其他 |

### 3.10 资料分类 `material.category`

| 值 | 说明 |
|----|------|
| report | 实验报告 |
| note | 复习笔记 |
| lecture | 讲义 |

---

### 3.11 找搭子 `partner.category`

| 值 | 说明 |
|----|------|
| study | 学习 |
| food | 干饭 |
| sport | 运动 |
| game | 开黑 |

### 3.12 校园点位 `spot.category`

| 值 | 说明 |
|----|------|
| study | 学习 |
| teach | 教学 |
| food | 餐饮 |
| dorm | 宿舍 |
| life | 生活 |
| sport | 运动 |
| gate | 校门 |
| culture | 文体 |

### 3.13 智能推荐 `feed.type`

`task` | `course` | `partner` | `material` | `event` | `post`

### 3.14 徽章 `badge.rarity`

`common` | `rare` | `epic` | `legendary`

---

## 四、接口总览

### 4.1 核心业务（既有，保持兼容）

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|------|
| POST | /api/auth/wx-login | 微信登录 | 否 |
| GET | /api/user/profile | 当前用户 | 是 |
| PUT | /api/user/profile | 更新资料 | 是 |
| GET | /api/users/{userId}/home | 用户主页 | 是 |
| POST | /api/users/{userId}/follow | 关注/取消 | 是 |
| POST | /api/upload/image | 上传图片 | 是 |
| POST | /api/upload/material | 上传资料文件 | 是 |
| GET | /api/posts | 帖子列表 | 否 |
| GET | /api/posts/{id} | 帖子详情 | 否 |
| POST | /api/posts | 发帖 | 是 |
| POST | /api/posts/{id}/like | 点赞切换 | 是 |
| POST | /api/posts/{id}/comments | 评论 | 是 |
| DELETE | /api/posts/{id} | 删帖 | 是 |
| GET | /api/tasks | 任务列表 | 否 |
| GET | /api/tasks/{id} | 任务详情 | 否 |
| POST | /api/tasks | 创建任务（积分冻结） | 是 |
| POST | /api/tasks/{id}/accept | 接单 | 是 |
| POST | /api/tasks/{id}/deliver | 提交交付 | 是 |
| POST | /api/tasks/{id}/confirm | 确认完工（结算积分） | 是 |
| GET | /api/tasks/my/{type} | 我的任务 | 是 |
| GET | /api/materials | 资料列表 | 否 |
| GET | /api/materials/{id} | 资料详情 | 否 |
| POST | /api/materials | 上架资料 | 是 |
| POST | /api/materials/{id}/purchase | 积分兑换资料 | 是 |
| GET | /api/materials/{id}/download-url | 下载签名 URL | 是 |
| GET | /api/materials/my/showcase | 我的橱窗 | 是 |
| GET | /api/notifications/activities | 动态通知 | 是 |
| GET | /api/notifications/unread-count | 未读数 | 是 |
| POST | /api/notifications/activities/{id}/read | 标记已读 | 是 |
| GET | /api/messages/conversations | 会话列表 | 是 |
| GET | /api/messages/{peerId} | 聊天记录 | 是 |
| POST | /api/messages/send | 发送私信 | 是 |
| GET | /api/orders | 兑换/任务记录 | 是 |

### 4.2 积分与校园扩展（v1.1 新增）

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|------|
| GET | /api/points/account | 积分账户 | 是 |
| POST | /api/points/redeem | 积分兑换好物（演示） | 是 |
| POST | /api/points/exchange | 通用扣积分 | 是 |
| GET | /api/checkin/status | 签到状态 | 是 |
| POST | /api/checkin | 执行签到 | 是 |
| GET | /api/badges | 成就徽章 | 是 |
| GET | /api/rankings | 热榜 | 否/是 |
| GET | /api/campus/spots | 地图点位 | 否 |
| GET | /api/campus/spots/{id} | 点位详情 | 否 |
| GET | /api/campus/map/tasks | 地图活跃路径 | 否 |
| GET | /api/campus/events | 活动列表 | 否 |
| POST | /api/campus/events/{id}/join | 报名活动 | 是 |
| GET | /api/partners | 找搭子列表 | 否 |
| POST | /api/partners | 发起组局 | 是 |
| POST | /api/partners/{id}/join | 加入组局 | 是 |
| GET | /api/courses | 课程列表 | 否 |
| GET | /api/courses/{id} | 课程详情+评价 | 否 |
| POST | /api/courses/{id}/reviews | 提交课评 | 是 |
| GET | /api/ai/schedule | 本周课表 | 是 |
| GET | /api/ai/schedule/today | 今日课程 | 是 |
| POST | /api/ai/assistant/chat | AI 助手对话 | 是 |
| GET | /api/ai/feed | 智能推荐 | 是 |
| POST | /api/ai/feed/feedback | 推荐反馈 | 是 |
| GET | /api/search/hot | 热搜词 | 否 |
| GET | /api/favorites | 我的收藏 | 是 |
| POST | /api/favorites/toggle | 切换收藏 | 是 |
| POST | /api/reports | 举报（建议） | 是 |
| GET | /api/health | 健康检查 | 否 |

### 4.3 兼容别名（可选实现）

| 旧路径 | 建议行为 |
|--------|----------|
| GET /api/wallet | **重定向/等同** `GET /api/points/account` |
| POST /api/wallet/withdraw | **不要打款**；等同 `POST /api/points/redeem` 或直接 410 |
| POST /api/tasks/{id}/pay | 可选空实现：返回 `{ "payParams": { "mock": true } }`；正式以创建时积分为准 |

### 4.4 禁止实现

| 路径 | 原因 |
|------|------|
| `/api/treehole/**` | 匿名 UGC，审核风险 |
| `/api/bottles/**` | 同上 |
| 真实微信支付下单/企业付款 | 产品决策 + 个人主体 |

---

## 五、认证与用户

### 5.1 微信登录

```
POST /api/auth/wx-login
```

**请求体**

```json
{
  "code": "081xxx"
}
```

**处理逻辑**

1. 用 `code` 调微信 `jscode2session` 获取 `openid`（及 `unionid`）
2. 不存在则注册，存在则更新 `last_login_at`
3. 签发 JWT（建议 payload：`userId`, `exp`）

**响应 `data`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "nickname": "微信用户",
    "avatar": "https://...",
    "studentId": null,
    "realNameVerified": false,
    "status": 0,
    "walletBalance": 0
  }
}
```

---

### 5.2 获取当前用户

```
GET /api/user/profile
Authorization: Bearer {token}
```

**响应 `data`**：同上 `user` 对象。

---

### 5.3 更新个人信息

```
PUT /api/user/profile
```

**请求体**

```json
{
  "nickname": "银哈哈",
  "avatar": "https://oss.../avatar.jpg"
}
```

**校验**：昵称长度 2~20；头像须为本系统 OSS 域名（防外链）。

---

### 5.4 用户主页

```
GET /api/users/{userId}/home
```

**响应 `data`**

```json
{
  "user": {
    "id": 2,
    "nickname": "Helena",
    "avatar": "https://...",
    "bio": "热爱生活",
    "postCount": 12,
    "taskCount": 3,
    "materialCount": 2,
    "followed": false
  },
  "posts": [ /* PostItem，仅 status=1 */ ],
  "materials": [ /* MaterialItem，仅 status=1 */ ]
}
```

- `followed`：当前登录用户是否已关注 TA（未登录可为 `false`）
- 查看自己主页时 `followed` 忽略，可不展示关注按钮

---

### 5.5 关注 / 取消关注（Toggle）

```
POST /api/users/{userId}/follow
```

**业务**：已关注则取消，未关注则建立；不能关注自己。

**响应 `data`**

```json
{
  "followed": true
}
```

**副作用**：向对方写入 `FOLLOW` 类型通知（可选，避免重复通知）。

---

### 5.6 实名认证（建议实现）

```
POST /api/user/real-name-verify
```

**请求体**：`{ "realName": "张三", "studentId": "2021001001" }`  
对接学校统一认证或人工审核后置 `realNameVerified=true`。  
发帖、发布悬赏前必须校验。

---

## 六、文件上传

### 6.1 上传图片（帖子配图等）

```
POST /api/upload/image
Content-Type: multipart/form-data
```

| 字段 | 说明 |
|------|------|
| file | 图片文件，≤5MB，jpg/png/webp |

**处理**：上传至 OSS **公共读** Bucket；可选调 `mediaCheckAsync`。

**响应 `data`**

```json
{
  "url": "https://your-bucket.oss-cn-xxx.aliyuncs.com/posts/2026/05/xxx.jpg"
}
```

---

### 6.2 上传资料文件

```
POST /api/upload/material
Content-Type: multipart/form-data
```

| 限制 | 说明 |
|------|------|
| 后缀 | 仅 `pdf`, `doc`, `docx` |
| 大小 | 建议 ≤20MB |

**处理**：上传至 OSS **私有** Bucket，返回 `fileKey`，禁止返回永久公网 URL。

**响应 `data`**

```json
{
  "fileKey": "materials/2026/05/u1_abc.pdf",
  "fileName": "实验报告.pdf",
  "fileType": "pdf"
}
```

---

## 七、社区动态（帖子）

### 7.1 帖子列表

```
GET /api/posts?page=1&pageSize=10&keyword=&categoryId=
```

| Query | 说明 |
|-------|------|
| keyword | 搜正文 |
| categoryId | 分类筛选 |

**默认**：仅 `status=1`；按 `created_at DESC`。

**PostItem**

```json
{
  "id": 1,
  "userId": 2,
  "user": { "id": 2, "nickname": "Helena", "avatar": "https://..." },
  "categoryId": 1,
  "categoryName": "校园生活",
  "content": "正文内容",
  "images": ["https://..."],
  "likeCount": 21,
  "commentCount": 4,
  "liked": false,
  "status": 1,
  "createdAt": "2026-05-16T10:00:00+08:00"
}
```

- `liked`：登录用户是否已赞；未登录固定 `false`

---

### 7.2 帖子详情

```
GET /api/posts/{id}
```

**响应**：`PostItem` + `comments` 数组（按时间正序，分页可选）。

```json
{
  "id": 1,
  "content": "...",
  "comments": [
    {
      "id": 10,
      "user": { "id": 3, "nickname": "Daniel", "avatar": "..." },
      "content": "写得好！",
      "createdAt": "..."
    }
  ]
}
```

---

### 7.3 发布帖子

```
POST /api/posts
```

**请求体**

```json
{
  "categoryId": 1,
  "content": "帖子正文",
  "images": ["https://oss.../1.jpg"]
}
```

**业务规则**

1. 校验实名 `realNameVerified`
2. 文本 `msgSecCheck`；图片 `mediaCheckAsync`
3. 违规 → `422`，`包含敏感词，请修改后重试`
4. 疑似违规 → `status=0`，message：`已提交审核`
5. 通过 → `status=1`，message：`发布成功`

**响应 `data`**

```json
{
  "id": 100,
  "status": 1,
  "message": "发布成功"
}
```

---

### 7.4 点赞 / 取消点赞

```
POST /api/posts/{id}/like
```

**幂等 Toggle**：已赞则取消，未赞则点赞；维护 `post_like(user_id, post_id)` 唯一索引。

**响应 `data`**

```json
{
  "liked": true,
  "likeCount": 22
}
```

**副作用**：点赞时给作者发 `LIKE_POST` 通知（不通知自己）。

---

### 7.5 发表评论

```
POST /api/posts/{id}/comments
```

```json
{ "content": "评论内容" }
```

- 内容走 `msgSecCheck`
- `commentCount+1`，通知作者 `COMMENT_POST`

**响应 `data`**

```json
{
  "id": 101,
  "content": "评论内容",
  "createdAt": "..."
}
```

---

### 7.6 删除帖子

```
DELETE /api/posts/{id}
```

仅作者可删；软删除推荐。

---

## 八、代办悬赏

### 8.1 任务列表

```
GET /api/tasks?status=1&category=&keyword=&sort=time&page=1&pageSize=10
```

| Query | 说明 |
|-------|------|
| status | 默认 1（招募中） |
| category | pickup / errand / study / other |
| sort | `time`（默认）\| `reward`（赏金降序） |
| keyword | 搜标题、描述、地点 |

**TaskItem**

```json
{
  "id": 1,
  "title": "代取快递",
  "description": "详细说明",
  "location": "菜鸟驿站 → 6号宿舍",
  "rewardAmount": 500,
  "status": 1,
  "category": "pickup",
  "tags": ["代取", "急单"],
  "publisher": { "id": 4, "nickname": "小明", "avatar": "..." },
  "acceptor": null,
  "createdAt": "...",
  "deadline": "..."
}
```

---

### 8.2 任务详情

```
GET /api/tasks/{id}
```

在 TaskItem 基础上增加：

```json
{
  "deliveryNote": "已完成取件",
  "deliveryImages": ["https://..."],
  "acceptor": { "id": 5, "nickname": "小红", "avatar": "..." }
}
```

- 仅相关人可见交付凭证（雇主、接单人、管理员）

---

### 8.3 创建任务

```
POST /api/tasks
```

```json
{
  "title": "代取快递",
  "description": "说明",
  "location": "地点",
  "rewardAmount": 500,
  "category": "pickup",
  "deadline": "2026-05-17T18:00:00+08:00",
  "fromSpotId": 4
}
```

`fromSpotId` 可选（地图选点）。

**业务（积分版，推荐）**

1. 未封禁；（可选）实名
2. `msgSecCheck` 标题与描述
3. `rewardAmount` 最低 **50 积分**（与前端一致）
4. **原子**：校验积分 ≥ rewardAmount → 冻结/扣减发布者积分 → 插入 `task`，`status=1`
5. 写积分流水 `expense`，remark：`发布悬赏冻结：{title}`

**响应 `data`（推荐）**

```json
{
  "taskId": 1,
  "status": 1,
  "rewardAmount": 500
}
```

**兼容旧响应（可选）**：仍可返回 `payParams: { "mock": true }`，前端会跳过收银台。

---

### 8.4 任务支付（兼容空实现）

```
POST /api/tasks/{id}/pay
```

v1.1 **无需真支付**。若保留：返回 `{ "payParams": { "mock": true } }`。正式以 8.3 创建时积分为准。

---

### 8.5 接单

```
POST /api/tasks/{id}/accept
```

**业务（必须原子）**

```sql
UPDATE task SET status=2, acceptor_id=?, accepted_at=NOW(), version=version+1
WHERE id=? AND status=1 AND version=?
```

- 影响行数=0 → `409`，`任务已被接单`
- `acceptor_id == publisher_id` → `422`，不能接自己的单

---

### 8.6 提交交付

```
POST /api/tasks/{id}/deliver
```

```json
{
  "deliveryNote": "已放在宿舍楼下",
  "deliveryImages": ["https://oss.../proof1.jpg"]
}
```

- 仅接单人；`status` 必须为 2
- `deliveryImages.length >= 1`
- 成功 → `status=3`，记录 `delivered_at`

---

### 8.7 确认完工

```
POST /api/tasks/{id}/confirm
```

- 仅发布者；`status` 必须为 3

**事务内（积分结算）**

1. `task.status = 4`，`completed_at = now`
2. （可选）平台抽成积分，如 5%，记流水
3. 接单人 `points_balance` += 净额；写 `points_record` income
4. 发布者冻结积分核销（若创建时为冻结而非直接扣减，此处解冻给接单人）
5. （可选）通知双方

---

### 8.8 我的任务

```
GET /api/tasks/my/published?page=1&pageSize=10
GET /api/tasks/my/accepted?page=1&pageSize=10
```

`type` 路径：`published` | `accepted`

---

## 九、资料集市

### 9.1 资料列表

```
GET /api/materials?page=1&pageSize=20&sortBy=time&category=&keyword=
```

| sortBy | 说明 |
|--------|------|
| time | 上架时间降序 |
| price | 价格升序 |

默认 `status=1`。

**MaterialItem**

```json
{
  "id": 1,
  "title": "电路分析实验报告",
  "description": "简介",
  "price": 100,
  "coverUrl": "https://...",
  "fileType": "pdf",
  "category": "report",
  "soldCount": 12,
  "createdAt": "..."
}
```

---

### 9.2 资料详情

```
GET /api/materials/{id}
```

增加：

```json
{
  "owned": false
}
```

`owned`：当前用户是否已购买（`user_material` 表存在记录）。

---

### 9.3 上架资料

```
POST /api/materials
```

```json
{
  "title": "标题",
  "description": "简介",
  "price": 100,
  "fileKey": "materials/...",
  "coverUrl": "https://..."
}
```

- 校验 `fileKey` 归属当前用户上传记录
- 初始 `status=0` 或走审核策略

**响应**

```json
{
  "id": 1,
  "status": 0,
  "message": "已提交审核"
}
```

---

### 9.4 兑换资料（原「购买」）

```
POST /api/materials/{id}/purchase
```

**积分版业务**

- 不能兑自己的；已 `owned` → `422`
- 校验买家积分 ≥ `price`
- **原子**：扣积分 → 写流水 → 插入 `user_material` → 订单 `completed` → 卖家积分入账（可抽成）
- **不要**返回微信支付参数

**响应（推荐）**

```json
{
  "orderId": 99,
  "success": true,
  "balance": 2580
}
```

**兼容**：若仍返回 `payParams: { "mock": true }`，前端会当作兑换成功。
也可让前端只调 `POST /api/points/exchange` 再标记 owned（不推荐，易不一致）。

---

### 9.5 获取下载链接

```
GET /api/materials/{id}/download-url
```

**前置**：`owned=true` 或本人是卖家。

**响应**

```json
{
  "url": "https://private-bucket...?Expires=...&Signature=...",
  "expiresIn": 600
}
```

OSS `generatePresignedUrl`，有效期 **600 秒**。

---

### 9.6 我的橱窗

```
GET /api/materials/my/showcase
```

当前用户上架的资料（含待审核，前端可自行过滤或后端只返 status=1）。

---

## 十、消息与通知

### 10.1 动态通知列表

```
GET /api/notifications/activities?page=1&pageSize=20
```

**响应 `data`**

```json
{
  "list": [
    {
      "id": 1,
      "type": "COMMENT_POST",
      "user": { "id": 3, "nickname": "Daniel", "avatar": "..." },
      "content": "评论了你的帖子",
      "postId": 1,
      "commentText": "生日快乐！",
      "postThumbnail": "https://...",
      "read": false,
      "followedBack": false,
      "createdAt": "..."
    }
  ],
  "unreadCount": 2
}
```

- `FOLLOW` 类型：`postId=null`，`followedBack` 表示当前用户是否已回关对方
- `postThumbnail`：取帖子首图

---

### 10.2 未读总数

```
GET /api/notifications/unread-count
```

```json
{ "count": 5 }
```

含动态通知 + 私信未读（与前端 Tab 角标一致）。

---

### 10.3 标记已读

```
POST /api/notifications/activities/{id}/read
```

---

### 10.4 会话列表

```
GET /api/messages/conversations
```

```json
{
  "list": [
    {
      "id": 1,
      "peerId": 2,
      "peerName": "Helena",
      "peerAvatar": "...",
      "lastMessage": "好的",
      "lastTime": "...",
      "unread": 2
    }
  ]
}
```

---

### 10.5 聊天记录

```
GET /api/messages/{peerId}?page=1&pageSize=30
```

按 `created_at DESC` 分页，前端倒序展示。

```json
{
  "list": [
    {
      "id": 1,
      "senderId": 2,
      "content": "你好",
      "type": "text",
      "createdAt": "..."
    }
  ]
}
```

---

### 10.6 发送私信

```
POST /api/messages/send
```

```json
{
  "peerId": 2,
  "content": "你好",
  "type": "text"
}
```

- `type`：`text` | `image`（image 时 content 存图片 URL）
- 内容安全校验；更新会话摘要

---

## 十一、订单与积分账户

> 前端「我的钱包」页已改为 **积分中心**，调用 `GET /api/points/account`（`getWallet()` 兼容封装）。

### 11.1 我的订单 / 兑换记录

```
GET /api/orders?page=1&pageSize=10&type=
```

`type` 可选：`material` | `task` | `event` | `redeem`

```json
{
  "list": [
    {
      "id": 1,
      "orderNo": "P20260516001",
      "type": "material",
      "title": "电路分析实验报告",
      "amount": 100,
      "unit": "points",
      "status": "completed",
      "createdAt": "2026-05-16T10:00:00+08:00"
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 10
}
```

---

### 11.2 积分账户

```
GET /api/points/account
```

兼容别名：`GET /api/wallet` → 同一响应。

```json
{
  "balance": 2680,
  "level": 5,
  "levelName": "校园达人",
  "exp": 420,
  "nextLevelExp": 500,
  "records": [
    {
      "id": 1,
      "type": "income",
      "amount": 50,
      "remark": "完成代办悬赏",
      "createdAt": "..."
    },
    {
      "id": 2,
      "type": "expense",
      "amount": 100,
      "remark": "兑换资料：电路分析实验报告",
      "createdAt": "..."
    }
  ]
}
```

`records` 默认最近 20 条，可分页。

---

### 11.3 积分兑换好物（替代提现）

```
POST /api/points/redeem
```

```json
{ "amount": 100, "remark": "积分兑换好物" }
```

**逻辑**

- `amount > 0` 且 `amount ≤ balance`
- 扣减积分，写 `expense` 流水
- **禁止**任何打款到微信/银行卡
- 响应：`{ "id": 1, "status": "completed", "balance": 2580 }`

废弃：`POST /api/wallet/withdraw`（若保留，内部转调本接口，勿真实打款）

---

### 11.4 通用扣积分

```
POST /api/points/exchange
```

```json
{ "cost": 100, "remark": "兑换资料：xxx" }
```

响应：`{ "success": true, "balance": 2580, "orderId": 99 }`  
积分不足：`422`，message：`积分不足，快去签到或接单吧`

前端发布悬赏、兑换资料会调用此接口（或由业务接口内部扣减）。

---

## 十二、微信支付（已废弃）

> **v1.1 起不做微信支付。** 个人主体 + 产品决策。下列内容仅作历史对照，**请勿实现回调打款。**

| 旧能力 | 新替代 |
|--------|--------|
| 任务 JSAPI 支付 | 创建任务时冻结积分 |
| 资料 JSAPI 支付 | `purchase` / `points/exchange` |
| 钱包提现 | `points/redeem`（虚拟） |
| `/api/pay/notify/wechat` | 不需要 |

若旧前端仍请求 `POST /api/tasks/{id}/pay`，可返回：

```json
{ "payParams": { "mock": true } }
```

表示无需拉起收银台。

---

## 十三、定时任务

| 任务 | Cron 建议 | 逻辑 |
|------|-----------|------|
| 自动验收 | 每小时 | `status=3` 且交付超过 48h → 自动 confirm，积分打给接单人 |
| 点赞同步 | 每 5 分钟 | Redis 点赞数刷回 MySQL（若采用） |
| 超时取消招募 | 每天 | 长期无人接单且过 deadline → status=5，解冻积分退回 |
| 热榜重算 | 每小时 | 助人/资料/创作榜快照 |

---

## 十四、数据库设计参考

### 14.1 核心表

| 表名 | 说明 |
|------|------|
| user | 用户、openid、**points_balance**、level/exp、实名、status |
| post / post_like / post_comment | 帖子 |
| task | 悬赏，含 version 乐观锁、可选 from_spot_id |
| material / user_material | 资料与兑换关系 |
| order_record | 积分订单/兑换记录（避免用 order 关键字冲突） |
| points_record | 积分流水 |
| activity_notification | 动态通知 |
| message | 私信 |
| user_follow | 关注 |
| campus_spot | 地图点位 |
| campus_event / event_join | 活动与报名 |
| partner / partner_member | 找搭子 |
| course / course_review | 课程与评价 |
| user_schedule | 用户课表（JSON 或明细表） |
| checkin_log | 签到日志 |
| badge / user_badge | 徽章定义与解锁 |
| favorite | 收藏 |
| report | 举报 |
| ranking_snapshot | 热榜快照（可选） |

### 14.2 `task` 关键字段

```text
id, publisher_id, acceptor_id, title, description, location,
from_spot_id, to_spot_id,
reward_amount, status, category, tags(JSON),
delivery_note, delivery_images(JSON),
version, created_at, accepted_at, delivered_at, completed_at, deadline
```

### 14.3 索引建议

- `post(status, created_at)`
- `task(status, category, created_at)`
- `material(status, created_at)`
- `activity_notification(user_id, read_flag, created_at)`
- `campus_spot(category)`
- `checkin_log(user_id, checkin_date)` UK
- `points_record(user_id, created_at)`

---

## 十五、管理端 Admin API 概要

> 独立 `Admin-JWT`。个人主体上线至少要有 **内容处理** 能力。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /admin/posts | 待审/全部帖子 |
| POST | /admin/posts/{id}/audit | 通过/驳回 |
| GET | /admin/materials | 待审资料 |
| POST | /admin/materials/{id}/audit | 通过/驳回 |
| GET | /admin/reports | 举报列表 |
| POST | /admin/reports/{id}/handle | 处理举报 |
| GET | /admin/users | 用户列表 |
| POST | /admin/users/{id}/ban | 封禁/解封 |
| POST | /admin/points/{userId}/adjust | 调账（须审计日志，勿对 C 端暴露） |

```json
{ "pass": false, "reason": "包含不文明用语" }
```

~~提现审批接口已废弃。~~

---

## 十六、积分 · 签到 · 徽章 · 热榜

### 16.1 签到状态

```
GET /api/checkin/status
```

```json
{
  "checkedToday": false,
  "streak": 6,
  "totalDays": 42,
  "todayReward": 0,
  "lastCheckinAt": "2026-07-28T08:00:00+08:00",
  "calendar": [
    {
      "date": "2026-07-28",
      "label": "一",
      "day": 28,
      "checked": false,
      "isToday": true
    }
  ],
  "tips": ["连续签到可叠加积分", "满 7 天额外 +30 积分", "漏签会重置连续天数"]
}
```

`calendar`：近 7 日（含今天）。

---

### 16.2 执行签到

```
POST /api/checkin
```

**逻辑建议**

- 同一自然日仅一次（UK `user_id + date`）
- 奖励：`10 + min(streak, 7) * 2`（可调）；满 7 天额外奖励可选
- 增加积分流水 + 可选 EXP
- 已签到：`422`，`今日已签到`

响应：在 status 结构上附加 `todayReward`、`points`（最新余额）。

---

### 16.3 成就徽章

```
GET /api/badges
```

```json
{
  "list": [
    {
      "id": 1,
      "name": "初来乍到",
      "icon": "🌱",
      "desc": "完成首次登录",
      "unlocked": true,
      "unlockedAt": "2024-09-01",
      "rarity": "common"
    },
    {
      "id": 4,
      "name": "学霸本霸",
      "icon": "📚",
      "desc": "上架 3 份资料",
      "unlocked": false,
      "progress": 1,
      "target": 3,
      "rarity": "epic"
    }
  ],
  "unlocked": 3
}
```

---

### 16.4 校园热榜

```
GET /api/rankings?type=helper
```

`type`：`helper` | `seller` | `creator`

```json
{
  "type": "helper",
  "list": [
    {
      "rank": 1,
      "userId": 3,
      "nickname": "Daniel",
      "avatar": "https://...",
      "score": 98,
      "label": "本周帮 12 单",
      "badge": "🥇"
    }
  ]
}
```

---

## 十七、校园地图

> 前端使用 **相对坐标 0~100** 自绘，可不接腾讯/高德。

### 17.1 点位列表

```
GET /api/campus/spots?category=all&keyword=
```

```json
{
  "schoolName": "SchoolShop 大学",
  "bounds": { "width": 100, "height": 100 },
  "list": [
    {
      "id": 1,
      "name": "图书馆",
      "alias": "静心楼",
      "zone": "中区",
      "x": 48,
      "y": 42,
      "category": "study",
      "hot": 98,
      "taskCount": 12,
      "desc": "通宵自习友好"
    }
  ]
}
```

### 17.2 点位详情

```
GET /api/campus/spots/{id}
```

返回单个 spot 对象。

### 17.3 地图活跃悬赏路径

```
GET /api/campus/map/tasks
```

```json
{
  "list": [
    {
      "id": 1,
      "title": "代取快递",
      "rewardAmount": 500,
      "fromSpotId": 4,
      "toSpotId": 7,
      "status": 1,
      "from": { "id": 4, "name": "菜鸟驿站", "x": 78, "y": 38 },
      "to": { "id": 7, "name": "6号宿舍", "x": 28, "y": 28 }
    }
  ]
}
```

创建任务可选 body 字段：`fromSpotId`（忽略不影响旧逻辑）。

---

## 十八、校园活动日历

### 18.1 活动列表

```
GET /api/campus/events?month=2026-07
```

```json
{
  "list": [
    {
      "id": 1,
      "title": "春季校园招聘双选会",
      "cover": "https://...",
      "location": "大礼堂",
      "startAt": "...",
      "endAt": "...",
      "category": "career",
      "joined": false,
      "capacity": 500,
      "joinedCount": 328,
      "tags": ["招聘", "热门"],
      "description": "..."
    }
  ],
  "total": 1
}
```

`category`：`career` | `study` | `culture` | `volunteer`

### 18.2 报名

```
POST /api/campus/events/{id}/join
```

响应：`{ "eventId": 1, "joined": true, "message": "报名成功，已加入日程" }`  
满员 / 重复报名：`422`

---

## 十九、找搭子

### 19.1 列表

```
GET /api/partners?category=all&keyword=
```

```json
{
  "list": [
    {
      "id": 1,
      "title": "今晚图书馆三楼拼自习",
      "description": "...",
      "category": "study",
      "tags": ["自习", "图书馆"],
      "timeText": "今晚 19:00",
      "location": "图书馆三楼",
      "needCount": 3,
      "joinedCount": 1,
      "publisher": { "id": 2, "nickname": "Helena", "avatar": "..." },
      "status": "open",
      "createdAt": "..."
    }
  ],
  "total": 1
}
```

**须内容安全**。

### 19.2 发起

```
POST /api/partners
```

```json
{
  "title": "今晚图书馆三楼拼自习",
  "description": "...",
  "category": "study",
  "tags": ["自习"],
  "timeText": "今晚 19:00",
  "location": "图书馆三楼",
  "needCount": 4
}
```

响应含 `id,status,joinedCount,createdAt`；创建者自动算 1 人。

### 19.3 加入

```
POST /api/partners/{id}/join
```

满员：`422`，`人数已满`  
响应：`{ "id": 1, "joined": true }`

---

## 二十、课程评价

### 20.1 课程列表

```
GET /api/courses?keyword=&sortBy=hot
```

`sortBy`：`hot` | `rating`

```json
{
  "list": [
    {
      "id": 1,
      "name": "高等数学 A",
      "teacher": "张教授",
      "college": "理学院",
      "rating": 4.6,
      "reviewCount": 128,
      "difficulty": 4,
      "useful": 5,
      "tags": ["硬核", "点名少"],
      "cover": "https://..."
    }
  ],
  "total": 1
}
```

`difficulty` / `useful`：1～5。

### 20.2 课程详情

```
GET /api/courses/{id}
```

在课程字段上附加：

```json
{
  "reviews": [
    {
      "id": 1,
      "courseId": 1,
      "user": { "id": 2, "nickname": "Helena", "avatar": "..." },
      "rating": 5,
      "content": "...",
      "likes": 24,
      "createdAt": "..."
    }
  ]
}
```

### 20.3 提交评价

```
POST /api/courses/{id}/reviews
```

```json
{ "rating": 5, "content": "讲得很清楚" }
```

**内容安全**；可奖励少量积分。  
响应：评价对象（含 id、createdAt、likes=0）。

---

## 二十一、AI 课表与智能推荐

### 21.1 本周课表

```
GET /api/ai/schedule
```

```json
{
  "weekLabel": "第 12 教学周",
  "term": "2025-2026 春季学期",
  "days": [
    {
      "day": 1,
      "label": "周一",
      "courses": [
        {
          "id": 1,
          "name": "高等数学 A",
          "teacher": "张教授",
          "place": "一教 301",
          "start": "08:00",
          "end": "09:40",
          "color": "#667eea"
        }
      ]
    }
  ]
}
```

`day`：1=周一 … 7=周日。一期可用用户手填/运营配置；勿依赖教务爬虫作主路径。

### 21.2 今日课程

```
GET /api/ai/schedule/today
```

```json
{
  "weekLabel": "第 12 教学周",
  "dayLabel": "周三",
  "courses": [],
  "next": null
}
```

### 21.3 AI 助手对话

```
POST /api/ai/assistant/chat
```

```json
{
  "message": "今天有什么课",
  "context": { "page": "ai-assistant" }
}
```

```json
{
  "id": 1710000000000,
  "role": "assistant",
  "content": "今天的课程……",
  "suggestions": ["今天有什么课", "推荐自习搭子", "怎么赚积分"],
  "createdAt": "...",
  "context": {}
}
```

- 可接大模型；**输出必须过内容安全**，失败返回兜底文案  
- 限流：如每用户 20 次/小时  
- 无大模型时可用规则引擎（课表查询 + FAQ）

### 21.4 智能推荐 Feed

```
GET /api/ai/feed?limit=10
```

```json
{
  "list": [
    {
      "id": "sf1",
      "type": "task",
      "targetId": 1,
      "title": "为你推荐：代取快递热单",
      "reason": "你常出没东区",
      "score": 0.96,
      "cover": "",
      "meta": {
        "rewardAmount": 500,
        "location": "菜鸟驿站 → 6号宿舍"
      }
    }
  ],
  "generatedAt": "...",
  "algorithm": "rule-hybrid-v1",
  "tip": "综合课表、活跃区域、浏览偏好生成"
}
```

一期规则引擎即可。

### 21.5 推荐反馈

```
POST /api/ai/feed/feedback
```

```json
{ "itemId": "sf1", "action": "like" }
```

`action`：`like` | `dislike` | `click`  
响应：`{ "ok": true, "itemId": "sf1", "action": "like" }`

---

## 二十二、搜索热词 · 收藏 · 举报

### 22.1 热搜

```
GET /api/search/hot
```

```json
{
  "list": [
    { "word": "代取快递", "heat": 980 },
    { "word": "期末笔记", "heat": 860 }
  ]
}
```

### 22.2 我的收藏

```
GET /api/favorites?type=all
```

`type`：`all` | `post` | `material` | `task` | `course`

```json
{
  "list": [
    {
      "id": 1,
      "type": "post",
      "targetId": 1,
      "title": "...",
      "cover": "https://...",
      "createdAt": "..."
    }
  ]
}
```

### 22.3 切换收藏

```
POST /api/favorites/toggle
```

```json
{ "type": "post", "targetId": 1 }
```

响应：`{ "favorited": true, "type": "post", "targetId": 1 }`

### 22.4 举报（建议实现，过审加分）

```
POST /api/reports
```

```json
{
  "targetType": "post",
  "targetId": 1,
  "reason": "骚扰辱骂",
  "detail": "可选补充"
}
```

`targetType`：`post` | `comment` | `partner` | `user` | `material` | `message`  
响应：`{ "id": 1, "status": "pending" }`

---

## 二十三、废弃接口清单

| 路径 | 状态 | 说明 |
|------|------|------|
| `/api/treehole/**` | ❌ 禁止 | 匿名树洞已下线 |
| `/api/bottles/**` | ❌ 禁止 | 漂流瓶已下线 |
| `POST /api/wallet/withdraw` 真实打款 | ❌ 禁止 | 改为积分兑换 |
| `POST /api/pay/notify/wechat` | ❌ 不需要 | 无微信支付 |
| `POST /api/tasks/{id}/pay` 真收银台 | ⚠️ 兼容空实现 | 返回 mock payParams 即可 |

---

## 附录 A：错误 message 一览

| message | code | 场景 |
|---------|------|------|
| 请先登录 | 401 | 无 Token |
| 账号已被封禁 | 403 | status=1 |
| 请先完成实名认证 | 422 | 发帖/发任务（若开启） |
| 包含敏感词，请修改后重试 | 422 | 内容安全 |
| 任务已被接单 | 409 | 抢单失败 |
| 不能接自己发布的任务 | 422 | 接单 |
| 不支持的文件格式 | 400 | 上传 |
| 积分不足，快去签到或接单吧 | 422 | 兑换/发悬赏 |
| 今日已签到 | 422 | 重复签到 |
| 人数已满 | 422 | 找搭子/活动 |

---

## 附录 B：与前端页面对照

| 前端页面 | 主要接口 |
|----------|----------|
| 登录 | POST /api/auth/wx-login |
| 首页-代办/帖子/集市 | GET tasks / posts / materials |
| 首页-智能推荐条 | GET /api/ai/feed |
| 发帖 | POST /api/posts |
| 帖子详情 + 海报 | GET posts/{id}，like/comments（海报纯前端） |
| 发布悬赏 + 地图选点 | POST points/exchange，POST tasks；GET campus/spots |
| 任务详情 | GET/POST tasks 系列；确认完工结算积分 |
| 资料详情/兑换 | GET materials，POST purchase 或 points/exchange，GET download-url |
| 消息 | notifications + messages |
| 用户主页 | users/{id}/home，follow |
| 积分中心 | points/account，redeem，checkin，badges |
| 签到 | checkin/status，checkin |
| 热榜 | rankings |
| 活动日历 | campus/events，join |
| 找搭子 | partners |
| 课程评价 | courses，reviews |
| AI 课表 | ai/schedule，assistant/chat |
| 智能推荐页 | ai/feed，feedback |
| 搜索 | search/hot + 各 list keyword |
| 收藏 | favorites |
| 设置 | 前端主题；退出清 token |

---

## 附录 C：联调 checklist（后端哥们直接勾）

- [ ] 统一响应 `{ code, message, data }`，成功 `code=0` 或 `200`
- [ ] JWT + 401 清登录
- [ ] 积分账户与流水正确；**无微信支付**
- [ ] 创建任务原子冻结；确认完工原子结算
- [ ] 发帖/评论/找搭子/简介接内容安全
- [ ] OSS 上传 + 资料短时下载链
- [ ] campus/spots 与 map/tasks 有数据
- [ ] checkin 防重复
- [ ] ai/schedule 有课表；chat 有兜底
- [ ] **未实现** treehole / bottles
- [ ] HTTPS + 合法域名 + `/api/health`
- [ ] 前端 `BASE_URL` 指向本环境，`USE_MOCK=false`

---

**文档维护**：字段增减请同步本文档与小程序 `api/`。  
**过审安全细节**：见 `TECH-REQUIREMENTS.md` 第 4 节。  
**版本**：v1.1 — 积分经济 + 校园扩展 + 废弃树洞/支付。
