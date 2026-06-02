-- Task Bounty Platform 初始化数据脚本
-- 包含：默认分类、信用分规则配置、系统配置、默认管理员账户

-- =====================================================
-- 1. 默认任务分类
-- =====================================================
INSERT INTO task_categories (name, sort_order, enabled, created_at) VALUES
('跑腿', 1, TRUE, NOW()),
('设计', 2, TRUE, NOW()),
('编程', 3, TRUE, NOW()),
('文案', 4, TRUE, NOW()),
('其他', 5, TRUE, NOW());

-- =====================================================
-- 2. 信用分规则配置
-- =====================================================
INSERT INTO credit_rule_configs (rule_key, rule_name, threshold_value, score_delta, enabled, updated_at) VALUES
('COMPLETION_RATE_GOOD', '完成率达标加分阈值', '90', 5, TRUE, NOW()),
('COMPLETION_RATE_BAD', '完成率过低减分阈值', '70', -5, TRUE, NOW()),
('PRAISE_RATE_GOOD', '好评率达标加分阈值', '95', 3, TRUE, NOW()),
('PRAISE_RATE_BAD', '好评率过低减分阈值', '80', -3, TRUE, NOW()),
('OVERTIME_PENALTY', '超时未交付扣分', '1', -8, TRUE, NOW()),
('VOLUNTARY_QUIT_PENALTY', '主动放弃任务扣分', '1', -3, TRUE, NOW()),
('PUBLISHER_CANCEL_PENALTY', '发布者无故下架扣分', '1', -5, TRUE, NOW()),
('FALSE_REPORT_PENALTY', '不实举报扣分', '1', -3, TRUE, NOW()),
('FILTER_VIOLATION_PENALTY', '敏感词过滤违规扣分', '3', -3, TRUE, NOW());

-- =====================================================
-- 3. 系统配置
-- =====================================================
INSERT INTO system_configs (config_key, config_value, config_type, description, updated_at) VALUES
('credit.initial_score', '80', 'INTEGER', '初始信用分', NOW()),
('credit.min_score', '0', 'INTEGER', '信用分下限', NOW()),
('credit.max_score', '100', 'INTEGER', '信用分上限', NOW()),
('credit.warning_threshold', '60', 'INTEGER', '信用分风险提示阈值', NOW()),
('credit.restrict_threshold', '40', 'INTEGER', '信用分限制接单阈值', NOW()),
('task.auto_cancel_days', '14', 'INTEGER', '无人接单自动取消天数', NOW()),
('task.reminder_hours', '24', 'INTEGER', '交付超时提醒提前量(小时)', NOW()),
('task.extend_ratio', '50', 'INTEGER', '截止时间单次延长比例(%)', NOW()),
('task.max_extend_times', '2', 'INTEGER', '最大延长次数', NOW()),
('task.auto_confirm_hours', '72', 'INTEGER', '待确认自动完成小时数', NOW()),
('attachment.save_days', '30', 'INTEGER', '附件保存天数', NOW()),
('attachment.max_file_size', '20971520', 'INTEGER', '附件最大大小(字节)', NOW()),
('attachment.max_delivery_size', '31457280', 'INTEGER', '交付物最大大小(字节)', NOW()),
('platform.freeze_days', '40', 'INTEGER', '毕业生冻结天数', NOW()),
('platform.max_concurrent_orders', '3', 'INTEGER', '最大同时接单数', NOW()),
('platform.user_sync_cron', '0 0 3 1 * ?', 'CRON', '用户信息同步Cron表达式', NOW()),
('platform.nickname_cooldown', '30', 'INTEGER', '昵称修改冷却天数', NOW()),
('sensitive_words', '脏话,诈骗,色情,赌博,毒品,暴力', 'STRING', '敏感词过滤列表(逗号分隔)', NOW());

-- =====================================================
-- 4. 默认管理员账户
-- 学号: admin001, 姓名: 系统管理员
-- 实际使用前需要通过登录接口激活（使用studentNo=admin001登录）
-- =====================================================
-- 管理员账户将在首次通过 /api/auth/login 使用 admin001 登录时自动创建
-- 然后需要手动在数据库中将其 role 字段改为 'ADMIN'
