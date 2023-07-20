package vg.skye.e4mc_minecraft.mixins;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ServerChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalServerChannel;
import net.minecraft.server.ServerNetworkIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vg.skye.e4mc_minecraft.E4mcClient;
import vg.skye.e4mc_minecraft.QuiclimeHandler;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;

@Mixin(ServerNetworkIo.class)
public abstract class ServerNetworkIoMixin {
	private static final ThreadLocal<Boolean> initializingE4mc = ThreadLocal.withInitial(() -> false);

	@Shadow
	public abstract void bind(@Nullable InetAddress address, int port) throws IOException;

	@Inject(method = "bind", at = @At("HEAD"))
	private void bind(InetAddress address, int port, CallbackInfo ci) {
		if (!initializingE4mc.get()) {
			initializingE4mc.set(true);
			try {
				bind(address, port);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				initializingE4mc.set(false);
			}
		} else {
			QuiclimeHandler handler = new QuiclimeHandler();
			E4mcClient.HANDLER = handler;
			handler.startAsync();
		}
	}

	@ModifyArg(method = "bind", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/ServerBootstrap;channel(Ljava/lang/Class;)Lio/netty/bootstrap/AbstractBootstrap;", remap = false))
	private Class<? extends ServerChannel> redirectChannel(Class<? extends ServerChannel> aClass) {
		return initializingE4mc.get() ? LocalServerChannel.class : aClass;
	}

	@Redirect(method = "bind", at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/ServerBootstrap;localAddress(Ljava/net/InetAddress;I)Lio/netty/bootstrap/AbstractBootstrap;", remap = false))
	private AbstractBootstrap<ServerBootstrap, ServerChannel> redirectAddress(ServerBootstrap instance, InetAddress address, int port) {
		return initializingE4mc.get() ? instance.localAddress(new LocalAddress("e4mc-relay")) : instance.localAddress(address, port);
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void stop(CallbackInfo ci) {
		if (E4mcClient.HANDLER != null) {
			E4mcClient.HANDLER.stop();
		}
	}


}
