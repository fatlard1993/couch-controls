package justfatlard.couch_controls.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import justfatlard.couch_controls.input.Binds;
import justfatlard.couch_controls.input.Gamepad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.SDLMouse;

import java.util.List;

/**
 * Menu navigation: the half of a controller that a keyboard-and-mouse
 * emulator cannot do.
 *
 * <p>An emulated stick-as-mouse gives you a free-floating pointer to drive
 * onto a 16x16 slot, from a couch, on a 4K screen. This instead collects
 * where the slots and buttons actually are and steps between them, so a flick
 * of the stick lands dead centre on the next one.
 *
 * <p>It steps the <em>real</em> pointer rather than drawing a highlight of
 * its own, which is what keeps it honest: hover states, tooltips, item counts
 * and every screen's existing mouse handling all keep working, because from
 * the game's side nothing unusual happened. The right stick still moves the
 * pointer freely for anything this cannot enumerate.
 */
public final class Navigator {
	private Navigator() {}

	/** How far the left stick must go before it counts as a step. */
	private static final float STEP_THRESHOLD = 0.5f;

	/** Held-direction repeat, tuned like a key: one step, a pause, then a run. */
	private static final float REPEAT_DELAY_SECONDS = 0.35f;
	private static final float REPEAT_INTERVAL_SECONDS = 0.11f;

	private static final float FREE_CURSOR_PIXELS_PER_SECOND = 500f;

	/**
	 * Sideways distance costs more than forward distance, so pressing "down"
	 * in a grid prefers the slot directly below over one that is nearer in a
	 * straight line but a column across. Purely a feel constant; higher makes
	 * navigation more column-locked.
	 */
	private static final double PERPENDICULAR_PENALTY = 2.5;

	/** SDL's left-shift bit. {@code MouseButtonEvent.hasShiftDown()} tests {@code modifiers & 3}. */
	private static final int SHIFT_MODIFIER = 1;

	private static double cursorX;
	private static double cursorY;
	private static float repeatCooldown;
	private static boolean repeating;
	private static Screen seatedOn;

	public static void reset() {
		seatedOn = null;
		repeatCooldown = 0f;
		repeating = false;
	}

	public static void onFrame(Gamepad pad, Minecraft client, float frameSeconds) {
		Screen screen = client.gui.screen();
		if (screen == null) return;

		List<NavTarget> targets = Targets.collect(screen);

		if (seatedOn != screen) {
			seat(client, screen, targets);
		}

		moveFreely(pad, client, frameSeconds);
		step(pad, targets, frameSeconds);
		press(pad, screen);

		warp(client);
	}

	/**
	 * Put the cursor somewhere sensible when a screen opens, rather than
	 * wherever the mouse happened to be left. Nearest target to the middle,
	 * since that is usually the container itself rather than a stray corner
	 * button.
	 */
	private static void seat(Minecraft client, Screen screen, List<NavTarget> targets) {
		seatedOn = screen;
		repeatCooldown = 0f;

		Window window = client.getWindow();
		cursorX = window.getGuiScaledWidth() / 2.0;
		cursorY = window.getGuiScaledHeight() / 2.0;

		NavTarget nearest = null;
		double best = Double.MAX_VALUE;
		for (NavTarget target : targets) {
			double distance = distanceSquared(target, cursorX, cursorY);
			if (distance < best) {
				best = distance;
				nearest = target;
			}
		}

		if (nearest != null) {
			cursorX = nearest.centerX();
			cursorY = nearest.centerY();
		}
	}

	/**
	 * The right stick as a plain pointer. Kept alongside stepping rather than
	 * replaced by it: not everything is enumerable — a scrollable list, a map,
	 * a screen from a mod that draws its own controls — and without this those
	 * become unreachable rather than merely awkward.
	 */
	private static void moveFreely(Gamepad pad, Minecraft client, float frameSeconds) {
		float x = pad.rightX();
		float y = pad.rightY();
		if (x == 0f && y == 0f) return;

		Window window = client.getWindow();
		cursorX = Math.clamp(cursorX + x * FREE_CURSOR_PIXELS_PER_SECOND * frameSeconds, 0, window.getGuiScaledWidth());
		cursorY = Math.clamp(cursorY + y * FREE_CURSOR_PIXELS_PER_SECOND * frameSeconds, 0, window.getGuiScaledHeight());
	}

	private static void step(Gamepad pad, List<NavTarget> targets, float frameSeconds) {
		int dx = 0;
		int dy = 0;

		if (pad.isDown(Binds.NAV_LEFT) || pad.leftX() <= -STEP_THRESHOLD) dx--;
		if (pad.isDown(Binds.NAV_RIGHT) || pad.leftX() >= STEP_THRESHOLD) dx++;
		if (pad.isDown(Binds.NAV_UP) || pad.leftY() <= -STEP_THRESHOLD) dy--;
		if (pad.isDown(Binds.NAV_DOWN) || pad.leftY() >= STEP_THRESHOLD) dy++;

		if (dx == 0 && dy == 0) {
			repeatCooldown = 0f;
			repeating = false;
			return;
		}

		if (repeatCooldown > 0f) {
			repeatCooldown -= frameSeconds;
			return;
		}

		// Same shape as a held key: the first step lands the moment the stick
		// moves, then a long pause, then a fast run. The flag is what
		// separates those two, since the cooldown has always run down to zero
		// by the time we get here and cannot tell them apart on its own.
		repeatCooldown = repeating ? REPEAT_INTERVAL_SECONDS : REPEAT_DELAY_SECONDS;
		repeating = true;

		NavTarget next = pick(targets, dx, dy);
		if (next != null) {
			cursorX = next.centerX();
			cursorY = next.centerY();
		}
	}

	/** Nearest target in the requested direction, or null at the edge. */
	private static NavTarget pick(List<NavTarget> targets, int dx, int dy) {
		NavTarget best = null;
		double bestCost = Double.MAX_VALUE;

		for (NavTarget target : targets) {
			double offsetX = target.centerX() - cursorX;
			double offsetY = target.centerY() - cursorY;

			double along = offsetX * dx + offsetY * dy;
			// Strictly forward: a target level with the cursor is not "down"
			// from it, and including those makes a grid step sideways.
			if (along <= 0.5) continue;

			double perpendicular = Math.abs(offsetX * dy - offsetY * dx);
			double cost = along + PERPENDICULAR_PENALTY * perpendicular;

			if (cost < bestCost) {
				bestCost = cost;
				best = target;
			}
		}

		return best;
	}

	private static void press(Gamepad pad, Screen screen) {
		if (pad.justPressed(Binds.CLICK)) {
			click(screen, InputConstants.MOUSE_BUTTON_LEFT, 0);
		}
		if (pad.justPressed(Binds.RIGHT_CLICK)) {
			click(screen, InputConstants.MOUSE_BUTTON_RIGHT, 0);
		}
		if (pad.justPressed(Binds.QUICK_MOVE)) {
			click(screen, InputConstants.MOUSE_BUTTON_LEFT, SHIFT_MODIFIER);
		}
		// Start closes as well as opens, so it toggles the pause menu the way a
		// console game does. Without this the button that paused you does nothing to
		// get you back, and a pad-only player has to reach for the keyboard.
		if (pad.justPressed(Binds.CLOSE) || pad.justPressed(Binds.PAUSE)) {
			screen.onClose();
		}
	}

	/**
	 * A press and its release, through the screen's ordinary mouse path.
	 *
	 * <p>Both halves matter. Vanilla containers start a quick-craft drag on
	 * press and only commit it on release, so a click that never releases
	 * leaves the screen mid-drag and the next one behaves strangely.
	 */
	private static void click(Screen screen, int button, int modifiers) {
		MouseButtonEvent event = new MouseButtonEvent(cursorX, cursorY, new MouseButtonInfo(button, modifiers));

		screen.mouseClicked(event, false);
		screen.mouseReleased(event);
	}

	/**
	 * Put the operating system pointer where the navigator thinks it is.
	 *
	 * <p>This is what makes hover and tooltips work without reimplementing
	 * them: the warp produces an ordinary motion event, the game updates its
	 * own pointer state from it, and every screen highlights whatever is
	 * under the cursor exactly as it would for a mouse.
	 */
	private static void warp(Minecraft client) {
		Window window = client.getWindow();
		if (window.getGuiScaledWidth() == 0 || window.getGuiScaledHeight() == 0) return;

		float windowX = (float) (cursorX * window.getScreenWidth() / window.getGuiScaledWidth());
		float windowY = (float) (cursorY * window.getScreenHeight() / window.getGuiScaledHeight());

		SDLMouse.SDL_WarpMouseInWindow(window.handle(), windowX, windowY);
	}

	private static double distanceSquared(NavTarget target, double x, double y) {
		double offsetX = target.centerX() - x;
		double offsetY = target.centerY() - y;
		return offsetX * offsetX + offsetY * offsetY;
	}
}
