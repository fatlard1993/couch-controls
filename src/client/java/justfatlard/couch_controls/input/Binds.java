package justfatlard.couch_controls.input;

import justfatlard.couch_controls.CouchControls;
import justfatlard.couch_controls.play.HotbarCycle;
import justfatlard.couch_controls.play.PandoricalKeybinds;
import net.minecraft.client.Options;
import org.lwjgl.sdl.SDLGamepad;

import java.util.Map;

/**
 * Which physical control does what, by position (SOUTH, not A). The world's layout is only the
 * default, since {@link PadBinds} holds what the player moved; the menu's and pause are fixed.
 * The two overlap on purpose: SOUTH is jump and confirm. Sprint is no button: see
 * {@code WorldControls}.
 */
public final class Binds {
	private Binds() {}

	// --- In world ---

	/** Escape's counterpart, and like Escape not rebindable: in the key binds screen it clears a pad binding. */
	public static final int PAUSE = SDLGamepad.SDL_GAMEPAD_BUTTON_START;

	/** By key mapping name. */
	static void worldDefaults(Options options, Map<String, Integer> into) {
		into.put(options.keyJump.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH);
		into.put(options.keyDrop.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_WEST);
		into.put(options.keyInventory.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH);
		// The same button closes it: in menus EAST is close.
		into.put(options.keyChat.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_EAST);
		into.put(options.keyAttack.getName(), Gamepad.VIRTUAL_RIGHT_TRIGGER);
		into.put(options.keyUse.getName(), Gamepad.VIRTUAL_LEFT_TRIGGER);
		into.put(HotbarCycle.previous().getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER);
		into.put(HotbarCycle.next().getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER);
		// The left thumb's, not the right's: the right thumb is on the camera.
		into.put(options.keyShift.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK);
		into.put(options.keySwapOffhand.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK);
		into.put(options.keyPlayerList.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_BACK);
		if (CouchControls.PANDORICAL_LOADED) PandoricalKeybinds.defaults(into);
	}

	// --- In menus ---

	public static final int CLICK = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
	public static final int RIGHT_CLICK = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;
	public static final int QUICK_MOVE = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;
	public static final int CLOSE = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;

	public static final int SCROLL_UP = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;
	public static final int SCROLL_DOWN = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;

	public static final int NAV_UP = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP;
	public static final int NAV_DOWN = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN;
	public static final int NAV_LEFT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT;
	public static final int NAV_RIGHT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT;

	// --- On the on-screen keyboard, where the stick and d-pad move between keys ---

	public static final int TYPE = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
	public static final int DELETE = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;
	public static final int SPACE = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;
	/** Closes the keyboard, not the screen under it. */
	public static final int KEYBOARD_CLOSE = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;
	public static final int ENTER = SDLGamepad.SDL_GAMEPAD_BUTTON_START;
	public static final int SHIFT = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK;
	public static final int CURSOR_LEFT = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;
	public static final int CURSOR_RIGHT = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;
}
