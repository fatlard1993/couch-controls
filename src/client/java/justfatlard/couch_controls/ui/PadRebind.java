package justfatlard.couch_controls.ui;

import justfatlard.couch_controls.input.Binds;
import justfatlard.couch_controls.input.Gamepad;
import justfatlard.couch_controls.input.PadBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/** The key binds screen waiting for a press: a pad control for the pad column, or vanilla's own wait for a key. */
public final class PadRebind {
	private PadRebind() {}

	private static KeyMapping listening;

	public static void listen(KeyMapping mapping) {
		listening = mapping;
	}

	public static boolean listeningTo(KeyMapping mapping) {
		return listening == mapping;
	}

	public static boolean listening() {
		return listening != null;
	}

	/** Escape clears the binding, as it does a key; any other key only stops the wait. */
	public static void keyPressed(KeyBindsScreen screen, boolean escape) {
		if (escape) PadBinds.set(listening, PadBinds.NONE);
		stop(screen);
	}

	public static void stop(KeyBindsScreen screen) {
		listening = null;
		screen.refreshKeybindLabels();
	}

	/**
	 * True when the frame's pad input belongs here and not to the navigator. Start clears the
	 * binding, as Escape does a key.
	 *
	 * <p>Vanilla's wait takes the pad too. Left to the navigator, the next confirm would bind the
	 * left mouse button to whatever was waiting, and close would leave the screen; a pad cannot
	 * give a key, so a press there only ends the wait.
	 */
	public static boolean onFrame(Gamepad pad, Minecraft client) {
		if (!(client.gui.screen() instanceof KeyBindsScreen screen)) {
			listening = null;
			return false;
		}
		if (listening == null && screen.selectedKey == null) return false;

		int slot = pad.firstPressed();
		if (slot == PadBinds.NONE) return true;

		if (listening != null) PadBinds.set(listening, slot == Binds.PAUSE ? PadBinds.NONE : slot);
		screen.selectedKey = null;
		stop(screen);
		return true;
	}
}
