package justfatlard.couch_controls;

import justfatlard.couch_controls.input.Gamepad;
import justfatlard.couch_controls.play.WorldControls;
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
				wasConnected = false;
			}
			return;
		}
		wasConnected = true;
		// The next world frame then counts the takeover press as spent.
		if (GAMEPAD.handedOver()) WorldControls.release();

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
