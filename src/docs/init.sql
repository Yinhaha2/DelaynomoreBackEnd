-- =============================================================================
-- SchoolShop 数据库初始化脚本
-- 适用：MySQL 8.0+
-- 说明：建表 + 开发联调用初始数据（对齐前端 mock）
-- 用法：mysql -u root -p < init.sql
-- 文档：mysql.md / er.md / api.md
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- 0. 创建库（可按需修改库名）
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `schoolshop`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `schoolshop`;

-- -----------------------------------------------------------------------------
-- 1. 删表（依赖倒序）
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `pay_notify_log`;
DROP TABLE IF EXISTS `message`;
DROP TABLE IF EXISTS `conversation`;
DROP TABLE IF EXISTS `activity_notification`;
DROP TABLE IF EXISTS `withdraw`;
DROP TABLE IF EXISTS `wallet_record`;
DROP TABLE IF EXISTS `order`;
DROP TABLE IF EXISTS `post_comment`;
DROP TABLE IF EXISTS `post_like`;
DROP TABLE IF EXISTS `post`;
DROP TABLE IF EXISTS `user_material`;
DROP TABLE IF EXISTS `material`;
DROP TABLE IF EXISTS `task`;
DROP TABLE IF EXISTS `upload_file`;
DROP TABLE IF EXISTS `user_follow`;
DROP TABLE IF EXISTS `user`;

-- -----------------------------------------------------------------------------
-- 2. 建表
-- -----------------------------------------------------------------------------

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

CREATE TABLE `user_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `follower_id` bigint NOT NULL COMMENT '关注者用户ID',
  `followee_id` bigint NOT NULL COMMENT '被关注者用户ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`),
  KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户关注关系表';

CREATE TABLE `upload_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '上传用户ID',
  `file_key` varchar(255) NOT NULL COMMENT 'OSS 相对路径',
  `file_name` varchar(255) DEFAULT '' COMMENT '原始文件名',
  `file_type` varchar(10) DEFAULT '' COMMENT '文件后缀',
  `biz_type` varchar(20) NOT NULL COMMENT 'image / material',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_file_key` (`user_id`, `file_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='上传文件暂存表';

CREATE TABLE `task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `publisher_id` bigint NOT NULL COMMENT '发布者用户ID',
  `acceptor_id` bigint DEFAULT NULL COMMENT '接单人用户ID',
  `title` varchar(100) NOT NULL COMMENT '任务标题',
  `description` text NOT NULL COMMENT '任务详细描述',
  `location` varchar(255) NOT NULL COMMENT '跑腿交割地点',
  `reward_amount` int NOT NULL COMMENT '悬赏感谢费（单位：分）',
  `status` tinyint DEFAULT '0' COMMENT '0-待支付,1-招募中,2-进行中,3-待验收,4-已完成,5-已取消',
  `category` varchar(20) NOT NULL COMMENT 'pickup/errand/study/other',
  `tags` json DEFAULT NULL COMMENT '展示标签',
  `delivery_note` varchar(255) DEFAULT NULL COMMENT '交付留言',
  `delivery_images` json DEFAULT NULL COMMENT '交付凭证图片',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `deadline` datetime NOT NULL COMMENT '截止时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `accepted_at` datetime DEFAULT NULL COMMENT '接单时间',
  `delivered_at` datetime DEFAULT NULL COMMENT '交付时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_category` (`status`, `category`),
  KEY `idx_publisher` (`publisher_id`),
  KEY `idx_acceptor` (`acceptor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代办悬赏任务表';

CREATE TABLE `material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `user_id` bigint NOT NULL COMMENT '卖家用户ID',
  `title` varchar(100) NOT NULL COMMENT '资料标题',
  `description` text COMMENT '资料简介',
  `price` int NOT NULL DEFAULT '0' COMMENT '售价（分）',
  `cover_url` varchar(255) DEFAULT '' COMMENT '封面图',
  `file_key` varchar(255) NOT NULL COMMENT 'OSS fileKey',
  `file_name` varchar(255) DEFAULT '' COMMENT '原始文件名',
  `file_type` varchar(10) DEFAULT 'pdf' COMMENT 'pdf/doc/docx',
  `category` varchar(20) NOT NULL COMMENT 'report/note/lecture',
  `status` tinyint DEFAULT '0' COMMENT '0-待审核,1-已上架,2-已驳回',
  `sold_count` int DEFAULT '0' COMMENT '已售数量',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上架时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`, `created_at`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资料集市表';

CREATE TABLE `user_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '购买人',
  `material_id` bigint NOT NULL COMMENT '资料ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_material` (`user_id`, `material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户资料购买关系表';

CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '帖子ID',
  `user_id` bigint NOT NULL COMMENT '发布者',
  `category_id` int NOT NULL COMMENT '1-校园生活,2-吐槽,3-表白墙,4-失物招领',
  `content` text NOT NULL COMMENT '正文',
  `images` json DEFAULT NULL COMMENT '图片URL数组',
  `like_count` int DEFAULT '0' COMMENT '点赞数',
  `comment_count` int DEFAULT '0' COMMENT '评论数',
  `status` tinyint DEFAULT '0' COMMENT '0-待审核,1-已发布,2-已驳回',
  `is_deleted` tinyint DEFAULT '0' COMMENT '0-否,1-软删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`, `created_at`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='社区帖子表';

CREATE TABLE `post_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '点赞用户',
  `post_id` bigint NOT NULL COMMENT '帖子ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='帖子点赞表';

CREATE TABLE `post_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `post_id` bigint NOT NULL COMMENT '帖子ID',
  `user_id` bigint NOT NULL COMMENT '评论用户',
  `parent_id` bigint DEFAULT '0' COMMENT '父评论ID',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='帖子评论表';

CREATE TABLE `order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(32) NOT NULL COMMENT '业务订单号',
  `user_id` bigint NOT NULL COMMENT '下单用户',
  `type` varchar(20) NOT NULL COMMENT 'material / task',
  `biz_id` bigint NOT NULL COMMENT 'material_id 或 task_id',
  `title` varchar(100) NOT NULL COMMENT '展示标题',
  `amount` int NOT NULL COMMENT '金额（分）',
  `status` tinyint DEFAULT '0' COMMENT '0-待支付,1-已完成,2-已取消',
  `out_trade_no` varchar(64) DEFAULT NULL COMMENT '微信商户订单号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `paid_at` datetime DEFAULT NULL COMMENT '支付时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  KEY `idx_user_type` (`user_id`, `type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一订单表';

CREATE TABLE `wallet_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `type` varchar(20) NOT NULL COMMENT 'income / expense',
  `amount` int NOT NULL COMMENT '金额（分）',
  `remark` varchar(255) DEFAULT '' COMMENT '备注',
  `biz_type` varchar(20) DEFAULT NULL COMMENT 'task/material/withdraw',
  `biz_id` bigint DEFAULT NULL COMMENT '业务ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='钱包流水表';

CREATE TABLE `withdraw` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '提现ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `amount` int NOT NULL COMMENT '提现金额（分）',
  `status` tinyint DEFAULT '0' COMMENT '0-待处理,1-已打款,2-已拒绝',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT '拒绝原因',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `processed_at` datetime DEFAULT NULL COMMENT '处理时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提现申请表';

CREATE TABLE `activity_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收者',
  `actor_id` bigint NOT NULL COMMENT '触发者',
  `type` varchar(30) NOT NULL COMMENT '通知类型',
  `post_id` bigint DEFAULT NULL COMMENT '关联帖子',
  `comment_text` varchar(500) DEFAULT NULL COMMENT '评论摘要',
  `post_thumbnail` varchar(255) DEFAULT NULL COMMENT '帖子缩略图',
  `read` tinyint(1) DEFAULT '0' COMMENT '0-未读,1-已读',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read_created` (`user_id`, `read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态通知表';

CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '当前用户',
  `peer_id` bigint NOT NULL COMMENT '对方用户',
  `last_message` varchar(500) DEFAULT '' COMMENT '最后消息',
  `last_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `unread` int DEFAULT '0' COMMENT '未读数',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_peer` (`user_id`, `peer_id`),
  KEY `idx_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私信会话表';

CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `sender_id` bigint NOT NULL COMMENT '发送者',
  `receiver_id` bigint NOT NULL COMMENT '接收者',
  `content` varchar(1000) NOT NULL COMMENT '内容',
  `type` varchar(20) DEFAULT 'text' COMMENT 'text / image',
  `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_peer_created` (`sender_id`, `receiver_id`, `created_at`),
  KEY `idx_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='私信消息表';

CREATE TABLE `pay_notify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `out_trade_no` varchar(64) NOT NULL COMMENT '微信商户订单号',
  `payload` text COMMENT '回调报文',
  `processed_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付回调幂等表';

-- -----------------------------------------------------------------------------
-- 3. 初始数据（开发联调，ID 固定便于测试）
-- -----------------------------------------------------------------------------

-- 3.1 用户（id 1 为默认登录测试账号）
INSERT INTO `user` (`id`, `openid`, `nickname`, `avatar`, `student_id`, `real_name_verified`, `status`, `wallet_balance`, `wallet_frozen`, `bio`, `last_login_at`) VALUES
(1, 'mock_openid_001', '银哈哈', 'https://picsum.photos/seed/u1/200', '2021001001', 1, 0, 12800, 0, '校园互助达人', NOW()),
(2, 'mock_openid_002', 'Helena', 'https://picsum.photos/seed/u2/200', '2021001002', 1, 0, 3200, 0, '热爱生活，乐于助人', NOW()),
(3, 'mock_openid_003', 'Daniel', 'https://picsum.photos/seed/u3/200', '2021001003', 1, 0, 5600, 0, '期末冲刺中', NOW()),
(4, 'mock_openid_004', '小明', 'https://picsum.photos/seed/u4/200', '2021001004', 1, 0, 800, 0, '', NOW()),
(5, 'mock_openid_005', '小红', 'https://picsum.photos/seed/u5/200', '2021001005', 0, 0, 1500, 0, '二食堂常客', NOW()),
(6, 'mock_openid_006', 'starryskies23', 'https://picsum.photos/seed/a1/200', '2021001006', 0, 0, 0, 0, '星星爱好者', NOW());

-- 3.2 关注
INSERT INTO `user_follow` (`follower_id`, `followee_id`) VALUES
(6, 1),
(1, 3),
(1, 2);

-- 3.3 上传暂存
INSERT INTO `upload_file` (`user_id`, `file_key`, `file_name`, `file_type`, `biz_type`) VALUES
(3, 'materials/2026/05/u3_circuit.pdf', '电路分析实验报告.pdf', 'pdf', 'material'),
(3, 'materials/2026/05/u3_ds_note.pdf', '数据结构复习笔记.pdf', 'pdf', 'material'),
(2, 'posts/2026/05/u2_flower.jpg', 'flower.jpg', 'jpg', 'image');

-- 3.4 悬赏任务（status=1 招募中，供首页列表）
INSERT INTO `task` (`id`, `publisher_id`, `acceptor_id`, `title`, `description`, `location`, `reward_amount`, `status`, `category`, `tags`, `version`, `deadline`, `accepted_at`, `delivered_at`, `completed_at`, `created_at`) VALUES
(1, 4, NULL, '代取快递 - 菜鸟驿站', '帮忙取两个包裹，送到6号宿舍楼楼下，取件码私聊', '菜鸟驿站 → 6号宿舍', 500, 1, 'pickup', '["代取","急单"]', 0, DATE_ADD(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(2, 5, NULL, '代买食堂晚饭', '二食堂一份麻辣香锅，微辣，送到图书馆三楼自习区', '二食堂 → 图书馆', 800, 1, 'errand', '["代买","食堂"]', 0, DATE_ADD(NOW(), INTERVAL 12 HOUR), NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 3, NULL, '高数作业答疑 1 小时', '线上腾讯会议，讲解第三章习题，基础薄弱友好', '线上', 1500, 1, 'study', '["学习","线上"]', 0, DATE_ADD(NOW(), INTERVAL 2 DAY), NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(4, 4, 2, '代送文件到行政楼', '已接单进行中示例', '图书馆 → 行政楼', 300, 2, 'errand', '["跑腿"]', 1, DATE_ADD(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(5, 4, 1, '代取外卖（已完成）', '二食堂麻辣烫，已完成验收', '二食堂 → 5号宿舍', 500, 4, 'errand', '["代买"]', 2, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(6, 3, 2, '打印店取资料（已完成）', '打印店取装订资料送到实验室', '打印店 → 实验楼', 300, 4, 'errand', '["跑腿"]', 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 3 HOUR));

-- 3.5 资料集市（status=1 已上架）
INSERT INTO `material` (`id`, `user_id`, `title`, `description`, `price`, `cover_url`, `file_key`, `file_name`, `file_type`, `category`, `status`, `sold_count`) VALUES
(1, 3, '电路分析实验报告', '含完整仿真数据与结论', 100, 'https://picsum.photos/seed/m1/200', 'materials/2026/05/u3_circuit.pdf', '电路分析实验报告.pdf', 'pdf', 'report', 1, 12),
(2, 3, '数据结构期末复习笔记', '覆盖全部考点，手写扫描高清', 200, 'https://picsum.photos/seed/m2/200', 'materials/2026/05/u3_ds_note.pdf', '数据结构复习笔记.pdf', 'pdf', 'note', 1, 28),
(3, 3, '大学物理实验报告模板', '格式规范，可直接套用', 150, 'https://picsum.photos/seed/m3/200', 'materials/2026/05/u3_physics.docx', '物理实验报告.docx', 'docx', 'report', 1, 8),
(4, 2, 'C语言程序设计讲义', '老师课堂 PPT 整理版', 80, 'https://picsum.photos/seed/m4/200', 'materials/2026/05/u2_c_lecture.pdf', 'C语言讲义.pdf', 'pdf', 'lecture', 1, 45);

-- 3.6 资料购买（用户1 已购资料1）
INSERT INTO `user_material` (`user_id`, `material_id`) VALUES
(1, 1);

-- 3.7 帖子（status=1 已发布）
INSERT INTO `post` (`id`, `user_id`, `category_id`, `content`, `images`, `like_count`, `comment_count`, `status`, `is_deleted`, `created_at`) VALUES
(1, 2, 1, '今天在图书馆门口看到一片郁金香，春天真的来了～', '["https://picsum.photos/seed/flower/600/400"]', 21, 4, 1, 0, DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
(2, 3, 2, '期末周图书馆座位太难抢了，有没有同学分享抢座攻略？', NULL, 6, 18, 1, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(3, 2, 3, '想对图书馆三楼常坐窗边看书的同学说声，你的专注真的很打动人。', NULL, 52, 31, 1, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(4, 4, 4, '在一教捡到一个黑色水杯，杯身有贴纸，失主请联系我。', '["https://picsum.photos/seed/cup/600/400"]', 3, 2, 1, 0, DATE_SUB(NOW(), INTERVAL 8 HOUR));

-- 3.8 点赞
INSERT INTO `post_like` (`user_id`, `post_id`) VALUES
(1, 2),
(3, 1),
(4, 1);

-- 3.9 评论
INSERT INTO `post_comment` (`post_id`, `user_id`, `parent_id`, `content`, `created_at`) VALUES
(1, 3, 0, '写得好！', DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(1, 1, 0, '春天真美～', DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
(2, 2, 0, '建议早上7点前去占座', DATE_SUB(NOW(), INTERVAL 30 MINUTE));

-- 3.10 订单
INSERT INTO `order` (`id`, `order_no`, `user_id`, `type`, `biz_id`, `title`, `amount`, `status`, `out_trade_no`, `paid_at`) VALUES
(1, 'O20260516001', 1, 'material', 1, '电路分析实验报告', 100, 1, 'MAT_1_20260516001', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'O20260516002', 1, 'task', 1, '代取快递 - 菜鸟驿站', 500, 1, 'TASK_1_20260516001', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(3, 'O20260516003', 4, 'task', 2, '代买食堂晚饭', 800, 0, 'TASK_2_20260516003', NULL);

-- 3.11 钱包流水
INSERT INTO `wallet_record` (`user_id`, `type`, `amount`, `remark`, `biz_type`, `biz_id`, `created_at`) VALUES
(1, 'income', 475, '代办悬赏收入（扣手续费后）', 'task', 5, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
(1, 'expense', 100, '购买资料', 'material', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'income', 285, '代办悬赏收入（扣手续费后）', 'task', 6, DATE_SUB(NOW(), INTERVAL 3 HOUR));

-- 3.12 提现（待处理示例）
INSERT INTO `withdraw` (`user_id`, `amount`, `status`) VALUES
(1, 5000, 0);

-- 用户1 冻结 50 元用于提现演示
UPDATE `user` SET `wallet_frozen` = 5000 WHERE `id` = 1;

-- 3.13 动态通知（v1 仅 FOLLOW / LIKE_POST / COMMENT_POST）
INSERT INTO `activity_notification` (`user_id`, `actor_id`, `type`, `post_id`, `comment_text`, `post_thumbnail`, `read`, `created_at`) VALUES
(1, 6, 'FOLLOW', NULL, NULL, NULL, 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1, 2, 'LIKE_POST', 1, NULL, 'https://picsum.photos/seed/flower/200', 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1, 3, 'COMMENT_POST', 1, '写得好！', 'https://picsum.photos/seed/flower/200', 1, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 3.14 私信会话（用户1 ↔ 用户2，各一条会话记录）
INSERT INTO `conversation` (`user_id`, `peer_id`, `last_message`, `last_time`, `unread`) VALUES
(1, 2, '没问题！', DATE_SUB(NOW(), INTERVAL 5 MINUTE), 0),
(2, 1, '没问题！', DATE_SUB(NOW(), INTERVAL 5 MINUTE), 2);

-- 3.15 私信消息
INSERT INTO `message` (`sender_id`, `receiver_id`, `content`, `type`, `is_read`, `created_at`) VALUES
(2, 1, '嗨，明天有空帮我取个快递吗？', 'text', 1, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(1, 2, '可以的，几点？', 'text', 1, DATE_SUB(NOW(), INTERVAL 115 MINUTE)),
(2, 1, '下午三点左右，菜鸟驿站', 'text', 1, DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(2, 1, '好的，我明天帮你取', 'text', 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(1, 2, '没问题！', 'text', 1, DATE_SUB(NOW(), INTERVAL 5 MINUTE));

-- 3.16 支付回调日志（示例）
INSERT INTO `pay_notify_log` (`out_trade_no`, `payload`) VALUES
('MAT_1_20260516001', '{"mock":true,"trade_state":"SUCCESS"}'),
('TASK_1_20260516001', '{"mock":true,"trade_state":"SUCCESS"}');

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 4. 验证（可选，手动执行）
-- -----------------------------------------------------------------------------
-- SELECT COUNT(*) AS user_count FROM user;
-- SELECT id, nickname, wallet_balance, wallet_frozen FROM user;
-- SELECT id, title, status FROM task WHERE status = 1;
-- SELECT id, title, status FROM material WHERE status = 1;
-- SELECT id, content, like_count FROM post WHERE status = 1 AND is_deleted = 0;
