package justfatlard.couch_controls.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import justfatlard.couch_controls.CouchControls;
import justfatlard.couch_controls.input.Binds;
import justfatlard.couch_controls.input.Gamepad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.sdl.SDLMouse;

import java.util.List;

/**
 * Steps the real pointer between a screen's slots and buttons, and yields it to the mouse
 * whenever the mouse moved last.
 */
public final class Navigator {
	private Navigator() {}

	/** How far the left stick must go before it counts as a step, and how far back before a held step lets go. */
	private static final float STEP_THRESHOLD = 0.5f;
	private static final float STEP_RELEASE = 0.4f;

	/** Held-direction repeat, tuned like a key: one step, a pause, then a run. */
	private static final float REPEAT_DELAY_SECONDS = 0.35f;
	private static final float REPEAT_INTERVAL_SECONDS = 0.11f;

	private static final float FREE_CURSOR_PIXELS_PER_SECOND = 500f;

	/** Presses are dropped this long after a screen opens over the world, so one meant for the world cannot click it. */
	private static final float OPEN_GRACE_SECONDS = 0.2f;
	private static float grace;

	/** Sideways distance costs this much more than forward, so "down" in a grid keeps to its column. */
	private static final double PERPENDICULAR_PENALTY = 2.5;

	/** SDL's left-shift bit. {@code MouseButtonEvent.hasShiftDown()} tests {@code modifiers & 3}. */
	private static final int SHIFT_MODIFIER = 1;

	/**
	 * A warp's motion event arrives through the pump a frame later, so for this many frames a
	 * pointer short of where it was sent is the warp still landing, not the mouse moving.
	 */
	private static final int WARP_SETTLE_FRAMES = 3;

	/** Past this, in window pixels, the pointer was moved by the mouse; under it is rounding. */
	private static final double MOUSE_MOVED_PIXELS = 1.5;

	private static double cursorX;
	private static double cursorY;
	private static float repeatCooldown;
	private static boolean repeating;
	/** The screen the cursor was last placed for; a new screen starts from the mouse. */
	private static Screen current;
	/** The screen the pad has seated itself on; null until its first step there. */
	private static Screen seatedOn;
	private static boolean moved;
	/** Where the pointer was last sent, in window pixels. */
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
			grace = current == null ? OPEN_GRACE_SECONDS : 0f;
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
		if (grace > 0f) grace -= frameSeconds;
		else press(pad, screen);

		if (moved) warp(client);
		moved = false;
	}

	/** Take the cursor from wherever the mouse went, if it moved since the pad last placed the pointer. */
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

	/** The pad's first push on a screen lands on the target nearest the middle, usually the container. */
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

	/** The right stick as a plain pointer, for what cannot be enumerated: scroll regions, maps, custom-drawn screens. */
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

		float reach = repeating ? STEP_RELEASE : STEP_THRESHOLD;
		if (pad.isDown(Binds.NAV_LEFT) || pad.leftX() <= -reach) dx--;
		if (pad.isDown(Binds.NAV_RIGHT) || pad.leftX() >= reach) dx++;
		if (pad.isDown(Binds.NAV_UP) || pad.leftY() <= -reach) dy--;
		if (pad.isDown(Binds.NAV_DOWN) || pad.leftY() >= reach) dy++;

		if (dx == 0 && dy == 0) {
			repeatCooldown = 0f;
			repeating = false;
			return;
		}

		if (repeatCooldown > 0f) {
			repeatCooldown -= frameSeconds;
			return;
		}

		// The cooldown is zero for the first step and for the run alike; the flag tells them apart.
		repeatCooldown = repeating ? REPEAT_INTERVAL_SECONDS : REPEAT_DELAY_SECONDS;
		repeating = true;

		// The seating push is spent on seating: a seat-and-step lands one past the slot shown.
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
			// Strictly forward: counting targets level with the cursor makes a grid step sideways.
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
			if (openedByInventoryKey(screen)) screen.onClose();
			else click(screen, InputConstants.MOUSE_BUTTON_LEFT, SHIFT_MODIFIER);
		}
		if ((pad.justPressed(Binds.CLOSE) || pad.justPressed(Binds.PAUSE)) && screen.shouldCloseOnEsc()) {
			screen.onClose();
		}

		// Some interactions are scroll and nothing else: a bundle's next item is chosen only
		// in BundleMouseActions.onMouseScrolled.
		if (pad.justPressed(Binds.SCROLL_UP)) scroll(screen, 1.0);
		if (pad.justPressed(Binds.SCROLL_DOWN)) scroll(screen, -1.0);
	}

	private static boolean openedByInventoryKey(Screen screen) {
		return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
	}

	private static final boolean NAVIGATION_SCROLL =
		CouchControls.PANDORICAL_LOADED && PandoricalScroll.linked();

	private static void scroll(Screen screen, double notches) {
		Runnable send = () -> screen.mouseScrolled(cursorX, cursorY, 0.0, notches);
		if (NAVIGATION_SCROLL) PandoricalScroll.around(send);
		else send.run();
	}

	/** Press and release both: containers start a quick-craft drag on press and commit it only on release. */
	private static void click(Screen screen, int button, int modifiers) {
		MouseButtonEvent event = new MouseButtonEvent(cursorX, cursorY, new MouseButtonInfo(button, modifiers));

		screen.mouseClicked(event, false);
		screen.mouseReleased(event);
	}

	/** Moves the OS pointer, so hover and tooltips update through the game's own mouse path. */
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
