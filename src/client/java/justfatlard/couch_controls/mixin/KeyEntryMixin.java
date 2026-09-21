package justfatlard.couch_controls.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.couch_controls.Driver;
import justfatlard.couch_controls.input.PadBinds;
import justfatlard.couch_controls.ui.PadRebind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** A pad column in each key binds row, between vanilla's key and its reset, which now resets both. */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyEntryMixin {
	@Unique private static final int PAD_BUTTON_WIDTH = 70;
	@Unique private static final int GAP = 5;

	@Shadow @Final private KeyMapping key;
	@Shadow @Final private Component name;
	@Shadow @Final private Button changeButton;
	@Shadow @Final private Button resetButton;

	@Unique private Button couch_controls$padButton;
	@Unique private boolean couch_controls$padCollision;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void couch_controls$addPadButton(CallbackInfo ci) {
		couch_controls$padButton = Button.builder(Component.empty(), button -> {
				if (!(Minecraft.getInstance().gui.screen() instanceof KeyBindsScreen screen)) return;
				screen.selectedKey = null;
				PadRebind.listen(key);
				screen.refreshKeybindLabels();
			})
			.bounds(0, 0, PAD_BUTTON_WIDTH, 20)
			.createNarration(label -> Component.translatable("couch-controls.controls.pad_narration", name, label.get()))
			.build();
		couch_controls$refreshPad();
	}

	@WrapOperation(method = "extractContent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/Button;setPosition(II)V"))
	private void couch_controls$makeRoom(Button button, int x, int y, Operation<Void> original) {
		original.call(button, button == changeButton ? x - PAD_BUTTON_WIDTH - GAP : x, y);
	}

	@Inject(method = "extractContent", at = @At("TAIL"))
	private void couch_controls$extractPadButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
		couch_controls$updatePadMessage();
		couch_controls$padButton.setPosition(resetButton.getX() - GAP - PAD_BUTTON_WIDTH, resetButton.getY());
		couch_controls$padButton.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Inject(method = "children", at = @At("RETURN"), cancellable = true)
	private void couch_controls$padChild(CallbackInfoReturnable<List<? extends GuiEventListener>> cir) {
		cir.setReturnValue(ImmutableList.of(changeButton, couch_controls$padButton, resetButton));
	}

	@Inject(method = "narratables", at = @At("RETURN"), cancellable = true)
	private void couch_controls$padNarratable(CallbackInfoReturnable<List<? extends NarratableEntry>> cir) {
		cir.setReturnValue(ImmutableList.of(changeButton, couch_controls$padButton, resetButton));
	}

	/** Also runs from vanilla's constructor, before the pad button exists. */
	@Inject(method = "refreshEntry", at = @At("TAIL"))
	private void couch_controls$refreshPadEntry(CallbackInfo ci) {
		if (couch_controls$padButton != null) couch_controls$refreshPad();
	}

	/** Vanilla's reset button: the row goes back to its defaults, pad included. */
	@Inject(method = "lambda$new$2", at = @At("HEAD"))
	private static void couch_controls$resetPad(KeyMapping key, KeyBindsList list, Button button, CallbackInfo ci) {
		PadBinds.reset(key);
	}

	@Unique
	private void couch_controls$refreshPad() {
		resetButton.active = !key.isDefault() || !PadBinds.isDefault(key);
		couch_controls$padButton.active = PadBinds.reachable(key);
		couch_controls$padCollision = false;
		couch_controls$padButton.setTooltip(null);
		if (!PadBinds.reachable(key)) {
			couch_controls$padButton.setTooltip(Tooltip.create(Component.translatable("couch-controls.controls.keyboard_only")));
			return;
		}

		int slot = PadBinds.slotOf(key);
		MutableComponent others = Component.empty();
		if (slot != PadBinds.NONE) {
			for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
				if (other == key || PadBinds.slotOf(other) != slot || !PadBinds.reachable(other)) continue;
				if (couch_controls$padCollision) others.append(", ");
				couch_controls$padCollision = true;
				others.append(Component.translatable(other.getName()));
			}
		}
		if (PadRebind.listeningTo(key)) {
			couch_controls$padButton.setTooltip(Tooltip.create(Component.translatable("couch-controls.controls.listening")));
		} else if (couch_controls$padCollision) {
			couch_controls$padButton.setTooltip(Tooltip.create(Component.translatable("controls.keybinds.duplicateKeybinds", others)));
		}
		couch_controls$updatePadMessage();
	}

	/** Every frame, as vanilla's is: the label follows the pad in hand. */
	@Unique
	private void couch_controls$updatePadMessage() {
		if (!PadBinds.reachable(key)) {
			couch_controls$padButton.setMessage(Component.literal("-"));
			return;
		}
		String label = Driver.controlNames().label(PadBinds.slotOf(key));
		Component message = label != null ? Component.literal(label) : Component.translatable("key.keyboard.unknown");
		if (couch_controls$padCollision) {
			message = Component.literal("[ ").append(message.copy().withStyle(ChatFormatting.WHITE)).append(" ]").withStyle(ChatFormatting.YELLOW);
		}
		if (PadRebind.listeningTo(key)) {
			message = Component.literal("> ")
				.append(message.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
				.append(" <")
				.withStyle(ChatFormatting.YELLOW);
		}
		couch_controls$padButton.setMessage(message);
	}
}
