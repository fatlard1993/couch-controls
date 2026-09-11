package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fallback for a player whose input is not a {@code KeyboardInput}, which
 * {@code KeyboardInputMixin} never reaches. On the ordinary path the vector
 * arriving here is already the pad's.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@ModifyVariable(method = "modifyInput", at = @At("HEAD"), argsOnly = true)
	private Vec2 couch_controls$analogMovement(Vec2 original) {
		Vec2 fromPad = WorldControls.moveVector();
		return fromPad != null ? fromPad : original;
	}
}
