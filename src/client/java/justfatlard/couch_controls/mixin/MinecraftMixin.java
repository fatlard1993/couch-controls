package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.Driver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per frame, not per client tick: the camera is driven from here, and twenty samples a second
 * is visibly steppy. At the head, so the pad is sampled before any key mapping is asked.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void couch_controls$pollGamepad(boolean renderLevel, CallbackInfo ci) {
		Driver.onFrame((Minecraft) (Object) this);
	}
}
