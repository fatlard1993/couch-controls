package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where a pad button becomes a game action.
 *
 * <p>Rather than calling the game's methods directly — swing the arm, use the
 * item, start breaking — a bound button simply makes the vanilla key mapping
 * for that action report itself pressed. Everything downstream then runs
 * untouched: block breaking keeps its progress and its cooldown, bows and
 * food keep their charge-up, sneaking keeps its edge cases. None of that has
 * to be re-derived, and none of it can drift out of sync with vanilla,
 * because it *is* vanilla.
 *
 * <p>Only ever forces a mapping to true. A pad that says nothing leaves the
 * keyboard's answer alone, so both work at once and neither has to win.
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void couch_controls$padHold(CallbackInfoReturnable<Boolean> cir) {
		if (WorldControls.isPadDown((KeyMapping) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * The press-once counterpart. Kept separate from {@code isDown} because
	 * vanilla drains this one: inventory, drop and hand-swap all read clicks
	 * rather than holds, and answering them from the held state would repeat
	 * the action every tick the button stayed down.
	 */
	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void couch_controls$padClick(CallbackInfoReturnable<Boolean> cir) {
		if (WorldControls.consumePadClick((KeyMapping) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
