-- 试卷表和题目表
-- 创建时间: 2026-04-06

-- 试卷表
CREATE TABLE rubric (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '试卷标题',
    class_name VARCHAR(100) COMMENT '班级名称',
    
    create_id BIGINT NOT NULL COMMENT '创建者ID',
    create_student_no VARCHAR(50) COMMENT '创建者学号',
    is_public BOOLEAN DEFAULT FALSE COMMENT '是否公开',
    question_count INT DEFAULT 0 COMMENT '题目数量',
    
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    deleted TINYINT(2) DEFAULT 0,
    
    INDEX idx_create_id (create_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';

-- 题目表
CREATE TABLE question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rubric_id BIGINT NOT NULL COMMENT '试卷ID',
    order_index INT NOT NULL COMMENT '题目序号',
    
    question_text TEXT NOT NULL COMMENT '题目内容',
    question_type VARCHAR(50) NOT NULL COMMENT '题型',
    options_json TEXT COMMENT '选项',
    answer VARCHAR(1000) COMMENT '答案',
    explanation TEXT COMMENT '解析',
    calculation_steps_json TEXT COMMENT '计算步骤',
    
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    deleted TINYINT DEFAULT 0,
    
    INDEX idx_rubric_id (rubric_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';
