package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Analog movement: the one thing a key cannot express.
 *
 * <p>{@code applyInput} funnels the whole movement vector through
 * {@code modifyInput} before splitting it into the player's strafe and
 * forward fields, which makes this the single point where a stick's actual
 * deflection can replace the keyboard's cardinal 1.0. Everything after it —
 * sneak scaling, slowdowns, the square-movement correction — still applies,
 * because they all live downstream of here.
 *
 * <p>Returning the original when no stick is held is what lets keyboard and
 * pad coexist: the mod is invisible until the stick actually moves.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@ModifyVariable(method = "modifyInput", at = @At("HEAD"), argsOnly = true)
	private Vec2 couch_controls$analogMovement(Vec2 original) {
		Vec2 fromPad = WorldControls.moveVector();
		return fromPad != null ? fromPad : original;
	}
}
