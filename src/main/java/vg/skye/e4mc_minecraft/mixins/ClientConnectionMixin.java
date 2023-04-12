package vg.skye.e4mc_minecraft.mixins;

import io.netty.channel.Channel;
import io.netty.channel.local.LocalAddress;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
	@Shadow
	private Channel channel;

	@Inject(method = "isLocal", at = @At("RETURN"), cancellable = true)
	private void isLocalInject(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			 if (this.channel.localAddress().equals(new LocalAddress("e4mc-relay"))) {
				 cir.setReturnValue(false);
			 }
		}
	}
}
