-- =====================================================
-- 多渠道模型支持 - 数据库迁移脚本
-- 执行时间: 2026-11-24
-- 说明: 为 models 和 user 表添加 model_channel 字段
-- =====================================================

-- 1. 为 models 表添加 model_channel 字段
ALTER TABLE models
ADD COLUMN IF NOT EXISTS model_channel VARCHAR(32) DEFAULT 'dashscope'
COMMENT '模型所属渠道：dashscope=阿里云百炼，openai=OpenAI兼容接口，zhipu=智谱GLM等'
AFTER model_type;

-- 为 models 表添加 multi_model 字段（是否走 multimodal-generation 端点）
ALTER TABLE models
ADD COLUMN IF NOT EXISTS multi_model TINYINT(1) DEFAULT 0
COMMENT '是否走 multimodal-generation 端点（新架构模型如 kimi-k3 需要）'
AFTER model_channel;

-- 为 models 表添加 enable_thinking 字段（是否需要显式开启思考模式）
ALTER TABLE models
ADD COLUMN IF NOT EXISTS enable_thinking TINYINT(1) DEFAULT 0
COMMENT '是否需要显式开启思考模式（强制思考模型如 glm 系列）'
AFTER multi_model;

-- 2. 为 user 表添加 model_channel 字段
ALTER TABLE user
ADD COLUMN IF NOT EXISTS model_channel VARCHAR(32) DEFAULT 'dashscope'
COMMENT '用户 API Key 对应的模型渠道：dashscope=阿里云百炼，openai=OpenAI兼容接口等'
AFTER api_key;

-- =====================================================
-- 验证迁移结果
-- =====================================================
-- 执行以下命令查看表结构：
-- DESC models;
-- DESC user;

-- =====================================================
-- 回滚脚本（如需回滚执行以下SQL）
-- =====================================================
-- ALTER TABLE models DROP COLUMN IF EXISTS model_channel;
-- ALTER TABLE models DROP COLUMN IF EXISTS multi_model;
-- ALTER TABLE models DROP COLUMN IF EXISTS enable_thinking;
-- ALTER TABLE user DROP COLUMN IF EXISTS model_channel;
