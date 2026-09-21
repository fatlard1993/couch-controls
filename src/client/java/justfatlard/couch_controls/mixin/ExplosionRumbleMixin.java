package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.Rumble;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** At the tail: the network thread's pass throws out of the head to reschedule, so only the client thread's gets here. */
@Mixin(ClientPacketListener.class)
public class ExplosionRumbleMixin {
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void couch_controls$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		Rumble.explosion(packet.center(), packet.radius());
	}
}
