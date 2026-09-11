package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Writes the pad into both halves of {@code ClientInput}, immediately after
 * {@code tick()} rebuilds them from the keys: {@code keyPresses}, which the
 * server is sent, and {@code moveVector}, which the player walks by and
 * sprint starts from.
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void couchcontrols$mergePad(CallbackInfo ci) {
		ClientInput input = (ClientInput) (Object) this;
		input.keyPresses = WorldControls.mergeKeyPresses(input.keyPresses);
		Vec2 fromPad = WorldControls.moveVector();
		if (fromPad != null) ((ClientInputAccessor) input).couch_controls$setMoveVector(fromPad);
	}
}
