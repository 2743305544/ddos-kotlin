package com.shiyi

/**
 *
 * @author Shi Yi
 * @date 2025/5/5
 * @Description Network stress testing tool for infrastructure resilience testing
 */


import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import kotlinx.coroutines.*
import picocli.CommandLine
import picocli.CommandLine.*
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

@Command(
    name = "netstresstool",
    mixinStandardHelpOptions = true,
    version = ["Network Stress Tool 1.0"],
    description = ["A high-performance network stress testing tool for infrastructure resilience testing"]
)
class NetworkStressTool : Callable<Int> {
    private val logger = LoggerFactory.getLogger(NetworkStressTool::class.java)

    /**
     * 目标URL
     * 指定要测试的目标网站地址，例如 http://example.com
     * 这是必需的参数
     */
    @Option(
        names = ["-t", "--target"],
        description = ["Target URL to test (e.g., http://example.com)"],
        required = true
    )
    private lateinit var targetUrl: String

    /**
     * 端口号
     * 指定连接的端口号，如果不指定，则使用默认端口（HTTP为80，HTTPS为443）
     */
    @Option(names = ["-p", "--port"], description = ["Port number (default: 80 for HTTP, 443 for HTTPS)"])
    private var port: Int = -1

    /**
     * 并发连接数
     * 指定同时建立的连接数量，默认为100
     */
    @Option(names = ["-c", "--connections"], description = ["Number of concurrent connections (default: 100)"])
    private var connections: Int = 100

    /**
     * 每个连接的请求数
     * 指定每个连接发送的请求数量，默认为100
     */
    @Option(names = ["-r", "--requests"], description = ["Number of requests per connection (default: 100)"])
    private var requestsPerConnection: Int = 100

    /**
     * HTTP方法
     * 指定使用的HTTP方法，如GET、POST等，默认为GET
     */
    @Option(names = ["-m", "--method"], description = ["HTTP method to use (GET, POST, etc., default: GET)"])
    private var method: String = "GET"

    /**
     * POST数据
     * 当使用POST方法时，指定要发送的数据内容
     */
    @Option(names = ["-d", "--data"], description = ["Data to send with POST requests"])
    private var postData: String = ""

    /**
     * 自定义请求头
     * 指定要添加的自定义HTTP请求头，格式为"名称: 值"
     */
    @Option(names = ["-H", "--header"], description = ["Custom headers (format: 'Name: Value')"])
    private var headers: List<String> = mutableListOf()

    /**
     * 连接超时时间
     * 指定连接超时的毫秒数，默认为5000毫秒（5秒）
     */
    @Option(names = ["--timeout"], description = ["Connection timeout in milliseconds (default: 5000)"])
    private var timeout: Int = 5000

    /**
     * 工作线程数
     * 指定用于处理请求的工作线程数量，默认为CPU核心数的2倍
     */
    @Option(names = ["--threads"], description = ["Number of worker threads (default: available processors * 2)"])
    private var threads: Int = Runtime.getRuntime().availableProcessors() * 2

    /**
     * 请求速率限制
     * 指定每秒最大请求数，默认不限制
     */
    @Option(names = ["--rate-limit"], description = ["Rate limit in requests per second (default: no limit)"])
    private var rateLimit: Int = -1

    /**
     * 测试持续时间
     * 指定测试运行的秒数，默认一直运行到完成所有请求
     */
    @Option(names = ["--duration"], description = ["Test duration in seconds (default: run until completion)"])
    private var duration: Int = -1

    private val successCounter = AtomicLong(0)
    private val failureCounter = AtomicLong(0)
    private val bytesSent = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)

    override fun call(): Int = runBlocking {
        try {
            val uri = URI(targetUrl)
            val scheme = uri.scheme ?: "http"
            val host = uri.host
            val actualPort = if (port != -1) port else if (scheme == "https") 443 else 80
            val path = if (uri.path.isNullOrEmpty()) "/" else uri.path + (uri.query?.let { "?$it" } ?: "")

            logger.info("Starting network stress test against $targetUrl")
            logger.info("Configuration: $connections connections, $requestsPerConnection requests per connection")
            logger.info("Using $threads worker threads")

            val startTime = System.currentTimeMillis()
            val endTime = if (duration > 0) startTime + duration * 1000 else Long.MAX_VALUE

            val workerGroup = NioEventLoopGroup(threads)

            try {
                val jobs = (1..connections).map {
                    async(Dispatchers.IO) {
                        val bootstrap = Bootstrap()
                            .group(workerGroup)
                            .channel(NioSocketChannel::class.java)
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                            .option(ChannelOption.SO_KEEPALIVE, true)
                            .option(ChannelOption.SO_REUSEADDR, true) // 允许重用本地地址和端口
                            .handler(object : ChannelInitializer<SocketChannel>() {
                                override fun initChannel(ch: SocketChannel) {
                                    ch.pipeline().addLast(
                                        HttpClientCodec(),
                                        HttpContentDecompressor(),
                                        object : SimpleChannelInboundHandler<HttpObject>() {
                                            override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
                                                when (msg) {
                                                    is HttpResponse -> {
                                                        if (msg.status().code() in 200..299) {
                                                            successCounter.incrementAndGet()
                                                        } else {
                                                            failureCounter.incrementAndGet()
                                                        }
                                                    }

                                                    is HttpContent -> {
                                                        bytesReceived.addAndGet(msg.content().readableBytes().toLong())
                                                        if (msg is LastHttpContent) {
                                                            ctx.close()
                                                        }
                                                    }
                                                }
                                            }

                                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                                failureCounter.incrementAndGet()
                                                ctx.close()
                                            }
                                        }
                                    )
                                }
                            })

                        val requestsToSend = if (duration > 0) Int.MAX_VALUE else requestsPerConnection
                        var requestsSent = 0

                        while (requestsSent < requestsToSend && System.currentTimeMillis() < endTime) {
                            try {
                                val future = bootstrap.connect(InetSocketAddress(host, actualPort)).sync()
                                if (future.isSuccess) {
                                    val channel = future.channel()

                                    val request = when (method.uppercase()) {
                                        "GET" -> DefaultFullHttpRequest(
                                            HttpVersion.HTTP_1_1,
                                            HttpMethod.GET,
                                            path
                                        )

                                        "POST" -> {
                                            val content = Unpooled.copiedBuffer(postData, CharsetUtil.UTF_8)
                                            val request = DefaultFullHttpRequest(
                                                HttpVersion.HTTP_1_1,
                                                HttpMethod.POST,
                                                path,
                                                content
                                            )
                                            request.headers()
                                                .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
                                            request.headers()
                                                .set(HttpHeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                                            request
                                        }

                                        else -> DefaultFullHttpRequest(
                                            HttpVersion.HTTP_1_1,
                                            HttpMethod.valueOf(method),
                                            path
                                        )
                                    }

                                    // Set common headers
                                    request.headers().set(HttpHeaderNames.HOST, host)
                                    request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                                    request.headers().set(HttpHeaderNames.USER_AGENT, "NetStressTool/1.0")

                                    // Set custom headers
                                    headers.forEach { header ->
                                        val parts = header.split(":", limit = 2)
                                        if (parts.size == 2) {
                                            request.headers().set(parts[0].trim(), parts[1].trim())
                                        }
                                    }

                                    bytesSent.addAndGet(request.content().readableBytes().toLong())
                                    channel.writeAndFlush(request)

                                    requestsSent++

                                    // Implement rate limiting if specified
                                    if (rateLimit > 0) {
                                        val sleepTime = 1000 / rateLimit
                                        delay(sleepTime.toLong())
                                    }
                                } else {
                                    failureCounter.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                failureCounter.incrementAndGet()
                                // 提供更详细的错误信息，包括异常类型
                                logger.error("Connection error (${e.javaClass.simpleName}): ${e.message}")
                                
                                // 如果是地址已被使用的错误，提供更具体的建议
                                if (e.message?.contains("Address already in use") == true) {
                                    logger.warn("Try reducing the number of connections or adding delay between requests")
                                }
                            }
                        }
                    }
                }

                // Wait for all jobs to complete
                jobs.awaitAll()

            } finally {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS)
            }

            val testDuration = (System.currentTimeMillis() - startTime) / 1000.0
            val totalRequests = successCounter.get() + failureCounter.get()
            val requestsPerSecond = totalRequests / testDuration

            logger.info("Test completed in $testDuration seconds")
            logger.info("Total requests: $totalRequests")
            logger.info("Successful requests: ${successCounter.get()}")
            logger.info("Failed requests: ${failureCounter.get()}")
            logger.info("Requests per second: $requestsPerSecond")
            logger.info("Data sent: ${bytesSent.get() / 1024} KB")
            logger.info("Data received: ${bytesReceived.get() / 1024} KB")

            return@runBlocking 0
        } catch (e: Exception) {
            logger.error("Error during test execution: ${e.message}", e)
            return@runBlocking 1
        }
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(NetworkStressTool()).execute(*args)
    exitProcess(exitCode)
}
