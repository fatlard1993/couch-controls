package justfatlard.couch_controls;

import justfatlard.couch_controls.input.ControlNames;
import justfatlard.couch_controls.input.Gamepad;
import justfatlard.couch_controls.input.PadBinds;
import justfatlard.couch_controls.play.WorldControls;
import justfatlard.couch_controls.ui.HintLink;
import justfatlard.couch_controls.ui.Navigator;
import justfatlard.couch_controls.ui.PadRebind;
import justfatlard.couch_controls.ui.ScreenKeyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Reads the pad once per frame and hands it to the world or the open screen. */
public final class Driver {
	private Driver() {}

	/** Longer frames are stalls (a world load, an alt-tab); passed through, they would snap the camera and skip menu steps. */
	private static final float MAX_FRAME_SECONDS = 0.1f;

	private static final Gamepad GAMEPAD = new Gamepad();

	private static long lastFrameMs;
	private static boolean wasConnected;
	/** Whether the pad was touched more recently than a key or a mouse button. */
	private static boolean padInHand;
	/** The driven pad's names for its controls; null until asked for, and again when the pad or a binding changes. */
	private static ControlNames names;
	private static ControlNames noPadNames;
	/** A new names object is what makes Pandorical re-read its hints, so a moved binding needs one. */
	private static int namedRevision;

	public static Gamepad gamepad() {
		return GAMEPAD;
	}

	public static boolean padInHand() {
		return padInHand && GAMEPAD.isConnected();
	}

	/** A key or a mouse button went down: the hands are off the pad. */
	public static void keysInHand() {
		padInHand = false;
		HintLink.keyboard();
		ScreenKeyboard.close();
	}

	/** The driven pad's names, or an Xbox pad's while none is connected. */
	public static ControlNames controlNames() {
		if (!GAMEPAD.isConnected()) {
			if (noPadNames == null) noPadNames = new ControlNames(0L);
			return noPadNames;
		}
		if (names == null) names = new ControlNames(GAMEPAD.handle());
		return names;
	}

	public static void init() {
		GAMEPAD.init();
	}

	public static void shutdown() {
		GAMEPAD.close();
	}

	public static void onFrame(Minecraft client) {
		long now = System.nanoTime() / 1_000_000L;
		float frameSeconds = lastFrameMs == 0L ? 0f : Mth.clamp((now - lastFrameMs) / 1000f, 0f, MAX_FRAME_SECONDS);
		lastFrameMs = now;

		GAMEPAD.poll(now);

		if (!GAMEPAD.isConnected()) {
			if (wasConnected) {
				WorldControls.release();
				Navigator.reset();
				HintLink.keyboard();
				names = null;
				wasConnected = false;
			}
			return;
		}
		wasConnected = true;
		// The next world frame then counts the takeover press as spent.
		if (GAMEPAD.handedOver()) {
			WorldControls.release();
			names = null;
		}

		if (namedRevision != PadBinds.revision()) {
			namedRevision = PadBinds.revision();
			names = null;
		}
		// Hints name the pad's buttons from the moment it is touched, until a key or the mouse
		// is (see HintInputMixin). The names go with the pad, so a new one gets its own.
		if (GAMEPAD.used()) {
			padInHand = true;
			HintLink.controller(controlNames());
		}

		if (client.gui.screen() == null) {
			Navigator.reset();
			WorldControls.onFrame(GAMEPAD, client, frameSeconds);
		} else {
			// Every frame, not only when the screen opens: mixins keep reading world state
			// that nothing recomputes while a screen is up.
			WorldControls.release();
			if (!PadRebind.onFrame(GAMEPAD, client) && !ScreenKeyboard.onFrame(GAMEPAD, client, frameSeconds)) {
				Navigator.onFrame(GAMEPAD, client, frameSeconds);
			}
		}
	}
}
