package justfatlard.couch_controls.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.couch_controls.Driver;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mouse button pressed takes the hands off the pad, as a key does. A button rather than
 * movement: the pad moves the pointer too, by warping it, and a hand brushing the mouse is not a
 * hand on it.
 */
@Mixin(MouseHandler.class)
public class MouseHintMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void couchcontrols$mouseInHand(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
		if (action == InputConstants.PRESS) Driver.keysInHand();
	}
}
