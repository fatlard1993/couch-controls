package justfatlard.couch_controls.ui;

import justfatlard.pandorical.api.NavigableScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * The Pandorical half, kept in its own class so the rest of the mod never
 * mentions Pandorical's types.
 *
 * <p>Pandorical is a soft dependency: this mod is useful on any client, and
 * most of what it does is pure vanilla. But a Pandorical screen builds its UI
 * from server-sent component definitions that are deliberately not vanilla
 * widgets, so {@code Screen.children()} reports it as empty and a navigator
 * would find nothing to press on it.
 *
 * <p>The isolation is the point, and it is why the "is Pandorical loaded"
 * flag lives in {@link Targets} rather than here: a flag on this class could
 * only be read by touching this class, which is the exact thing being
 * avoided. Guarded from outside, a client without Pandorical never loads,
 * links, or verifies anything below.
 *
 * <p>Deliberately not reflection. The suite already knows what string-keyed
 * reflection costs (see the village web's integration packages, which fail
 * silently when a class is renamed); compiling against the real interface
 * turns that same drift into a build error.
 */
final class PandoricalTargets {
	private PandoricalTargets() {}

	static void collect(Screen screen, List<NavTarget> into) {
		if (!(screen instanceof NavigableScreen navigable)) return;

		for (NavigableScreen.NavRegion region : navigable.navRegions()) {
			into.add(new NavTarget(region.centerX(), region.centerY()));
		}
	}
}
