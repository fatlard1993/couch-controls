package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.Driver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The per-frame heartbeat.
 *
 * <p>{@code runTick} rather than a Fabric client-tick callback because the
 * camera reads from here, and a game tick fires twenty times a second — fine
 * for discrete presses, visibly stepped for looking around. At the head of
 * the frame so the pad is already sampled by the time any mixin further down
 * asks a key mapping whether it is pressed.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void couch_controls$pollGamepad(boolean renderLevel, CallbackInfo ci) {
		Driver.onFrame((Minecraft) (Object) this);
	}
}
