package justfatlard.couch_controls;

import justfatlard.couch_controls.input.ControlNames;
import justfatlard.couch_controls.input.Gamepad;
import justfatlard.couch_controls.play.WorldControls;
import justfatlard.couch_controls.ui.HintLink;
import justfatlard.couch_controls.ui.Navigator;
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
	/** The driven pad's names for its controls; null until it is used, and again when it changes. */
	private static ControlNames names;

	public static Gamepad gamepad() {
		return GAMEPAD;
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

		// Hints name the pad's buttons from the moment it is touched, until a key or the mouse
		// is (see HintInputMixin). The names go with the pad, so a new one gets its own.
		if (GAMEPAD.used()) {
			if (names == null) names = new ControlNames(GAMEPAD.handle());
			HintLink.controller(names);
		}

		if (client.gui.screen() == null) {
			Navigator.reset();
			WorldControls.onFrame(GAMEPAD, client, frameSeconds);
		} else {
			// Every frame, not only when the screen opens: mixins keep reading world state
			// that nothing recomputes while a screen is up.
			WorldControls.release();
			Navigator.onFrame(GAMEPAD, client, frameSeconds);
		}
	}
}
