package justfatlard.couch_controls.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.couch_controls.Driver;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A key pressed on the keyboard takes the hands off the pad: hints name keys again and the pad
 * stops rumbling. Only a real press counts: the pad drives the game through key mappings and
 * screen calls, neither of which passes through here. The mouse's half is MouseHintMixin.
 */
@Mixin(KeyboardHandler.class)
public class HintInputMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void couchcontrols$keysInHand(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == InputConstants.PRESS) Driver.keysInHand();
	}
}
