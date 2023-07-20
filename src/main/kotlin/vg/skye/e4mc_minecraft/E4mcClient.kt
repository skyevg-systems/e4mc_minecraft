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
    const val VERSION = "4.0.0"
    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger("e4mc")
    @JvmField
    var HANDLER: QuiclimeHandler? = null

    //#if FABRIC==1
    override fun onInitialize() {
        //#if MC>=11904
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
        //#else
        //$$ CommandRegistrationCallback.EVENT.register { dispatcher, _ ->
        //#endif
            CommandsHelper.registerCommandWithDispatcher(dispatcher)
        }
    }
    //#else
    //$$ @SubscribeEvent
    //$$ fun onRegisterCommandEvent(event: RegisterCommandsEvent) {
    //$$     val dispatcher = event.getDispatcher()
    //$$     CommandsHelper.registerCommandWithDispatcher(dispatcher)
    //$$ }
    //#endif
}
