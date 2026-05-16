# SchoolShop 数据库建表说明

> 与 [api.md](./api.md) §十四 表清单一一对应；金额字段均为 **int（分）**。  
> **ER 图**见 [er.md](./er.md)。  
> **建表 + 初始数据脚本**见 [init.sql](./init.sql)。

## 表清单总览

| 序号 | 表名 | 说明 |
|------|------|------|
| 1 | user | 用户、openid、钱包、实名 |
| 2 | user_follow | 关注关系 |
| 3 | upload_file | 上传暂存（fileKey 归属校验） |
| 4 | task | 代办悬赏（含 version 乐观锁） |
| 5 | material | 资料集市 |
| 6 | user_material | 资料购买关系 |
| 7 | post | 社区帖子 |
| 8 | post_like | 帖子点赞 |
| 9 | post_comment | 帖子评论 |
| 10 | `order` | 统一订单（资料购买 + 任务支付） |
| 11 | wallet_record | 钱包流水 |
| 12 | withdraw | 提现申请 |
| 13 | activity_notification | 动态通知 |
| 14 | conversation | 私信会话摘要 |
| 15 | message | 私信消息 |
| 16 | pay_notify_log | 微信支付回调幂等 |

---

## 1. 用户表 (user)

注意点：钱包余额 `wallet_balance` 为 int（分）；可用余额 = `wallet_balance - wallet_frozen`。

```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
  `openid` varchar(64) NOT NULL COMMENT '微信小程序唯一标识',
  `unionid` varchar(64) DEFAULT NULL COMMENT '微信开放平台 unionid（可选）',
  `nickname` varchar(50) DEFAULT '微信用户' COMMENT '用户昵称',
  `avatar` varchar(255) DEFAULT '' COMMENT '头像URL',
  `student_id` varchar(20) DEFAULT NULL COMMENT '学号',
  `real_name_verified` tinyint(1) DEFAULT '0' COMMENT '是否实名认证：0-未认证，1-已认证',
  `status` tinyint DEFAULT '0' COMMENT '用户状态：0-正常，1-封禁',
  `wallet_balance` int DEFAULT '0' COMMENT '钱包余额（单位：分）',
  `wallet_frozen` int DEFAULT '0' COMMENT '提现冻结金额（单位：分）',
  `bio` varchar(255) DEFAULT '' COMMENT '个性签名',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';
```

---

## 2. 用户关注表 (user_follow)

```sql
CREATE TABLE `user_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `follower_id` bigint NOT NULL COMMENT '关注者用户ID',
  `followee_id` bigint NOT NULL COMMENT '被关注者用户ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`) COMMENT '防止重复关注',
  KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户关注关系表';
```

---

## 3. 上传暂存表 (upload_file)

用于 §9.3 校验 `fileKey` 归属当前用户；图片上传也可记录 `biz_type=image`。

```sql
CREATE TABLE `upload_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '上传用户ID',
  `file_key` varchar(255) NOT NULL COMMENT 'OSS 相对路径',
  `file_name` varchar(255) DEFAULT '' COMMENT '原始文件名',
  `file_type` varchar(10) DEFAULT '' COMMENT '文件后缀：pdf/doc/docx/jpg 等',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型：image-图片, material-资料文件',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_file_key` (`user_id`, `file_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='上传文件暂存表';
```

---

## 4. 悬赏任务表 (task)

注意点：`version` 用于乐观锁防并发抢单；`tags` 为 JSON 数组；交付凭证 `delivery_images` 为 JSON。

```sql
CREATE TABLE `task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `publisher_id` bigint NOT NULL COMMENT '发布者（雇主）用户ID',
  `acceptor_id` bigint DEFAULT NULL COMMENT '接单人用户ID',
  `title` varchar(100) NOT NULL COMMENT '任务标题',
  `description` text NOT NULL COMMENT '任务详细描述',
  `location` varchar(255) NOT NULL COMMENT '跑腿交割地点',
  `reward_amount` int NOT NULL COMMENT '悬赏感谢费（单位：分）',
  `status` tinyint DEFAULT '0' COMMENT '状态：0-待支付, 1-招募中, 2-进行中, 3-待验收, 4-已完成, 5-已取消/退款',
  `category` varchar(20) NOT NULL COMMENT '分类：pickup/errand/study/other',
  `tags` json DEFAULT NULL COMMENT '展示标签数组，如 ["代取","急单"]',
  `delivery_note` varchar(255) DEFAULT NULL COMMENT '接单人交付留言',
  `delivery_images` json DEFAULT NULL COMMENT '交付凭证图片URL数组',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `deadline` datetime NOT NULL COMMENT '任务截止时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `accepted_at` datetime DEFAULT NULL COMMENT '接单时间',
  `delivered_at` datetime DEFAULT NULL COMMENT '交付时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成/验收时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_category` (`status`, `category`),
  KEY `idx_publisher` (`publisher_id`),
  KEY `idx_acceptor` (`acceptor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代办悬赏任务表';
```

**tags 默认映射（创建时未传则由后端填充）**：pickup → `["代取"]`，errand → `["跑腿"]`，study → `["学习"]`，other → `["其他"]`。

---

## 5. 资料集市表 (material)

注意点：`file_key` 仅存 OSS 相对路径，禁止存永久公网 URL。

```sql
CREATE TABLE `material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `user_id` bigint NOT NULL COMMENT '上传卖家用户ID',
  `title` varchar(100) NOT NULL COMMENT '资料标题',
  `description` text COMMENT '资料简介',
  `price` int NOT NULL DEFAULT '0' COMMENT '售价（单位：分，0为免费）',
  `cover_url` varchar(255) DEFAULT '' COMMENT '封面图URL',
  `file_key` varchar(255) NOT NULL COMMENT 'OSS私有桶内的fileKey',
  `file_name` varchar(255) DEFAULT '' COMMENT '原始文件名',
  `file_type` varchar(10) DEFAULT 'pdf' COMMENT '文件类型：pdf, doc, docx',
  `category` varchar(20) NOT NULL COMMENT '分类：report/note/lecture',
  `status` tinyint DEFAULT '0' COMMENT '状态：0-待审核, 1-已上架, 2-已驳回',
  `sold_count` int DEFAULT '0' COMMENT '已售数量',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上架时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`, `created_at`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资料集市表';
```

---

## 6. 用户资料购买关系表 (user_material)

```sql
CREATE TABLE `user_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '购买人用户ID',
  `material_id` bigint NOT NULL COMMENT '资料ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_material` (`user_id`, `material_id`) COMMENT '防止重复购买'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户资料购买关系表';
```

---

## 7. 帖子主表 (post)

设计亮点：

- `images` 使用 JSON，对接前端 `["https://..."]` 数组。
- `is_deleted` 软删除，满足 §7.6 删帖要求。
- `(status, created_at)` 复合索引用于信息流分页。

```sql
CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '帖子主键ID',
  `user_id` bigint NOT NULL COMMENT '发布者用户ID',
  `category_id` int NOT NULL COMMENT '分类ID：1-校园生活, 2-吐槽, 3-表白墙, 4-失物招领',
  `content` text NOT NULL COMMENT '帖子正文内容',
  `images` json DEFAULT NULL COMMENT '图片URL数组(JSON格式,最多9张)',
  `like_count` int DEFAULT '0' COMMENT '点赞数累计',
  `comment_count` int DEFAULT '0' COMMENT '评论数累计',
  `status` tinyint DEFAULT '0' COMMENT '审核状态：0-待审核, 1-已发布, 2-已驳回',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否软删除：0-未删除, 1-已删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`, `created_at`) COMMENT '信息流大厅核心分页索引',
  KEY `idx_user_id` (`user_id`) COMMENT '用户个人主页查询索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='社区动态帖子表';
```

---

## 8. 帖子点赞关系表 (post_like)

`uk_user_post` 唯一索引保证一人一票，支撑点赞 Toggle 幂等。

```sql
CREATE TABLE `post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '点赞用户ID',
  `post_id` bigint NOT NULL COMMENT '被点赞的帖子ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post` (`user_id`, `post_id`) COMMENT '唯一索引防止重复点赞',
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='帖子点赞关系表';
```

---

## 9. 帖子评论表 (post_comment)

`parent_id` 预留二级回复扩展。

```sql
CREATE TABLE `post_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论主键ID',
  `post_id` bigint NOT NULL COMMENT '帖子ID',
  `user_id` bigint NOT NULL COMMENT '发表评论的用户ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父评论ID(0为一级评论)',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`, `created_at`) COMMENT '帖子详情加载评论'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='帖子评论表';
```

---

## 10. 统一订单表 (`order`)

资料购买与任务支付均写入此表，供「我的订单」统一查询。`status`：0-待支付，1-已完成，2-已取消。

```sql
CREATE TABLE `order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单主键ID',
  `order_no` varchar(32) NOT NULL COMMENT '业务订单号，如 O20260516001',
  `user_id` bigint NOT NULL COMMENT '下单用户ID',
  `type` varchar(20) NOT NULL COMMENT '订单类型：material-资料, task-悬赏',
  `biz_id` bigint NOT NULL COMMENT '关联业务ID：material_id 或 task_id',
  `title` varchar(100) NOT NULL COMMENT '展示标题（冗余）',
  `amount` int NOT NULL COMMENT '订单金额（单位：分）',
  `status` tinyint DEFAULT '0' COMMENT '0-待支付, 1-已完成, 2-已取消',
  `out_trade_no` varchar(64) DEFAULT NULL COMMENT '微信支付商户订单号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `paid_at` datetime DEFAULT NULL COMMENT '支付完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  KEY `idx_user_type` (`user_id`, `type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一订单表';
```

---

## 11. 钱包流水表 (wallet_record)

```sql
CREATE TABLE `wallet_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `type` varchar(20) NOT NULL COMMENT '类型：income-收入, expense-支出',
  `amount` int NOT NULL COMMENT '金额（单位：分，正数）',
  `remark` varchar(255) DEFAULT '' COMMENT '备注说明',
  `biz_type` varchar(20) DEFAULT NULL COMMENT '关联业务：task/material/withdraw 等',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='钱包流水表';
```

---

## 12. 提现申请表 (withdraw)

```sql
CREATE TABLE `withdraw` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提现申请ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `amount` int NOT NULL COMMENT '提现金额（单位：分）',
  `status` tinyint DEFAULT '0' COMMENT '0-待处理, 1-已打款, 2-已拒绝',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `processed_at` datetime DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提现申请表';
```

---

## 13. 动态通知表 (activity_notification)

`followedBack` 不存库，查询时 join `user_follow` 计算。v1 不写入 SAVE_POST / SHARE_POST / LIKE_COMMENT。

```sql
CREATE TABLE `activity_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收者用户ID',
  `actor_id` bigint NOT NULL COMMENT '触发者用户ID',
  `type` varchar(30) NOT NULL COMMENT 'FOLLOW/LIKE_POST/COMMENT_POST 等',
  `post_id` bigint DEFAULT NULL COMMENT '关联帖子ID',
  `comment_text` varchar(500) DEFAULT NULL COMMENT '评论摘要（COMMENT_POST）',
  `post_thumbnail` varchar(255) DEFAULT NULL COMMENT '帖子首图缩略图',
  `read` tinyint(1) DEFAULT '0' COMMENT '是否已读：0-未读, 1-已读',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read_created` (`user_id`, `read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态通知表';
```

---

## 14. 私信会话表 (conversation)

发送私信时更新双方会话行（`user_id` + `peer_id` 各一条）。

```sql
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '当前用户ID',
  `peer_id` bigint NOT NULL COMMENT '对方用户ID',
  `last_message` varchar(500) DEFAULT '' COMMENT '最后一条消息摘要',
  `last_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `unread` int DEFAULT '0' COMMENT '未读消息数',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_peer` (`user_id`, `peer_id`),
  KEY `idx_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私信会话摘要表';
```

---

## 15. 私信消息表 (message)

```sql
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `sender_id` bigint NOT NULL COMMENT '发送者用户ID',
  `receiver_id` bigint NOT NULL COMMENT '接收者用户ID',
  `content` varchar(1000) NOT NULL COMMENT '消息内容（text 或图片 URL）',
  `type` varchar(20) DEFAULT 'text' COMMENT '消息类型：text/image',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_peer_created` (`sender_id`, `receiver_id`, `created_at`),
  KEY `idx_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私信消息表';
```

---

## 16. 支付回调幂等表 (pay_notify_log)

```sql
CREATE TABLE `pay_notify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `out_trade_no` varchar(64) NOT NULL COMMENT '微信支付商户订单号',
  `payload` text COMMENT '回调原始报文',
  `processed_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付回调幂等日志表';
```

---

## 索引补充说明

除各表内索引外，建议关注：

- `post(status, created_at)` — 信息流列表
- `task(status, category, created_at)` — 任务大厅筛选
- `material(status, created_at)` — 资料列表
- `activity_notification(user_id, read, created_at)` — 通知列表与未读
