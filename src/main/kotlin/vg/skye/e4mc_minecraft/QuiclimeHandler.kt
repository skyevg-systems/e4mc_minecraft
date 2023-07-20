package vg.skye.e4mc_minecraft

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.local.LocalAddress
import io.netty.channel.local.LocalChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ChannelInputShutdownReadComplete
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.ByteToMessageCodec
import io.netty.incubator.codec.quic.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

interface QuiclimeControlMessage

data class RequestDomainAssignmentMessageServerbound(val kind: String = "request_domain_assignment"): QuiclimeControlMessage

data class DomainAssignmentCompleteMessageClientbound(val kind: String = "domain_assignment_complete", val domain: String): QuiclimeControlMessage
data class RequestMessageBroadcastMessageClientbound(val kind: String = "request_message_broadcast", val message: String): QuiclimeControlMessage

class QuiclimeControlMessageCodec : ByteToMessageCodec<QuiclimeControlMessage>() {
    val gson = Gson()

    override fun encode(ctx: ChannelHandlerContext, msg: QuiclimeControlMessage, out: ByteBuf) {
        val json = gson.toJson(msg).toByteArray()
        out.writeByte(json.size)
        out.writeBytes(json)
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        val size = `in`.getByte(`in`.readerIndex()).toInt()
        if (`in`.readableBytes() >= size + 1) {
            `in`.skipBytes(1)
            val buf = ByteArray(size)
            `in`.readBytes(buf)
            val json = gson.fromJson(buf.decodeToString(), JsonObject::class.java)
            out.add(gson.fromJson(json, when (json["kind"].asString) {
                "domain_assignment_complete" -> DomainAssignmentCompleteMessageClientbound::class.java
                "request_message_broadcast" -> RequestMessageBroadcastMessageClientbound::class.java
                else -> throw Exception("Invalid message type")
            }))
        }
    }
}

class QuiclimeToMinecraftHandler(private val toQuiclime: QuicStreamChannel) : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.read()
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        toQuiclime.writeAndFlush(msg).addListener {
            if (it.isSuccess) {
                ctx.channel().read()
            } else {
                ChatHelper.sendError()
                toQuiclime.close()
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        QuiclimeHandler.LOGGER.info("channel inactive(from MC): {} (MC: {})", toQuiclime, ctx.channel())
        if (toQuiclime.isActive) {
            toQuiclime.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        super.exceptionCaught(ctx, cause)
        ChatHelper.sendError()
        this.channelInactive(ctx)
    }
}

class QuiclimeToQuiclimeHandler : ChannelInboundHandlerAdapter() {
    private var toMinecraft: LocalChannel? = null
    override fun channelActive(ctx: ChannelHandlerContext) {
        QuiclimeHandler.LOGGER.info("channel active: {}", ctx.channel())
        val fut = Bootstrap()
            .group(ctx.channel().eventLoop())
            .channel(LocalChannel::class.java)
            .handler(QuiclimeToMinecraftHandler(ctx.channel() as QuicStreamChannel))
            .option(ChannelOption.AUTO_READ, false)
            .connect(LocalAddress("e4mc-relay"))
        toMinecraft = fut.channel() as LocalChannel
        fut.addListener {
            if (it.isSuccess) {
                ctx.channel().read()
            } else {
                ChatHelper.sendError()
                ctx.channel().close()
            }
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (toMinecraft?.isActive == true) {
            toMinecraft!!.writeAndFlush(msg).addListener {
                if (it.isSuccess) {
                    ctx.channel().read();
                } else {
                    ChatHelper.sendError()
                    (it as ChannelFuture).channel().close();
                }
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        QuiclimeHandler.LOGGER.info("channel inactive(from Quiclime): {} (MC: {})", ctx.channel(), toMinecraft)
        if (toMinecraft?.isActive == true) {
            toMinecraft!!.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        if (evt === ChannelInputShutdownReadComplete.INSTANCE) {
            this.channelInactive(ctx)
        }
        super.userEventTriggered(ctx, evt)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        super.exceptionCaught(ctx, cause)
        ChatHelper.sendError()
        this.channelInactive(ctx)
    }
}

data class BrokerResponse(val id: String, val host: String, val port: Int)

enum class QuiclimeHandlerState {
    STARTING,
    STARTED,
    UNHEALTHY,
    STOPPING,
    STOPPED
}

class QuiclimeHandler {
    private val group = NioEventLoopGroup()
    private var datagramChannel: NioDatagramChannel? = null
    private var quicChannel: QuicChannel? = null

    var state: QuiclimeHandlerState = QuiclimeHandlerState.STARTING

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("e4mc-quiclime")
        fun startAsync() {
            thread(start = true) {
                E4mcClient.HANDLER = QuiclimeHandler()
            }
        }
    }

    fun startAsync() {
        thread(start = true) {
            start()
        }
    }

    fun start() {
        try {
            val httpClient = HttpClient.newHttpClient()
            val request = HttpRequest
                .newBuilder(URI("https://broker.e4mc.link/getBestRelay"))
                .header("Accept", "application/json")
                .build()
            LOGGER.info("req: {}", request)
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            LOGGER.info("resp: {}", response)
            if (response.statusCode() != 200) {
                throw Exception()
            }
            val relayInfo = Gson().fromJson(response.body(), BrokerResponse::class.java)
            LOGGER.info("using relay {}", relayInfo.id)
            val context: QuicSslContext = QuicSslContextBuilder
                .forClient()
                .applicationProtocols("quiclime")
                .build()
            val codec = QuicClientCodecBuilder()
                .sslContext(context)
                .sslEngineProvider {
                    context.newEngine(it.alloc(), relayInfo.host, relayInfo.port)
                }
                .initialMaxStreamsBidirectional(512)
                .maxIdleTimeout(10, TimeUnit.SECONDS)
                .initialMaxData(4611686018427387903)
                .initialMaxStreamDataBidirectionalRemote(1250000)
                .initialMaxStreamDataBidirectionalLocal(1250000)
                .initialMaxStreamDataUnidirectional(1250000)
                .build()
            Bootstrap()
                .group(group)
                .channel(NioDatagramChannel::class.java)
                .handler(codec)
                .bind(0)
                .addListener { datagramChannelFuture ->
                    if (!datagramChannelFuture.isSuccess) {
                        ChatHelper.sendError()
                        throw datagramChannelFuture.cause()
                    }
                    datagramChannel = (datagramChannelFuture as ChannelFuture).channel() as NioDatagramChannel
                    QuicChannel.newBootstrap(datagramChannel)
                        .streamHandler(
                            @Sharable
                            object : ChannelInitializer<QuicStreamChannel>() {
                                override fun initChannel(ch: QuicStreamChannel) {
                                    ch.pipeline().addLast(QuiclimeToQuiclimeHandler())
                                }
                            }
                        )
                        .handler(object : ChannelInboundHandlerAdapter() {
                            @Suppress("OVERRIDE_DEPRECATION")
                            override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                                super.exceptionCaught(ctx, cause)
                                ChatHelper.sendError()
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                super.channelInactive(ctx)
                                state = QuiclimeHandlerState.STOPPED
                            }
                        })
                        .streamOption(ChannelOption.AUTO_READ, false)
                        .remoteAddress(InetSocketAddress(InetAddress.getByName(relayInfo.host), relayInfo.port))
                        .connect()
                        .addListener { quicChannelFuture ->
                            if (!quicChannelFuture.isSuccess) {
                                ChatHelper.sendError()
                                throw quicChannelFuture.cause()
                            }
                            quicChannel = quicChannelFuture.get() as QuicChannel
                            quicChannel!!.createStream(QuicStreamType.BIDIRECTIONAL,
                                object : ChannelInitializer<QuicStreamChannel>() {
                                    override fun initChannel(ch: QuicStreamChannel) {
                                        ch.pipeline().addLast(QuiclimeControlMessageCodec(), object : SimpleChannelInboundHandler<QuiclimeControlMessage>() {
                                            override fun channelRead0(ctx: ChannelHandlerContext?, msg: QuiclimeControlMessage?) {
                                                when (msg) {
                                                    is DomainAssignmentCompleteMessageClientbound -> {
                                                        state = QuiclimeHandlerState.STARTED
                                                        ChatHelper.sendDomainAssignment(msg.domain)
                                                    }
                                                    is RequestMessageBroadcastMessageClientbound -> {
                                                        ChatHelper.sendLiteral(msg.message)
                                                    }
                                                }
                                            }
                                        })
                                    }
                                }).addListener {
                                if (!it.isSuccess) {
                                    ChatHelper.sendError()
                                    throw it.cause()
                                }
                                val streamChannel = it.now as QuicStreamChannel
                                LOGGER.info("control channel open: {}", streamChannel)
                                streamChannel
                                    .writeAndFlush(RequestDomainAssignmentMessageServerbound())
                                    .addListener {
                                        LOGGER.info("control channel write complete")
                                    }


                                quicChannel!!.closeFuture().addListener {
                                    datagramChannel?.close()
                                }
                            }
                        }
                }
        } catch (e: Exception) {
            ChatHelper.sendError()
            this.stop()
            throw e
        }
    }

    fun stop() {
        state = QuiclimeHandlerState.STOPPING
        if (quicChannel?.close()?.addListener {
                if (datagramChannel?.close()?.addListener {
                    group.shutdownGracefully().addListener {
                        state = QuiclimeHandlerState.STOPPED
                    }
                } == null) {
                    group.shutdownGracefully().addListener {
                        state = QuiclimeHandlerState.STOPPED
                    }
                }
            } == null) {
            if (datagramChannel?.close()?.addListener {
                    group.shutdownGracefully().addListener {
                        state = QuiclimeHandlerState.STOPPED
                    }
                } == null) {
                group.shutdownGracefully().addListener {
                    state = QuiclimeHandlerState.STOPPED
                }
            }
        }
    }
}