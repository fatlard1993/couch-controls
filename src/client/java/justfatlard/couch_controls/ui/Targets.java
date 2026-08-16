package justfatlard.couch_controls.ui;

import justfatlard.couch_controls.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.loader.api.FabricLoader;
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

	/** Vanilla slots are 16x16, drawn one pixel inside their 18x18 cell. */
	private static final int SLOT_SIZE = 16;

	private static final boolean PANDORICAL_LOADED = FabricLoader.getInstance().isModLoaded("pandorical");

	/**
	 * Collected fresh every step rather than cached. Screens move under you:
	 * a container repositions on resize, widgets come and go with state, and
	 * Pandorical components are interpolated toward server-sent geometry over
	 * several ticks after any update. A cache would age into wrong answers
	 * silently, and the walk is a few dozen objects.
	 */
	public static List<NavTarget> collect(Screen screen) {
		List<NavTarget> targets = new ArrayList<>();

		if (screen instanceof AbstractContainerScreen<?> container) {
			collectSlots(container, targets);
		}

		collectWidgets(screen, targets);

		// Guarded rather than merged into the walk above: PandoricalTargets
		// names Pandorical's classes, and Pandorical is a soft dependency.
		// The flag has to be tested here, in a class that always loads, so
		// that a client without Pandorical never reaches that one at all.
		if (PANDORICAL_LOADED) {
			PandoricalTargets.collect(screen, targets);
		}

		return targets;
	}

	private static void collectSlots(AbstractContainerScreen<?> container, List<NavTarget> into) {
		int left = ((AbstractContainerScreenAccessor) container).couch_controls$getLeftPos();
		int top = ((AbstractContainerScreenAccessor) container).couch_controls$getTopPos();

		for (Slot slot : container.getMenu().slots) {
			// Empty slots stay in: they are where items get put down, and a
			// navigator that could only reach occupied slots could pick a
			// stack up and never place it.
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
