package vg.skye.e4mc_minecraft

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

object CommandsHelper {
    private fun getPlayerFromSource(src: ServerCommandSource): ServerPlayerEntity? {
        return try {
            src.playerOrThrow
        } catch (e: Exception) {
            null
        }
    }

    fun registerCommandWithDispatcher(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("e4mc")
                .requires { src ->
                    if (src.server.isDedicated) {
                        src.hasPermissionLevel(4)
                    } else {
                        src.server.isHost((getPlayerFromSource(src) ?: return@requires false).gameProfile)
                    }
                }
                .then(
                    CommandManager.literal("stop")
                        .executes { context ->
                            if ((E4mcClient.HANDLER != null) && (E4mcClient.HANDLER?.state != QuiclimeHandlerState.STOPPED)) {
                                E4mcClient.HANDLER!!.stop()
                                sendMessageToSource(ChatHelper.createTranslatableMessage("text.e4mc_minecraft.closeServer"), context.source)
                            } else {
                                sendErrorToSource(ChatHelper.createTranslatableMessage("text.e4mc_minecraft.serverAlreadyClosed"), context.source)
                            }
                            1
                        }
                )
                .then(CommandManager.literal("restart")
                    .executes {
                        if ((E4mcClient.HANDLER != null) && (E4mcClient.HANDLER?.state != QuiclimeHandlerState.STARTED)) {
                            E4mcClient.HANDLER?.stop()
                            val handler = QuiclimeHandler()
                            E4mcClient.HANDLER = handler
                            handler.startAsync()
                        }
                        1
                    }
                )
        )
    }

    private fun sendMessageToSource(text: Text, source: ServerCommandSource) {
        source.sendFeedback(text, true)
    }

    private fun sendErrorToSource(text: Text, source: ServerCommandSource) {
        source.sendError(text)
    }
}