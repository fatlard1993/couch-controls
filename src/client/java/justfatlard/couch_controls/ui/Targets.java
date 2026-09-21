package justfatlard.couch_controls.ui;

import justfatlard.couch_controls.CouchControls;
import justfatlard.couch_controls.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
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

		if (screen instanceof ChatScreen) {
			ChatLinks.collect(Minecraft.getInstance(), targets);
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
				if (child instanceof AbstractSelectionList<?> list && collectRows(list, into)) continue;
				into.add(NavTarget.ofBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
			}
		}
	}

	/**
	 * The buttons in a list's rows, such as the key binds screen's, in place of the list itself;
	 * false when its rows hold none, and the list stands as one target. Only whole rows count:
	 * a row's widgets are placed when it is drawn, so one scrolled out of view still reports
	 * where it last was. Sliders are left to the free pointer, since a click lands the value
	 * wherever the pointer is.
	 */
	private static boolean collectRows(AbstractSelectionList<?> list, List<NavTarget> into) {
		boolean found = false;
		// Entry is protected; LayoutElement is how a row's place is read from outside.
		for (Object row : list.children()) {
			if (!(row instanceof ContainerEventHandler container) || !(row instanceof LayoutElement place)) continue;
			if (place.getY() < list.getY() || place.getY() + place.getHeight() > list.getBottom()) continue;

			for (GuiEventListener child : container.children()) {
				if (child instanceof AbstractWidget widget && widget.visible && widget.isActive()
						&& !(widget instanceof AbstractSliderButton)) {
					into.add(NavTarget.ofBounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
					found = true;
				}
			}
		}
		return found;
	}
}
