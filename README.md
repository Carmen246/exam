# 在线考试系统

## 项目介绍

在线考试系统是一套基于 Spring Boot 和 Vue 的考试管理平台，支持用户管理、角色管理、部门管理、题库管理、试题管理、试题导入导出、考试管理、在线考试、错题训练等功能。

系统包含管理端和考试端，适用于在线练习、考试组织、题库维护和考试结果管理等场景。

## 技术栈

- 后端：Spring Boot、Shiro、JWT、MyBatis-Plus
- 前端：Vue、Element UI
- 数据库：MySQL
- 构建工具：Maven、npm

## 主要功能

### 系统管理

- 用户管理
- 角色管理
- 部门管理
- 权限控制
- 系统配置

### 考试管理

- 题库管理
- 试题管理
- 试题导入导出
- 考试创建与发布
- 考试权限配置
- 在线考试
- 成绩查看
- 错题训练

### 题型支持

- 单选题
- 多选题
- 判断题

### 组卷方式

- 指定题库组卷
- 按分数、数量组卷
- 题目和选项随机排序

## 环境要求

- JDK 1.8+
- MySQL 5.7+
- Maven 3.x
- Node.js 14.x 或兼容版本

## 快速运行

### 使用运行包

运行包位于 `docs/运行包` 目录。

1. 安装 Java 环境，要求 JDK 版本不低于 1.8。
2. 安装 MySQL 数据库，建议使用 MySQL 5.7。
3. 将 `docs/安装资源/数据库脚本.sql` 导入数据库。
4. 修改 `docs/运行包/application-local.yml`，配置自己的 MySQL 连接信息。
5. Windows 环境执行 `start.bat`，Linux 环境执行 `start.sh`。
6. 启动后访问 `http://localhost:8101`。

默认账号：

- 管理员：admin / admin
- 学员：person / person

### 源码运行

后端工程位于 `exam-api`，前端工程位于 `exam-vue`。

后端启动：

```bash
cd exam-api
mvn spring-boot:run
```

前端启动：

```bash
cd exam-vue
npm install
npm run serve
```

## 项目文档

相关文档位于 `docs` 目录：

- `docs/部署手册.pdf`
- `docs/源码说明.pdf`
- `docs/数据表结构.pdf`
- `docs/安装资源/数据库脚本.sql`
