# DJI Stock Checker

一个用于监控DJI商城商品库存状态的自动化工具。当商品有货时，会通过企业微信机器人发送通知。

## 功能特性

- 自动监控DJI商城指定商品的库存状态
- 支持自定义检查间隔时间（默认3-6分钟随机间隔）
- 通过企业微信机器人发送库存通知
- 支持自定义User-Agent和请求头
- 内置简单的反爬虫策略（随机延迟）

## 使用方法

### 前置条件

- JDK 8 或更高版本
- Maven 3.x

### 配置说明

1. 在`src/main/resources/config.properties`中配置以下参数：
   ```properties
   # 监控商品URL
   product.url=你要监控的商品URL
   
   # 企业微信机器人Webhook地址
   webhook.url=你的企业微信机器人webhook地址
   
   # 检查间隔时间（分钟）
   check.interval=3
   ```

2. 编译并运行：
   ```bash
   mvn clean package
   java -jar target/dji-stock-checker-1.0-jar-with-dependencies.jar
   ```

## 注意事项

- 本工具仅用于个人学习研究使用
- 请遵守DJI商城的使用条款
- 建议合理设置检查间隔时间，避免频繁请求
- 在生产环境中建议配置proper的SSL证书验证

## 贡献指南

欢迎提交Issue和Pull Request来帮助改进这个项目。

## 开源许可

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。