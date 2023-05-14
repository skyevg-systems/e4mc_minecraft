package vg.skye.e4mc_minecraft

//#if FABRIC==1
import net.fabricmc.api.ModInitializer
//#if MC>=11904
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
//#else
//$$ import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
//#endif
//#else
//$$ import net.minecraftforge.fml.common.Mod
//$$ import net.minecraftforge.eventbus.api.SubscribeEvent
//$$ import net.minecraftforge.event.RegisterCommandsEvent
//#endif
import net.minecraft.server.command.CommandManager.*
//#if MC>=11904
import net.minecraft.text.Text
//#elseif FABRIC==1
//$$ import net.minecraft.text.TranslatableText
//#else
//$$ import net.minecraft.network.chat.TranslatableComponent
//#endif
import org.slf4j.Logger
import org.slf4j.LoggerFactory


//#if FORGE==1
//$$ @Mod("e4mc_minecraft")
//$$ @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
//$$ object E4mcClient {
//#else
object E4mcClient : ModInitializer {
//#endif
    const val NAME = "e4mc"
    const val ID = "e4mc_minecraft"
    const val VERSION = "3.1.0"
    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger("e4mc")
    @JvmField
    var HANDLER: E4mcRelayHandler? = null

    //#if FABRIC==1
    override fun onInitialize() {
        //#if MC>=11904
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
        //#else
        //$$ CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
        //#endif
            dispatcher.register(literal("e4mc")
                .then(
                    literal("stop")
                        .requires { src ->
                            if (src.server.isDedicated) {
                                src.hasPermissionLevel(4)
                            } else {
                                src.server.isHost((src.player ?: return@requires false).gameProfile)
                            }
                        }
                        .executes { context ->
                            if (HANDLER != null) {
                                HANDLER!!.close()
                                HANDLER = null
                                //#if MC>=11904
                                context.source.sendMessage(Text.translatable("text.e4mc_minecraft.closeServer"))
                                //#else
                                //$$ context.source.sendFeedback(TranslatableText("text.e4mc_minecraft.closeServer"), false)
                                //#endif
                            } else {
                                //#if MC>=11904
                                context.source.sendMessage(Text.translatable("text.e4mc_minecraft.serverAlreadyClosed"))
                                //#else
                                //$$ context.source.sendFeedback(TranslatableText("text.e4mc_minecraft.serverAlreadyClosed"), false)
                                //#endif
                            }
                            1
                        }
                ))
        }
    }
    //#else
    //$$ @SubscribeEvent
    //$$ fun onRegisterCommandEvent(event: RegisterCommandsEvent) {
    //$$     val commandDispatcher = event.getDispatcher()
    //$$     commandDispatcher.register(literal("e4mc")
    //$$         .then(
    //$$             literal("stop")
    //$$                 .executes { context ->
    //$$                     if (HANDLER != null) {
    //$$                         HANDLER!!.close()
    //$$                         HANDLER = null
    //$$                         //#if MC>=11904
    //$$                         context.source.sendSuccess(Component.translatable("text.e4mc_minecraft.closeServer"), false)
    //$$                         //#else
    //$$                         //$$ context.source.sendSuccess(TranslatableComponent("text.e4mc_minecraft.closeServer"), false)
    //$$                         //#endif
    //$$                     } else {
    //$$                         //#if MC>=11904
    //$$                         context.source.sendFailure(Component.translatable("text.e4mc_minecraft.serverAlreadyClosed"))
    //$$                         //#else
    //$$                         //$$ context.source.sendFailure(TranslatableComponent("text.e4mc_minecraft.serverAlreadyClosed"))
    //$$                         //#endif
    //$$                     }
    //$$                     1
    //$$                 }
    //$$         ))
    //$$ }
    //#endif
}
