package vg.skye.e4mc_minecraft

//#if FABRIC==1
import net.fabricmc.api.ModInitializer
//#else
//$$ import net.minecraftforge.fml.common.Mod
//#endif
import org.slf4j.Logger
import org.slf4j.LoggerFactory

//#if FORGE==1
//$$ @Mod("e4mc_minecraft")
//$$ object E4mcClient {
//#else
object E4mcClient : ModInitializer {
//#endif
    const val NAME = "e4mc"
    const val ID = "e4mc_minecraft"
    const val VERSION = "2.0.0"
    @JvmField
    val LOGGER: Logger = LoggerFactory.getLogger("e4mc")

    //#if FABRIC==1
    override fun onInitialize() {
        // nothing needed
    }
    //#endif
}
