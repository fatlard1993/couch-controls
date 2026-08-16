package justfatlard.couch_controls;

import justfatlard.couch_controls.input.Gamepad;
import justfatlard.couch_controls.play.WorldControls;
import justfatlard.couch_controls.ui.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The one place the pad is read, once per frame, before anything else looks
 * at it.
 *
 * <p>Per frame rather than per tick because the camera lives here: twenty
 * samples a second is visibly steppy to look around with, and a stick is an
 * analog value that wants reading as often as it is drawn. Everything
 * discrete (button edges, menu steps) is rate limited on its own terms
 * instead of borrowing the tick for it.
 */
public final class Driver {
	private Driver() {}

	/**
	 * A frame long enough that it is almost certainly a stall — a world load,
	 * a shader compile, an alt-tab — rather than a slow frame. Camera and
	 * menu repeat are scaled by frame time, so passing one of these through
	 * would snap the view around or skip a screen's worth of slots.
	 */
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

	/** Called from {@code MinecraftMixin} at the top of every frame. */
	public static void onFrame(Minecraft client) {
		long now = System.nanoTime() / 1_000_000L;
		float frameSeconds = lastFrameMs == 0L ? 0f : Mth.clamp((now - lastFrameMs) / 1000f, 0f, MAX_FRAME_SECONDS);
		lastFrameMs = now;

		GAMEPAD.poll(now);

		if (!GAMEPAD.isConnected()) {
			// Let go of everything exactly once on unplug, rather than every
			// frame after it.
			if (wasConnected) {
				WorldControls.release();
				Navigator.reset();
				wasConnected = false;
			}
			return;
		}
		wasConnected = true;

		if (client.gui.screen() == null) {
			Navigator.reset();
			WorldControls.onFrame(GAMEPAD, client, frameSeconds);
		} else {
			// Released every frame a screen is up, not just on the frame it
			// opened: world state that stops being recomputed does not stop
			// being read, and a held stick would otherwise keep walking the
			// player around behind an open chest.
			WorldControls.release();
			Navigator.onFrame(GAMEPAD, client, frameSeconds);
		}
	}
}
