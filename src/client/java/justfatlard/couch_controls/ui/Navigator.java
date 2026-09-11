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
 *
 * <p>The mouse is never held hostage. The pointer is only warped on a frame
 * the pad moved it, and when the mouse has moved on its own since the last
 * warp, the cursor follows the mouse instead: whichever hand moved last has
 * the pointer, and the pad's next step starts from wherever it is.
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

	/**
	 * A warp's echo comes back through the event pump a frame later, so for
	 * this many frames a pointer that is not yet where it was sent is the
	 * warp still landing, not the mouse moving. Past it, the pump has had its
	 * turn and any difference is the mouse's.
	 */
	private static final int WARP_SETTLE_FRAMES = 3;

	/** The pointer counts as moved by the mouse past this, in window pixels; under it is rounding. */
	private static final double MOUSE_MOVED_PIXELS = 1.5;

	private static double cursorX;
	private static double cursorY;
	private static float repeatCooldown;
	private static boolean repeating;
	/** The screen the cursor was last placed for; a new screen starts from the mouse. */
	private static Screen current;
	/** The screen the pad has seated itself on; null until its first step there. */
	private static Screen seatedOn;
	/** Whether the pad moved the cursor this frame, and so the pointer should follow it. */
	private static boolean moved;
	/** Where the pointer was last sent, in window pixels, and how long ago. */
	private static double warpedX;
	private static double warpedY;
	private static int framesSinceWarp = Integer.MAX_VALUE;

	public static void reset() {
		current = null;
		seatedOn = null;
		repeatCooldown = 0f;
		repeating = false;
		moved = false;
		framesSinceWarp = Integer.MAX_VALUE;
	}

	public static void onFrame(Gamepad pad, Minecraft client, float frameSeconds) {
		Screen screen = client.gui.screen();
		if (screen == null) return;

		List<NavTarget> targets = Targets.collect(screen);

		if (current != screen) {
			// A new screen: the cursor is wherever the mouse is, and stays the
			// mouse's until the pad asks for it. Seating on every open moved the
			// pointer out from under a mouse user whenever a pad was plugged in.
			current = screen;
			seatedOn = null;
			repeatCooldown = 0f;
			repeating = false;
			framesSinceWarp = Integer.MAX_VALUE;
			follow(client, true);
		} else {
			follow(client, false);
		}

		moveFreely(pad, client, frameSeconds);
		step(pad, client, screen, targets, frameSeconds);
		press(pad, screen);

		if (moved) warp(client);
		moved = false;
	}

	/**
	 * Let the mouse have the cursor when it has moved since the pad last put
	 * the pointer somewhere. Nothing is warped here: this is the pad reading
	 * where the mouse went, so its next step starts from there.
	 */
	private static void follow(Minecraft client, boolean always) {
		double mouseX = client.mouseHandler.xpos();
		double mouseY = client.mouseHandler.ypos();
		if (!always) {
			if (framesSinceWarp < WARP_SETTLE_FRAMES) {
				framesSinceWarp++;
				boolean landed = Math.abs(mouseX - warpedX) <= MOUSE_MOVED_PIXELS
					&& Math.abs(mouseY - warpedY) <= MOUSE_MOVED_PIXELS;
				if (landed) framesSinceWarp = Integer.MAX_VALUE;
				else return;
			}
			if (Math.abs(mouseX - warpedX) <= MOUSE_MOVED_PIXELS && Math.abs(mouseY - warpedY) <= MOUSE_MOVED_PIXELS) return;
		}

		Window window = client.getWindow();
		if (window.getScreenWidth() == 0 || window.getScreenHeight() == 0) return;
		cursorX = mouseX * window.getGuiScaledWidth() / window.getScreenWidth();
		cursorY = mouseY * window.getGuiScaledHeight() / window.getScreenHeight();
		warpedX = mouseX;
		warpedY = mouseY;
	}

	/**
	 * Put the cursor somewhere sensible the first time the pad steps on a
	 * screen, rather than wherever the mouse happened to be left. Nearest
	 * target to the middle, since that is usually the container itself rather
	 * than a stray corner button.
	 */
	private static void seat(Minecraft client, Screen screen, List<NavTarget> targets) {
		seatedOn = screen;

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
		moved = true;
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
		moved = true;
	}

	private static void step(Gamepad pad, Minecraft client, Screen screen, List<NavTarget> targets, float frameSeconds) {
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

		// The first push on a screen seats the cursor and is spent on that: a
		// seat-and-step would land one past the slot the player was shown.
		if (seatedOn != screen) {
			seat(client, screen, targets);
			return;
		}

		NavTarget next = pick(targets, dx, dy);
		if (next != null) {
			cursorX = next.centerX();
			cursorY = next.centerY();
			moved = true;
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

		// The shoulders are the wheel. Menus have a whole class of interaction that
		// is scroll and nothing else: picking which item a bundle hands you next is
		// driven purely by BundleMouseActions.onMouseScrolled, so without this a pad
		// cannot reach inside a bundle at all. Scrollable lists get it for free.
		if (pad.justPressed(Binds.SCROLL_UP)) scroll(screen, 1.0);
		if (pad.justPressed(Binds.SCROLL_DOWN)) scroll(screen, -1.0);
	}

	private static final boolean NAVIGATION_SCROLL =
		net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pandorical") && PandoricalScroll.linked();

	private static void scroll(Screen screen, double notches) {
		Runnable send = () -> screen.mouseScrolled(cursorX, cursorY, 0.0, notches);
		if (NAVIGATION_SCROLL) PandoricalScroll.around(send);
		else send.run();
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
	 * under the cursor exactly as it would for a mouse. Only on a frame the
	 * pad moved the cursor; every frame was the mouse being dragged back to
	 * wherever the pad had left it.
	 */
	private static void warp(Minecraft client) {
		Window window = client.getWindow();
		if (window.getGuiScaledWidth() == 0 || window.getGuiScaledHeight() == 0) return;

		float windowX = (float) (cursorX * window.getScreenWidth() / window.getGuiScaledWidth());
		float windowY = (float) (cursorY * window.getScreenHeight() / window.getGuiScaledHeight());

		SDLMouse.SDL_WarpMouseInWindow(window.handle(), windowX, windowY);
		warpedX = windowX;
		warpedY = windowY;
		framesSinceWarp = 0;
	}

	private static double distanceSquared(NavTarget target, double x, double y) {
		double offsetX = target.centerX() - x;
		double offsetY = target.centerY() - y;
		return offsetX * offsetX + offsetY * offsetY;
	}
}
