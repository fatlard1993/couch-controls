package justfatlard.couch_controls.ui;

import justfatlard.pandorical.api.NavigableScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** A Pandorical screen's components are not widgets, so {@code children()} is empty; it says where they are instead. */
final class PandoricalTargets {
	private PandoricalTargets() {}

	static void collect(Screen screen, List<NavTarget> into) {
		if (!(screen instanceof NavigableScreen navigable)) return;

		for (NavigableScreen.NavRegion region : navigable.navRegions()) {
			into.add(new NavTarget(region.centerX(), region.centerY()));
		}
	}
}
