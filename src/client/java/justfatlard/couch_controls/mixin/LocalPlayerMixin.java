package justfatlard.couch_controls.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fallback for a player whose input is not a {@code KeyboardInput}, which
 * {@code KeyboardInputMixin} never reaches. On the ordinary path the vector
 * arriving here is already the pad's.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Shadow
	private boolean isSprintingPossible(boolean allowedInShallowWater) {
		throw new AssertionError();
	}

	@ModifyVariable(method = "modifyInput", at = @At("HEAD"), argsOnly = true)
	private Vec2 couch_controls$analogMovement(Vec2 original) {
		Vec2 fromPad = WorldControls.moveVector();
		return fromPad != null ? fromPad : original;
	}

	/**
	 * A sprint the pad holds is not ended by running into a block. The held sprint starts again
	 * the tick the player is clear, so every step of a hill took the sprint's wider view away and
	 * gave it back, and the screen pulsed all the way up.
	 */
	@ModifyExpressionValue(method = "shouldStopRunSprinting",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z"))
	private boolean couch_controls$keepSprintAgainstBlocks(boolean collided) {
		return collided && !WorldControls.holdingSprint();
	}

	/**
	 * Nor by the eyes breaking the surface while afloat. Vanilla starts a sprint the moment the
	 * eyes dip under and stops it the moment they come back up, until the swim itself begins; a
	 * held sprint started again on every bob, and the view pulsed wider and back. Kept through
	 * the surface, the next dip is a dive. Wading on the bottom still stops it, as it does
	 * vanilla's.
	 */
	@ModifyExpressionValue(method = "shouldStopRunSprinting",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSprintingPossible(Z)Z"))
	private boolean couch_controls$keepSprintAfloat(boolean possible) {
		if (possible || !WorldControls.holdingSprint()) return possible;
		LocalPlayer self = (LocalPlayer) (Object) this;
		return self.isInWater() && !self.onGround() && isSprintingPossible(true);
	}
}
