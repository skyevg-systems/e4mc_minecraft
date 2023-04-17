package vg.skye.e4mc_minecraft

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.local.LocalAddress
import io.netty.channel.local.LocalChannel
import io.netty.channel.nio.NioEventLoopGroup
//#if FABRIC==1
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
//#else
//$$ import net.minecraftforge.fml.loading.FMLLoader
//#endif
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
//#if MC>=11900
import net.minecraft.util.Formatting
//#elseif FABRIC==1
//$$ import net.minecraft.text.TranslatableText
//$$ import net.minecraft.text.LiteralText
//$$ import net.minecraft.util.Formatting
//#else
//$$ import net.minecraft.network.chat.TranslatableComponent
//$$ import net.minecraft.network.chat.TextComponent
//$$ import net.minecraft.ChatFormatting
//#endif
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue


data class DomainAssignedMessage(val DomainAssigned: String)
data class ChannelOpenMessage(val ChannelOpen: List<Any>)
data class ChannelClosedMessage(val ChannelClosed: Number)

class E4mcRelayHandler: WebSocketClient(URI("wss://ingress.e4mc.link")) {
    private val gson = Gson()
    private val childChannels = mutableMapOf<Int, LocalChannel>()
    private val messageQueue = mutableMapOf<Int, ArrayBlockingQueue<ByteBuffer>>()
    private val eventLoopGroup = NioEventLoopGroup()

    override fun onOpen(handshakedata: ServerHandshake?) {
        // not much to do here
    }

    override fun onMessage(message: String?) {
        E4mcClient.LOGGER.info("WebSocket Text message: {}", message)
        val json = gson.fromJson(message, JsonObject::class.java)
        when {
            json.has("DomainAssigned") -> handleDomainAssigned(json)
            json.has("ChannelOpen") -> handleChannelOpen(json)
            json.has("ChannelClosed") -> handleChannelClosed(json)
            else -> E4mcClient.LOGGER.warn("Unhandled WebSocket Text message: $message")
        }
    }

    override fun onMessage(bytes: ByteBuffer) {
        val channelId = bytes.get()
        val rest = bytes.slice()
        val channel = childChannels[channelId.toInt()]
        if (channel == null) {
            if (messageQueue[channelId.toInt()] == null) {
                E4mcClient.LOGGER.info("Creating queue for channel: {}", channelId)
                messageQueue[channelId.toInt()] = ArrayBlockingQueue(8)
            }
            messageQueue[channelId.toInt()]!!.add(rest)
        } else {
            val byteBuf = channel.alloc().buffer(rest.remaining())
            byteBuf.writeBytes(rest)
            channel.writeAndFlush(byteBuf).sync()
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        childChannels.forEach { (_, channel) -> channel.close() }
    }

    override fun onError(ex: java.lang.Exception) {
        ex.printStackTrace()
    }

    private fun handleDomainAssigned(json: JsonObject) {
        val msg = gson.fromJson(json, DomainAssignedMessage::class.java)

        //#if FABRIC==1
        val isClient = FabricLoader.getInstance().environmentType.equals(EnvType.CLIENT)
        val isServer = FabricLoader.getInstance().environmentType.equals(EnvType.SERVER)
        //#else
        //$$ val isClient = FMLLoader.getDist().isClient
        //$$ val isServer = FMLLoader.getDist().isDedicatedServer
        //#endif

        if (isServer) {
            E4mcClient.LOGGER.warn("e4mc running on Dedicated Server; This works, but isn't recommended as e4mc is designed for short-lived LAN servers")
        }
        E4mcClient.LOGGER.info("Domain assigned: ${msg.DomainAssigned}")

        if (isClient) {
            alertUser(msg.DomainAssigned)
        }
    }

    private fun alertUser(domain: String) {
        try {
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(
                createMessage(domain)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createMessage(domain: String): Text {
        //#if MC>=11900
        return Text.translatable(
            "text.e4mc_quilt.domainAssigned",
            Text.literal(domain).styled {
                it
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, domain))
                    .withColor(Formatting.GREEN)
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.copy.click")))
            }
        )
        //#elseif FABRIC==1
        //$$ return TranslatableText("text.e4mc_quilt.domainAssigned", LiteralText(domain).styled {
        //$$     return@styled it
        //$$         .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, domain))
        //$$         .withColor(Formatting.GREEN)
        //$$         .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, TranslatableText("chat.copy.click")))
        //$$ })
        //#else
        //$$ return TranslatableComponent("text.e4mc_quilt.domainAssigned", TextComponent(domain).withStyle {
        //$$     return@withStyle it
        //$$         .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, domain))
        //$$         .withColor(ChatFormatting.GREEN)
        //$$         .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, TranslatableComponent("chat.copy.click")))
        //$$ })
        //#endif
    }

    private fun handleChannelOpen(json: JsonObject) {
        val msg = gson.fromJson(json, ChannelOpenMessage::class.java)
        val channelId = msg.ChannelOpen[0] as Number
        val clientInfo = msg.ChannelOpen[1] as String
        E4mcClient.LOGGER.info("Channel opened: channelId=$channelId, clientInfo=$clientInfo")
        val host = clientInfo.substringBeforeLast(':')
        val port = clientInfo.substringAfterLast(':').toInt()
        val addr = InetSocketAddress(host, port)

        val self = this

        Bootstrap()
            .channel(LocalChannel::class.java)
            .handler(object : ChannelInitializer<LocalChannel>() {
                @Throws(java.lang.Exception::class)
                override fun initChannel(ch: LocalChannel) {
                    ch.pipeline().addLast(ChildHandler(self, addr, channelId.toInt()))
                }
            })
            .group(eventLoopGroup)
            .connect(LocalAddress("e4mc-relay"))
    }

    private fun handleChannelClosed(json: JsonObject) {
        val msg = gson.fromJson(json, ChannelClosedMessage::class.java)
        val channelId = msg.ChannelClosed.toInt()
        E4mcClient.LOGGER.info("Closing channel as requested: {}", channelId)
        childChannels.remove(channelId)?.let {
            it.pipeline().get(ChildHandler::class.java).isClosedFromServer = true
            it.close()
            E4mcClient.LOGGER.info("Channel closed: channelId=$channelId")
        }
    }

    class ChildHandler(private val parent: E4mcRelayHandler, private val address: InetSocketAddress, private val channelId: Int): SimpleChannelInboundHandler<ByteBuf>() {
        var isClosedFromServer = false
        override fun channelActive(ctx: ChannelHandlerContext) {
//            ctx.writeAndFlush(address)
            if (parent.messageQueue[channelId] != null) {
                parent.messageQueue[channelId]!!.forEach { buf ->
                    run {
                        E4mcClient.LOGGER.info("Handling queued buffer: {}", buf)
                        val byteBuf = ctx.alloc().buffer(buf.remaining())
                        byteBuf.writeBytes(buf)
                        ctx.writeAndFlush(byteBuf)
                    }
                }
                parent.messageQueue.remove(channelId)
            }
            parent.childChannels[channelId] = ctx.channel() as LocalChannel
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
            val buf = ByteArray(msg.readableBytes() + 1)
            buf[0] = channelId.toByte()
            msg.readBytes(buf, 1, msg.readableBytes())
            parent.send(buf)
        }

        override fun channelInactive(ctx: ChannelHandlerContext) {
            E4mcClient.LOGGER.info("Channel closed: {}", channelId)
            if (!isClosedFromServer) {
                parent.send(parent.gson.toJson(ChannelClosedMessage(channelId)))
            }
            parent.childChannels.remove(channelId)
        }
    }
}
