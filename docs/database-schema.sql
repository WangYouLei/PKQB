-- PKQB 数据库表结构
-- 适用于 MySQL

CREATE DATABASE IF NOT EXISTS pkqb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pkqb;

-- 班级表
CREATE TABLE `class` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '班级主键ID',
    `class_name` VARCHAR(64) NOT NULL COMMENT '班级名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_name` (`class_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

-- 用户表
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '加密密码',
    `student_no` VARCHAR(32) NOT NULL COMMENT '学号',
    `class_id` INT NOT NULL COMMENT '所属班级ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_class_id` (`class_id`),
    CONSTRAINT `fk_user_class` FOREIGN KEY (`class_id`) REFERENCES `class` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- HTML文件表
CREATE TABLE `file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '创建者ID',
    `rubric_id` BIGINT COMMENT '关联的试卷ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `minio_key` VARCHAR(512) NOT NULL COMMENT 'MinIO对象路径',
    `is_private` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否私有：1=私有（仅自己可见），0=公开（班级可见）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';
