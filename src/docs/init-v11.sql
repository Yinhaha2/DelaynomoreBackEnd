-- SchoolShop v1.1 增量迁移：校园积分 + 校园扩展
-- 用法：mysql -u root -p schoolshop < init-v11.sql

USE `schoolshop`;

-- user 扩展：等级经验（积分仍用 wallet_balance / wallet_frozen）
-- 若列已存在可跳过以下 ALTER（首次迁移执行）
ALTER TABLE `user`
  ADD COLUMN `level` int DEFAULT 1 COMMENT '积分等级' AFTER `wallet_frozen`,
  ADD COLUMN `exp` int DEFAULT 0 COMMENT '经验值' AFTER `level`;

ALTER TABLE `task`
  ADD COLUMN `from_spot_id` bigint DEFAULT NULL COMMENT '起点点位' AFTER `location`,
  ADD COLUMN `to_spot_id` bigint DEFAULT NULL COMMENT '终点点位' AFTER `from_spot_id`;

CREATE TABLE IF NOT EXISTS `campus_spot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `alias` varchar(64) DEFAULT '',
  `zone` varchar(32) DEFAULT '',
  `x` int NOT NULL COMMENT '相对坐标 0-100',
  `y` int NOT NULL,
  `category` varchar(20) NOT NULL,
  `hot` int DEFAULT 0,
  `task_count` int DEFAULT 0,
  `desc` varchar(255) DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `campus_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL,
  `cover` varchar(255) DEFAULT '',
  `location` varchar(100) DEFAULT '',
  `start_at` datetime NOT NULL,
  `end_at` datetime NOT NULL,
  `category` varchar(20) NOT NULL,
  `capacity` int DEFAULT 100,
  `joined_count` int DEFAULT 0,
  `tags` json DEFAULT NULL,
  `description` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_start` (`start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `event_join` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_user` (`event_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `partner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `publisher_id` bigint NOT NULL,
  `title` varchar(100) NOT NULL,
  `description` text,
  `category` varchar(20) NOT NULL,
  `tags` json DEFAULT NULL,
  `time_text` varchar(64) DEFAULT '',
  `location` varchar(100) DEFAULT '',
  `need_count` int DEFAULT 2,
  `joined_count` int DEFAULT 1,
  `status` varchar(16) DEFAULT 'open',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `partner_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `partner_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_partner_user` (`partner_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `teacher` varchar(64) DEFAULT '',
  `college` varchar(64) DEFAULT '',
  `rating` decimal(3,1) DEFAULT 0,
  `review_count` int DEFAULT 0,
  `difficulty` tinyint DEFAULT 3,
  `useful` tinyint DEFAULT 3,
  `tags` json DEFAULT NULL,
  `cover` varchar(255) DEFAULT '',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `course_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `rating` tinyint NOT NULL,
  `content` varchar(500) NOT NULL,
  `likes` int DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `schedule_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `day_of_week` tinyint NOT NULL COMMENT '1=周一',
  `name` varchar(64) NOT NULL,
  `teacher` varchar(64) DEFAULT '',
  `place` varchar(64) DEFAULT '',
  `start_time` varchar(8) NOT NULL,
  `end_time` varchar(8) NOT NULL,
  `color` varchar(16) DEFAULT '#667eea',
  PRIMARY KEY (`id`),
  KEY `idx_user_day` (`user_id`, `day_of_week`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `checkin_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `checkin_date` date NOT NULL,
  `reward` int DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `badge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `icon` varchar(16) DEFAULT '',
  `desc` varchar(255) DEFAULT '',
  `rarity` varchar(16) DEFAULT 'common',
  `target_type` varchar(32) DEFAULT '',
  `target_value` int DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_badge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `badge_id` bigint NOT NULL,
  `unlocked_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge` (`user_id`, `badge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(20) NOT NULL,
  `target_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(20) NOT NULL,
  `target_id` bigint NOT NULL,
  `reason` varchar(100) NOT NULL,
  `detail` varchar(500) DEFAULT '',
  `status` varchar(16) DEFAULT 'pending',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `feed_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `item_id` varchar(32) NOT NULL,
  `action` varchar(16) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_item` (`user_id`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `search_hot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(64) NOT NULL,
  `heat` int DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子数据（幂等：仅空表插入）
INSERT INTO `campus_spot` (`name`, `alias`, `zone`, `x`, `y`, `category`, `hot`, `task_count`, `desc`)
SELECT * FROM (SELECT '图书馆' AS n,'静心楼','中区',48,42,'study',98,12,'通宵自习友好') t
WHERE NOT EXISTS (SELECT 1 FROM campus_spot LIMIT 1);

INSERT INTO `campus_spot` (`name`,`alias`,`zone`,`x`,`y`,`category`,`hot`,`task_count`,`desc`) VALUES
('菜鸟驿站','快递站','东区',78,38,'life',90,18,'取件高峰 11-13 点'),
('6号宿舍','宿舍楼','西区',28,28,'dorm',70,8,'女生宿舍'),
('二食堂','食堂','南区',55,72,'food',85,15,'麻辣香锅推荐'),
('大礼堂','礼堂','中区',50,55,'culture',60,3,'活动场地'),
('体育馆','运动场','北区',20,65,'sport',75,6,'羽毛球场地'),
('一教','教学楼','东区',65,50,'teach',80,5,'高数教室集中'),
('图书馆三楼','自习区','中区',48,40,'study',88,10,'安静区'),
('南门','校门','南区',50,95,'gate',50,2,'地铁接驳'),
('打印店','文印','西区',35,45,'life',65,9,'装订快'),
('实验楼','实验室','东区',72,58,'teach',55,4,'电路实验');

UPDATE `task` SET `from_spot_id`=2, `to_spot_id`=3 WHERE `id`=1 AND `from_spot_id` IS NULL;

INSERT INTO `campus_event` (`title`,`cover`,`location`,`start_at`,`end_at`,`category`,`capacity`,`joined_count`,`tags`,`description`)
SELECT '春季校园招聘双选会','https://picsum.photos/seed/event1/400','大礼堂',
       DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY) + INTERVAL 6 HOUR,
       'career',500,328,'["招聘","热门"]','多家名企进校，带好简历'
WHERE NOT EXISTS (SELECT 1 FROM campus_event LIMIT 1);

INSERT INTO `course` (`name`,`teacher`,`college`,`rating`,`review_count`,`difficulty`,`useful`,`tags`,`cover`)
SELECT '高等数学 A','张教授','理学院',4.6,128,4,5,'["硬核","点名少"]','https://picsum.photos/seed/course1/200'
WHERE NOT EXISTS (SELECT 1 FROM course LIMIT 1);

INSERT INTO `course` (`name`,`teacher`,`college`,`rating`,`review_count`,`difficulty`,`useful`,`tags`,`cover`) VALUES
('数据结构','李老师','计算机学院',4.4,96,4,5,'["算法","作业多"]','https://picsum.photos/seed/course2/200'),
('大学英语','王老师','外语学院',4.2,64,2,4,'["口语","轻松"]','https://picsum.photos/seed/course3/200');

INSERT INTO `badge` (`name`,`icon`,`desc`,`rarity`,`target_type`,`target_value`)
SELECT '初来乍到','🌱','完成首次登录','common','login',1
WHERE NOT EXISTS (SELECT 1 FROM badge LIMIT 1);

INSERT INTO `badge` (`name`,`icon`,`desc`,`rarity`,`target_type`,`target_value`) VALUES
('签到达人','📅','累计签到 7 天','rare','checkin',7),
('学霸本霸','📚','上架 3 份资料','epic','material',3),
('互助先锋','🤝','完成 5 单悬赏','legendary','task',5);

INSERT INTO `search_hot` (`word`,`heat`)
SELECT '代取快递',980 WHERE NOT EXISTS (SELECT 1 FROM search_hot LIMIT 1);
INSERT INTO `search_hot` (`word`,`heat`) VALUES ('期末笔记',860),('高数答疑',720),('图书馆占座',650);

INSERT INTO `schedule_course` (`user_id`,`day_of_week`,`name`,`teacher`,`place`,`start_time`,`end_time`,`color`)
SELECT 1,1,'高等数学 A','张教授','一教 301','08:00','09:40','#667eea'
WHERE NOT EXISTS (SELECT 1 FROM schedule_course LIMIT 1);

INSERT INTO `schedule_course` (`user_id`,`day_of_week`,`name`,`teacher`,`place`,`start_time`,`end_time`,`color`) VALUES
(1,1,'数据结构','李老师','二教 205','10:00','11:40','#764ba2'),
(1,3,'高等数学 A','张教授','一教 301','08:00','09:40','#667eea'),
(1,5,'大学英语','王老师','外语楼 102','14:00','15:40','#f093fb');

INSERT INTO `partner` (`publisher_id`,`title`,`description`,`category`,`tags`,`time_text`,`location`,`need_count`,`joined_count`)
SELECT 2,'今晚图书馆三楼拼自习','一起刷题，互相监督','study','["自习","图书馆"]','今晚 19:00','图书馆三楼',4,1
WHERE NOT EXISTS (SELECT 1 FROM partner LIMIT 1);

INSERT INTO `partner_member` (`partner_id`,`user_id`) SELECT 1,2 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM partner_member LIMIT 1);

UPDATE `user` SET `level`=5, `exp`=420 WHERE `id`=1;
