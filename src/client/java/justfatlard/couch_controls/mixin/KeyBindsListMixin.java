package justfatlard.couch_controls.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Room for the pad column (KeyEntryMixin), kept inside the screen when the screen is narrow. */
@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {
	@Unique private static final int PAD_COLUMN = 75;
	@Unique private static final int SCROLLBAR_ROOM = 20;

	@Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)
	private void couch_controls$widenForPad(CallbackInfoReturnable<Integer> cir) {
		int screenWidth = ((KeyBindsList) (Object) this).getWidth();
		cir.setReturnValue(Math.max(cir.getReturnValue(), Math.min(cir.getReturnValue() + PAD_COLUMN, screenWidth - 2 * SCROLLBAR_ROOM)));
	}
}
