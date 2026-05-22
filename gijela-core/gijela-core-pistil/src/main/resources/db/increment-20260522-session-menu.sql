-- 会话管理菜单增量脚本（适用于已完成初始化的数据库）
-- 执行日期：2026-05-22
-- 说明：幂等脚本，可重复执行；不会影响已有数据

START TRANSACTION;

-- 1) 新增“会话管理”菜单（父级：系统管理 id=1）
INSERT INTO `aigc_sys_menu` (
  `id`, `parent_id`, `name`, `type`, `path`, `permission`, `icon`, `sort`, `status`, `remark`, `create_time`, `update_time`
)
SELECT
  15, 1, '会话管理', 'M', '/sys/sessions', 'auth:session:list', 'List', 5, 1, NULL, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_sys_menu` WHERE `id` = 15
);

-- 2) 新增“踢出会话”按钮权限（父级：会话管理 id=15）
INSERT INTO `aigc_sys_menu` (
  `id`, `parent_id`, `name`, `type`, `path`, `permission`, `icon`, `sort`, `status`, `remark`, `create_time`, `update_time`
)
SELECT
  151, 15, '踢出会话', 'B', NULL, 'auth:session:kickout', 'Delete', 1, 1, NULL, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_sys_menu` WHERE `id` = 151
);

-- 3) 新增“全部踢出”按钮权限（父级：会话管理 id=15）
INSERT INTO `aigc_sys_menu` (
  `id`, `parent_id`, `name`, `type`, `path`, `permission`, `icon`, `sort`, `status`, `remark`, `create_time`, `update_time`
)
SELECT
  152, 15, '全部踢出', 'B', NULL, 'auth:session:kickoutAll', 'Delete', 2, 1, NULL, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_sys_menu` WHERE `id` = 152
);

-- 4) 角色菜单授权（沿用当前初始化策略：role_id=4）
INSERT INTO `aigc_sys_role_menu` (`role_id`, `menu_id`)
SELECT 4, 15
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_sys_role_menu` WHERE `role_id` = 4 AND `menu_id` = 15
);

INSERT INTO `aigc_sys_role_menu` (`role_id`, `menu_id`)
SELECT 4, 151
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_sys_role_menu` WHERE `role_id` = 4 AND `menu_id` = 151
);

INSERT INTO `aigc_sys_role_menu` (`role_id`, `menu_id`)
SELECT 4, 152
WHERE NOT EXISTS (
  SELECT 1 FROM `aigc_sys_role_menu` WHERE `role_id` = 4 AND `menu_id` = 152
);

COMMIT;

-- 可选自检
-- SELECT id, parent_id, name, type, path, permission, sort, status FROM aigc_sys_menu WHERE id IN (15,151,152);
-- SELECT role_id, menu_id FROM aigc_sys_role_menu WHERE role_id = 4 AND menu_id IN (15,151,152);
