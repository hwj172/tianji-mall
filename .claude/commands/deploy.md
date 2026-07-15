<!-- .claude/commands/deploy.md -->
# 部署检查清单

请执行以下部署前检查：
1. 运行项目测试：mvn test
2. 代码格式与规范检查：mvn checkstyle:check
3. 确认 application.yml / application.yaml / .env 配置文件示例已更新（新增配置项需同步）
4. 项目打包构建：mvn clean package
5. 前端构建（如需）：npm run build
6. 报告所有检查结果
