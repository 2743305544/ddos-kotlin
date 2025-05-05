# 网络压力测试工具

这是一个使用 Kotlin、Picocli、Coroutines 和 Netty 构建的高性能网络压力测试工具。该工具专为合法的基础设施弹性测试而设计。

## 功能特点

- 使用 Kotlin 协程和 Netty 实现高性能并发请求
- 可自定义连接数和请求数
- 支持不同的 HTTP 方法（GET、POST 等）
- 自定义请求头和请求数据
- 请求速率限制
- 详细的统计数据和日志记录
- 基于持续时间或请求数量的测试

## 系统要求

**重要提示：** 本工具需要 JDK 21 或更高版本才能运行。请确保您已安装 JDK 21 并正确配置了环境变量。

您可以通过以下命令检查您的 Java 版本：

```bash
java -version
```

## 构建项目

```bash
./gradlew build
```

## 运行方式
```
java -jar ddos-kotlin-1.0.jar --args
```
查看帮助信息：

```bash
./gradlew run --args="--help"
```

或者在构建后直接运行：

```bash
java -jar build/libs/ddos-kotlin-1.0.jar --help
```

## 使用示例

基本用法：

```bash
java -jar build/libs/ddos-kotlin-1.0.jar -t http://example.com
```

高级用法：

```bash
java -jar build/libs/ddos-kotlin-1.0.jar \
  -t http://example.com \
  -c 200 \
  -r 500 \
  -m POST \
  -d "param1=value1&param2=value2" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Bearer token" \
  --threads 16 \
  --timeout 3000 \
  --rate-limit 100 \
  --duration 60
```

## 命令行选项

- `-t, --target`: 要测试的目标 URL（必需）
- `-p, --port`: 端口号（默认：HTTP 为 80，HTTPS 为 443）
- `-c, --connections`: 并发连接数（默认：100）
- `-r, --requests`: 每个连接的请求数（默认：100）
- `-m, --method`: 使用的 HTTP 方法（默认：GET）
- `-d, --data`: 与 POST 请求一起发送的数据
- `-H, --header`: 自定义请求头（格式：'名称: 值'）
- `--timeout`: 连接超时时间（毫秒，默认：5000）
- `--threads`: 工作线程数（默认：可用处理器数量 * 2）
- `--rate-limit`: 每秒请求数限制（默认：无限制）
- `--duration`: 测试持续时间（秒，默认：运行直到完成）
- `-h, --help`: 显示帮助信息
- `-V, --version`: 显示版本信息

## 法律免责声明

本工具仅供合法的安全测试使用，仅限测试您自己的基础设施。未经授权对您不拥有或没有明确许可测试的系统进行测试是非法和不道德的。在进行任何安全测试之前，请务必获得适当的授权。
