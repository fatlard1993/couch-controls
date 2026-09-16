package justfatlard.couch_controls.ui;

import java.util.function.Function;
import justfatlard.couch_controls.CouchControls;
import justfatlard.pandorical.client.hint.InputHints;

/**
 * Tells Pandorical which hands the player is using, so hints name pad buttons rather than keys.
 *
 * <p>Only when the installed Pandorical has {@code InputHints}: loaded says nothing about which
 * version is loaded, and calling into an older one throws out of the frame. Checked once.
 */
public final class HintLink {
	private HintLink() {}

	private static final boolean LINKED = CouchControls.PANDORICAL_LOADED && linked();

	private static boolean linked() {
		try {
			InputHints.controller();
			return true;
		} catch (LinkageError older) {
			CouchControls.LOGGER.warn("Pandorical predates InputHints; hints will keep naming keys");
			return false;
		}
	}

	public static void controller(Function<String, String> names) {
		if (LINKED) InputHints.controller(names);
	}

	public static void keyboard() {
		if (LINKED) InputHints.keyboard();
	}
}
