package justfatlard.couch_controls.ui;

import justfatlard.couch_controls.CouchControls;
import justfatlard.couch_controls.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Finds everything on a screen worth landing on. */
public final class Targets {
	private Targets() {}

	private static final int SLOT_SIZE = 16;

	/**
	 * Fresh on every call, never cached: containers move on resize, widgets come and go, and
	 * Pandorical components interpolate toward new geometry over several ticks.
	 */
	public static List<NavTarget> collect(Screen screen) {
		List<NavTarget> targets = new ArrayList<>();

		if (screen instanceof AbstractContainerScreen<?> container) {
			collectSlots(container, targets);
		}

		collectWidgets(screen, targets);

		if (CouchControls.PANDORICAL_LOADED) {
			PandoricalTargets.collect(screen, targets);
		}

		return targets;
	}

	private static void collectSlots(AbstractContainerScreen<?> container, List<NavTarget> into) {
		int left = ((AbstractContainerScreenAccessor) container).couch_controls$getLeftPos();
		int top = ((AbstractContainerScreenAccessor) container).couch_controls$getTopPos();

		for (Slot slot : container.getMenu().slots) {
			// Empty slots stay: they are where a held stack gets put down.
			if (!slot.isActive()) continue;

			into.add(NavTarget.ofBounds(left + slot.x, top + slot.y, SLOT_SIZE, SLOT_SIZE));
		}
	}

	private static void collectWidgets(Screen screen, List<NavTarget> into) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget.visible && widget.isActive()) {
				into.add(NavTarget.ofBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
			}
		}
	}
}
