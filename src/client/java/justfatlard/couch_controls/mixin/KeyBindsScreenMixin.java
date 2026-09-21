package justfatlard.couch_controls.mixin;

import justfatlard.couch_controls.input.PadBinds;
import justfatlard.couch_controls.ui.PadRebind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While the pad column waits for a press, the mouse and keys only end the wait; reset all covers the pad. */
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin {
	@Shadow private Button resetButton;

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void couch_controls$clickStopsListening(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (!PadRebind.listening()) return;
		PadRebind.stop((KeyBindsScreen) (Object) this);
		cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void couch_controls$keyStopsListening(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!PadRebind.listening()) return;
		PadRebind.keyPressed((KeyBindsScreen) (Object) this, event.isEscape());
		cir.setReturnValue(true);
	}

	@Inject(method = "lambda$addFooter$0", at = @At("HEAD"))
	private void couch_controls$resetAllPad(Button button, CallbackInfo ci) {
		PadBinds.resetAll();
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void couch_controls$padCanReset(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!resetButton.active && PadBinds.anyChanged(Minecraft.getInstance().options)) resetButton.active = true;
	}
}
