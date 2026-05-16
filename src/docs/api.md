# SchoolShop 后端 API 接口规范（开发版）

> **版本**：v1.0  
> **适用**：Spring Boot 3.x + MySQL 8 + Redis + 阿里云 OSS + 微信小程序  
> **小程序前端**：`E:\schoolshop\schoolshop`（uni-app），本文档与前端 `api/` 目录一一对应  
> **完整 DDL**：见 [mysql.md](./mysql.md)
> **管理端**：B 端 Admin 接口见 [第十章](#十管理端-admin-api-概要)（后续迭代）

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
11. [订单与钱包](#十一订单与钱包)
12. [微信支付](#十二微信支付)
13. [定时任务](#十三定时任务)
14. [数据库设计参考](#十四数据库设计参考)
15. [管理端 Admin API 概要](#十五管理端-admin-api-概要)
16. [附录 A：错误 message 一览](#附录-a错误-message-一览)
17. [附录 B：与前端页面对照](#附录-b与前端页面对照)
18. [附录 C：前端实现现状与后端约定](#附录-c前端实现现状与后端约定)

---

## 一、概述与架构

### 1.1 业务模块

| 模块 | 说明 | 核心流程 |
|------|------|----------|
| 基础支撑 | 登录、用户、上传、支付、钱包 | 全站底座 |
| 社区互动 | 发帖、点赞、评论 | 先审后发 / 微信内容安全 |
| 资料交易 | 实验报告等数字商品 | 支付 → 自动发货 → 预签名下载 |
| 代办悬赏 | 跑腿互助担保交易 | 预支付托管 → 接单 → 交付 → 验收打款 |

### 1.2 推荐分层（Spring Boot）

```
controller/     # REST 接口，参数校验
service/        # 业务逻辑、事务
mapper/         # MyBatis-Plus
domain/         # 实体、DTO、VO
common/         # 统一响应、异常、拦截器
integration/    # 微信、OSS、支付
job/            # 定时任务（自动验收等）
```

### 1.3 基础 URL

| 环境 | 示例 |
|------|------|
| 开发 | `http://localhost:8080` |
| 生产 | `https://api.your-domain.com` |

所有业务接口前缀：`/api`  
微信支付回调：`POST /api/pay/notify/wechat`（无需 Token）

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

### 2.5 金额与时间

- **金额**：一律使用 **分**（`int`），禁止浮点元
- **时间**：ISO 8601 字符串，如 `2026-05-16T10:30:00+08:00`，或统一 UTC 毫秒时间戳（前后端约定一种即可）

### 2.6 全局拦截器要求

1. 解析 JWT，注入 `currentUserId`
2. 写操作前校验 `user.status != 1`（封禁）→ 返回 `403`，message：`账号已被封禁`
3. 发帖、发布悬赏等需校验 `realNameVerified == true`

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
| 0 | 待支付 | 支付、取消 |
| 1 | 招募中 | 接单 |
| 2 | 进行中 | 接单人提交交付 |
| 3 | 待验收 | 雇主确认完工 |
| 4 | 已完成 | — |
| 5 | 已取消/退款 | — |

**状态流转**：

```
0 --支付成功--> 1 --接单--> 2 --交付--> 3 --确认/超时--> 4
0 --取消--> 5
1 --超时未接单退款--> 5（可选）
```

### 3.5 订单 `order.status`

| 值 | 前端字段 | 含义 |
|----|----------|------|
| 0 | pending | 待支付 |
| 1 | completed | 已完成 |
| 2 | cancelled | 已取消 |

### 3.6 提现 `withdraw.status`

| 值 | 含义 |
|----|------|
| 0 | 待处理 |
| 1 | 已打款 |
| 2 | 已拒绝 |

### 3.7 通知 `activity.type`

| type | 说明 | postId | v1 |
|------|------|--------|-----|
| FOLLOW | 关注 | null | 是 |
| LIKE_POST | 赞帖子 | 有 | 是 |
| COMMENT_POST | 评论帖子 | 有 | 是 |
| SAVE_POST | 收藏帖子 | 有 | **否（v2 预留）** |
| SHARE_POST | 分享帖子 | 有 | **否（v2 预留）** |
| LIKE_COMMENT | 赞评论 | 有 | **否（v2 预留）** |

> v1 仅写入 FOLLOW / LIKE_POST / COMMENT_POST；SAVE_POST、SHARE_POST、LIKE_COMMENT 前端可展示 mock 数据，后端不生成、不建 `post_save` 表。

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

## 四、接口总览

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
| POST | /api/tasks | 创建任务 | 是 |
| POST | /api/tasks/{id}/pay | 任务支付下单 | 是 |
| POST | /api/tasks/{id}/accept | 接单 | 是 |
| POST | /api/tasks/{id}/deliver | 提交交付 | 是 |
| POST | /api/tasks/{id}/confirm | 确认完工 | 是 |
| GET | /api/tasks/my/{type} | 我的任务 | 是 |
| GET | /api/materials | 资料列表 | 否 |
| GET | /api/materials/{id} | 资料详情 | 否 |
| POST | /api/materials | 上架资料 | 是 |
| POST | /api/materials/{id}/purchase | 购买下单 | 是 |
| GET | /api/materials/{id}/download-url | 下载签名 URL | 是 |
| GET | /api/materials/my/showcase | 我的橱窗 | 是 |
| GET | /api/notifications/activities | 动态通知 | 是 |
| GET | /api/notifications/unread-count | 未读数 | 是 |
| POST | /api/notifications/activities/{id}/read | 标记已读 | 是 |
| GET | /api/messages/conversations | 会话列表 | 是 |
| GET | /api/messages/{peerId} | 聊天记录 | 是 |
| POST | /api/messages/send | 发送私信 | 是 |
| GET | /api/orders | 我的订单 | 是 |
| GET | /api/wallet | 钱包 | 是 |
| POST | /api/wallet/withdraw | 提现申请 | 是 |
| POST | /api/pay/notify/wechat | 微信支付回调 | 否 |

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

**处理**：上传至 OSS **私有** Bucket；写入 `upload_file`（`biz_type=material`）供上架校验；返回 `fileKey`，禁止返回永久公网 URL。

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
  "tags": ["代取", "急单"]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| title, description, location, rewardAmount | 是 | 与前端 `pages/commission/create.vue` 一致 |
| category | 否 | 默认 `other`（前端当前未传） |
| deadline | 否 | 默认创建时间 + **7 天** |
| tags | 否 | 未传时按 category 映射默认标签（见 [mysql.md](./mysql.md) §4） |

**业务**

1. 实名 + 未封禁
2. `msgSecCheck` 标题与描述
3. `rewardAmount` 最低 100 分（1 元）
4. 插入 `task`，`status=0`；同步插入 `order`（`type=task`, `status=0`, `biz_id=task.id`, `title=task.title`, `amount=rewardAmount`）
5. 调微信统一下单（`out_trade_no` 关联该 `order`），返回 `payParams`

**响应 `data`**

```json
{
  "taskId": 1,
  "payParams": {
    "timeStamp": "1715769600",
    "nonceStr": "xxx",
    "package": "prepay_id=wx...",
    "signType": "RSA",
    "paySign": "xxx"
  }
}
```

---

### 8.4 任务支付（补单 / 重新支付）

```
POST /api/tasks/{id}/pay
```

仅 `status=0` 且发布者本人可调。

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

**事务内**

1. `task.status = 4`，`completed_at = now`
2. 计算平台手续费（可配置，如 5%）
3. `wallet_record` 接单人入账
4. `user.wallet_balance` 增加净额
5. （可选）发布者通知

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
  "coverUrl": "https://...",
  "category": "report"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| title, fileKey | 是 | 与前端 `pages/market/publish.vue` 一致 |
| category | 否 | 默认 `report`（前端当前未传） |

- 校验 `fileKey` 归属 `upload_file` 表中当前用户记录
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

### 9.4 购买资料

```
POST /api/materials/{id}/purchase
```

- 不能买自己的；已 `owned` → `422`
- 创建 `order`（type=material, status=pending）
- 返回微信支付参数

**响应**

```json
{
  "orderId": "O20260516001",
  "payParams": { /* 同 8.3 */ }
}
```

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

## 十一、订单与钱包

### 11.1 我的订单

```
GET /api/orders?page=1&pageSize=10&type=
```

`type` 可选：`material` | `task`

数据来源：统一查询 `order` 表（资料购买与任务支付均写入，见 §8.3、§9.4、§12.2）。

```json
{
  "list": [
    {
      "id": 1,
      "orderNo": "O20260516001",
      "type": "material",
      "title": "电路分析实验报告",
      "amount": 100,
      "status": "completed",
      "createdAt": "..."
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 10
}
```

`status` 字符串映射：`0` → `pending`，`1` → `completed`，`2` → `cancelled`（与 §3.5 一致）。

---

### 11.2 钱包

```
GET /api/wallet
```

```json
{
  "balance": 12800,
  "records": [
    {
      "id": 1,
      "type": "income",
      "amount": 500,
      "remark": "代办悬赏收入",
      "createdAt": "..."
    }
  ]
}
```

`records` 默认最近 20 条，可加分页。

---

### 11.3 提现申请

```
POST /api/wallet/withdraw
```

```json
{ "amount": 5000 }
```

- `amount` ≤ 可用余额（`wallet_balance - wallet_frozen`），最低 100 分
- `wallet_frozen` 增加对应金额，生成 `withdraw` 待 Admin 审核打款

**响应 `data`（示例）**

```json
{
  "id": 1,
  "status": "pending"
}
```

提现 `status` 映射：`0` → `pending`，`1` → `completed`，`2` → `rejected`。

---

## 十二、微信支付

### 12.1 统一下单（内部）

在创建任务、购买资料时调用微信 JSAPI 下单：

- `openid` 从当前用户表取
- `out_trade_no` 建议：`TASK_{taskId}` / `MAT_{orderId}`
- `notify_url`：`https://api.xxx.com/api/pay/notify/wechat`

### 12.2 支付回调

```
POST /api/pay/notify/wechat
Content-Type: application/json 或 text/xml（按微信 v3 规范）
```

**要求**

1. 验签
2. **幂等**：同一 `out_trade_no` 只处理一次
3. 事务内：

| 订单类型 | 处理 |
|----------|------|
| 任务支付 | `order.status: 0→1`（`type=task`）；`task.status: 0→1` |
| 资料购买 | `order.status: 0→1`（`type=material`）；插入 `user_material`；卖家钱包入账（扣手续费） |

两种类型均先写入 `pay_notify_log` 保证幂等，再更新 `order` 与业务表。

**响应**：按微信文档返回成功 ACK。

### 12.3 返回前端的 payParams

```json
{
  "timeStamp": "1715769600",
  "nonceStr": "5K8264ILTKCH16CQ2502SI8ZNMTM67VS",
  "package": "prepay_id=wx201410272009395522657a690389285100",
  "signType": "RSA",
  "paySign": "..."
}
```

与小程序 `uni.requestPayment` 字段一致。

---

## 十三、定时任务

| 任务 | Cron 建议 | 逻辑 |
|------|-----------|------|
| 自动验收 | 每小时 | `status=3` 且 `delivered_at < now()-48h` → 调 `confirm` 同款事务 |
| 点赞同步 | 每 5 分钟 | Redis 点赞数刷回 MySQL（若采用 Redis） |
| 待支付关闭 | 每天 | `status=0` 且超过 24h 未支付 → `status=5` |

---

## 十四、数据库设计参考

> **完整 `CREATE TABLE` 语句**见 [mysql.md](./mysql.md)。

### 14.1 核心表（简表）

| 表名 | 说明 |
|------|------|
| user | 用户、openid、钱包余额/冻结、实名、status |
| user_follow | 关注，UK(follower_id, followee_id) |
| upload_file | 上传暂存，fileKey 归属校验 |
| task | 悬赏，含 version、tags(JSON) |
| material | 资料，含 file_key、file_name |
| user_material | 购买关系，UK(user_id, material_id) |
| post | 帖子，含 is_deleted 软删除 |
| post_like | 点赞，UK(user_id, post_id) |
| post_comment | 评论 |
| order | 统一订单（material / task） |
| wallet_record | 流水 |
| withdraw | 提现申请 |
| activity_notification | 动态通知 |
| conversation | 私信会话摘要 |
| message | 私信消息 |
| pay_notify_log | 支付回调幂等，UK(out_trade_no) |

### 14.2 关键字段示例 `task`

```sql
id, publisher_id, acceptor_id, title, description, location,
reward_amount, status, category, tags(JSON), delivery_note, delivery_images(JSON),
version, created_at, accepted_at, delivered_at, completed_at, deadline
```

### 14.3 索引建议

- `post(status, created_at)`
- `task(status, category, created_at)`
- `material(status, created_at)`
- `activity_notification(user_id, read, created_at)`

（其余见 [mysql.md](./mysql.md) 各表定义。）

---

## 十五、管理端 Admin API 概要

> 供 PC 管理后台使用，认证方式建议独立 `Admin-JWT`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /admin/posts | 待审帖子列表 |
| POST | /admin/posts/{id}/audit | 通过/驳回 |
| GET | /admin/materials | 待审资料 |
| POST | /admin/materials/{id}/audit | 通过/驳回 |
| GET | /admin/tasks | 待审任务（若有） |
| GET | /admin/withdraws | 提现列表 |
| POST | /admin/withdraws/{id}/approve | 准予提现（商家转账到零钱） |
| POST | /admin/users/{id}/ban | 封禁/解封 |

审核驳回请求体：

```json
{
  "pass": false,
  "reason": "包含不文明用语"
}
```

通过后目标 `status=1`，并发送微信订阅消息通知用户。

---

## 附录 A：错误 message 一览

| message | code | 场景 |
|---------|------|------|
| 请先登录 | 401 | 无 Token |
| 账号已被封禁 | 403 | status=1 |
| 请先完成实名认证 | 422 | 发帖/发任务 |
| 包含敏感词，请修改后重试 | 422 | 内容安全 |
| 任务已被接单 | 409 | 抢单失败 |
| 不能接自己发布的任务 | 422 | 接单 |
| 不支持的文件格式 | 400 | 上传 |
| 余额不足 | 422 | 提现 |

---

## 附录 B：与前端页面对照

| 前端页面 | 接口 |
|----------|------|
| 登录 | POST /api/auth/wx-login |
| 首页-代办 | GET /api/tasks |
| 首页-帖子 | GET /api/posts |
| 首页-集市 | GET /api/materials |
| 发帖 | POST /api/posts |
| 帖子详情 | GET /api/posts/{id}，POST like/comments |
| 发布悬赏 | POST /api/tasks |
| 任务详情/交付/验收 | GET/POST tasks 系列 |
| 资料详情/购买 | GET materials，POST purchase，GET download-url |
| 消息-动态 | GET notifications/activities |
| 消息-私信 | GET conversations，GET messages/{peerId}，POST send |
| 用户主页 | GET users/{id}/home，POST follow |
| 我的/钱包/订单 | profile，wallet，orders，showcase |
| 搜索 | 复用 GET /api/posts、/api/tasks、/api/materials 的 `keyword`，无独立搜索接口 |

---

## 附录 C：前端实现现状与后端约定

> 前端工程路径：`E:\schoolshop\schoolshop`（uni-app，`api/` 与 `pages/` 与本文档对照）。

### C.1 请求体未传字段的默认值

| 接口 | 前端实际提交 | 后端默认 |
|------|----------------|----------|
| POST /api/tasks | title, description, location, rewardAmount | category=`other`；deadline=创建时间+7天；tags 按 category 映射 |
| POST /api/materials | title, description, price, fileKey, coverUrl | category=`report` |

### C.2 列表响应增强

- **帖子列表**：除 `categoryName` 外须返回 `categoryId`（前端首页可按 id 筛选，当前 mock 仅含 name）。
- **任务列表**：返回 `tags` 数组（可为后端按 category 生成）。

### C.3 v1 不实现的能力

| 能力 | 说明 |
|------|------|
| SAVE_POST / SHARE_POST / LIKE_COMMENT 通知 | 前端可展示 mock，后端 v1 不写入 |
| 帖子收藏 / 分享 | 无 API、无数据表 |
| 独立 /api/search | 搜索页分别调用 posts、tasks、materials 的 keyword |
| 实名认证接口 | info 页有入口未接 API；§5.6 建议后续实现 |

### C.4 订单与支付联动

- 创建任务时同步写 `order`（type=task, status=0）。
- 购买资料时写 `order`（type=material, status=0）。
- 支付回调统一更新 `order.status` 及 task / user_material 业务表（§12.2）。

### C.5 联调

前端将 `utils/config.js` 中 `BASE_URL` 指向后端，`USE_MOCK` 设为 `false`。

---

**文档维护**：后端实现时若字段有增减，请同步更新本文档、[mysql.md](./mysql.md) 及小程序 `api/` 层。
