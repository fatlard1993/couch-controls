package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.play.WorldControls;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bound pad button makes the vanilla key mapping for its action report pressed; it never reports released. */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	@Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
	private void couch_controls$padHold(CallbackInfoReturnable<Boolean> cir) {
		if (WorldControls.isPadDown((KeyMapping) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
	private void couch_controls$padClick(CallbackInfoReturnable<Boolean> cir) {
		if (WorldControls.consumePadClick((KeyMapping) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
