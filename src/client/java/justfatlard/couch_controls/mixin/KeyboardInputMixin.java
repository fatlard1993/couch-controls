package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Folds the pad into the boolean input record, at the only moment where the
 * write survives.
 *
 * <p>{@code keyPresses} is rebuilt from the keyboard on every game tick, so
 * anything written into it earlier in the frame is gone before a consumer
 * reads it. TAIL puts this immediately after vanilla recomputes it.
 *
 * <p>Separate from the analog vector in {@code LocalPlayerMixin} because the
 * two feed different things: that one is what the client walks by, this one
 * is what the server is told and what the tutorial watches. Movement looks
 * fine with only the first, which is exactly what makes losing the second
 * hard to notice.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void couchcontrols$mergePad(CallbackInfo ci) {
		ClientInput input = (ClientInput) (Object) this;
		input.keyPresses = WorldControls.mergeKeyPresses(input.keyPresses);
	}
}
