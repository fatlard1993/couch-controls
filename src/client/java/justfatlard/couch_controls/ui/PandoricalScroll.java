package justfatlard.couch_controls.ui;

import justfatlard.couch_controls.CouchControls;
import justfatlard.pandorical.client.screen.NavigationScroll;

/** A shoulder scroll marked as navigation, so Pandorical's wheel-over-a-slot item move stands aside. */
final class PandoricalScroll {
	private PandoricalScroll() {}

	/**
	 * Whether the installed Pandorical has {@code NavigationScroll}. Loaded says nothing about
	 * which version is loaded, and calling into an older one throws out of the frame.
	 */
	static boolean linked() {
		try {
			NavigationScroll.isActive();
			return true;
		} catch (LinkageError older) {
			CouchControls.LOGGER.warn("Pandorical predates NavigationScroll; shoulder scroll over a slot may move its item");
			return false;
		}
	}

	static void around(Runnable scroll) {
		NavigationScroll.around(scroll);
	}
}
