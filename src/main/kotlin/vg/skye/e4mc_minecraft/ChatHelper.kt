package vg.skye.e4mc_minecraft

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

object ChatHelper {
    //#if FABRIC==1
    val isClient = FabricLoader.getInstance().environmentType.equals(EnvType.CLIENT)
    val isServer = FabricLoader.getInstance().environmentType.equals(EnvType.SERVER)
    //#else
    //$$ val isClient = FMLLoader.getDist().isClient
    //$$ val isServer = FMLLoader.getDist().isDedicatedServer
    //#endif

    fun sendLiteral(msg: String) {
        alertUser(createLiteralMessage("[e4mc] $msg"))
    }

    fun sendError() {
        if (E4mcClient.HANDLER?.state == QuiclimeHandlerState.STARTED) {
            E4mcClient.HANDLER?.state = QuiclimeHandlerState.UNHEALTHY
        }
        alertUser(createTranslatableMessage("text.e4mc_minecraft.error"))
    }

    fun sendDomainAssignment(domain: String) {
        if (isServer) {
            E4mcClient.LOGGER.warn("e4mc running on Dedicated Server; This works, but isn't recommended as e4mc is designed for short-lived LAN servers")
        }

        E4mcClient.LOGGER.info("Domain assigned: $domain")
        alertUser(createDomainAssignedMessage(domain))
    }

    private fun alertUser(message: Text) {
        if (isClient) {
            MinecraftClient.getInstance().inGameHud.chatHud.addMessage(
                message
            )
        }
    }

    private fun createLiteralMessage(message: String): Text {
        //#if MC>=11900
        return Text.literal(message)
        //#elseif FABRIC==1
        //$$ return LiteralText(message)
        //#else
        //$$ return TextComponent(message)
        //#endif
    }

    fun createTranslatableMessage(key: String, vararg objects: Any?): Text {
        //#if MC>=11900
        return Text.translatable(key, *objects)
        //#elseif FABRIC==1
        //$$ return TranslatableText(key, *objects)
        //#else
        //$$ return TranslatableComponent(key, *objects)
        //#endif
    }

    private fun createDomainAssignedMessage(domain: String): Text {
        //#if MC>=11900
        return Text.translatable(
            "text.e4mc_minecraft.domainAssigned",
            Text.literal(domain).styled {
                it
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, domain))
                    .withColor(Formatting.GREEN)
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.copy.click")))
            }
        ).append(
            Text.translatable("text.e4mc_minecraft.clickToStop").styled {
                it
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4mc stop"))
                    .withColor(Formatting.GRAY)
            }
        )
        //#elseif FABRIC==1
        //$$ return TranslatableText("text.e4mc_minecraft.domainAssigned", LiteralText(domain).styled {
        //$$     return@styled it
        //$$         .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, domain))
        //$$         .withColor(Formatting.GREEN)
        //$$         .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, TranslatableText("chat.copy.click")))
        //$$ }).append(
        //$$     TranslatableText("text.e4mc_minecraft.clickToStop").styled {
        //$$         return@styled it
        //$$             .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4mc stop"))
        //$$             .withColor(Formatting.GRAY)
        //$$     }
        //$$ )
        //#else
        //$$ return TranslatableComponent("text.e4mc_minecraft.domainAssigned", TextComponent(domain).withStyle {
        //$$     return@withStyle it
        //$$         .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, domain))
        //$$         .withColor(ChatFormatting.GREEN)
        //$$         .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, TranslatableComponent("chat.copy.click")))
        //$$ }).append(
        //$$     TranslatableComponent("text.e4mc_minecraft.clickToStop").withStyle {
        //$$         return@withStyle it
        //$$             .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/e4mc stop"))
        //$$             .withColor(ChatFormatting.GRAY)
        //$$     }
        //$$ )
        //#endif
    }
}