CREATE TABLE `la_api_resources` (
  `RESOURCE_ID` varchar(32) NOT NULL COMMENT '资源编号',
  `PERMISSION_ID` varchar(19) NOT NULL COMMENT '接口权限ID',
  PRIMARY KEY (`RESOURCE_ID`,`PERMISSION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='接口资源关联表'


业务系统A        业务系统B
    │                │
    │ (client)       │
    └───────推送权限定义──────┐
                              │
                     permission-server
                              │
                  存储接口权限元数据
                              │
                        绑定菜单 / 角色
                              │
                          运行时鉴权


接口权限