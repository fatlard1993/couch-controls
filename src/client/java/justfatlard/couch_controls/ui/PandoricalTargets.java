package justfatlard.couch_controls.ui;

import justfatlard.pandorical.api.NavigableScreen;
import justfatlard.pandorical.client.inventory.ClientInventoryButtons;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.List;

/** A Pandorical screen's components are not widgets, so {@code children()} is empty; it says where they are instead. */
final class PandoricalTargets {
	private PandoricalTargets() {}

	static void collect(Screen screen, List<NavTarget> into) {
		// The buttons mods put on the player's own inventory are drawn over it, not added to it.
		if (screen instanceof InventoryScreen inventory) {
			var panel = (justfatlard.couch_controls.mixin.AbstractContainerScreenAccessor) inventory;
			for (var button : ClientInventoryButtons.all()) {
				into.add(NavTarget.ofBounds(panel.couch_controls$getLeftPos() + button.screenX(),
					panel.couch_controls$getTopPos() + button.screenY(), button.size(), button.size()));
			}
		}

		if (!(screen instanceof NavigableScreen navigable)) return;

		for (NavigableScreen.NavRegion region : navigable.navRegions()) {
			into.add(new NavTarget(region.centerX(), region.centerY()));
		}
	}
}
