package justfatlard.couch_controls.input;

import org.lwjgl.sdl.SDLGamepad;

/**
 * Which physical control does what.
 *
 * <p>SDL names buttons by position, not by letter (SOUTH, not A), so this
 * layout follows the thumb rather than the label and comes out right on an
 * Xbox pad, a PlayStation pad, and the 8BitDo alike.
 *
 * <p>In-world and in-menu are separate tables on purpose, and the overlaps
 * are deliberate: SOUTH is jump and also the confirm click, EAST is sneak and
 * also back out. Both read as "the obvious one" and "the cancel one" in their
 * own context, which is easier to keep in the hand than one global table
 * where every button means exactly one thing.
 *
 * <p>Hardcoded rather than configurable. A rebinding UI is a real want, but
 * it is a screen to navigate before you can navigate screens, and this layout
 * has to be usable before any of that exists.
 */
public final class Binds {
	private Binds() {}

	// --- In world ---

	public static final int JUMP = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
	public static final int SNEAK = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;
	public static final int DROP = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;
	public static final int INVENTORY = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;

	/**
	 * Triggers, matching every shooter's muscle memory: the dominant one
	 * breaks blocks and hits things, the other places and uses.
	 */
	public static final int ATTACK = Gamepad.VIRTUAL_RIGHT_TRIGGER;
	public static final int USE = Gamepad.VIRTUAL_LEFT_TRIGGER;

	public static final int HOTBAR_PREV = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;
	public static final int HOTBAR_NEXT = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;

	public static final int SPRINT = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK;
	public static final int SWAP_HANDS = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK;

	public static final int PAUSE = SDLGamepad.SDL_GAMEPAD_BUTTON_START;
	public static final int PLAYER_LIST = SDLGamepad.SDL_GAMEPAD_BUTTON_BACK;

	// --- In menus ---

	public static final int CLICK = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
	public static final int RIGHT_CLICK = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;

	/** Shift-click: the whole reason chest-to-inventory transfers are bearable. */
	public static final int QUICK_MOVE = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;

	public static final int CLOSE = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;

	public static final int NAV_UP = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP;
	public static final int NAV_DOWN = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN;
	public static final int NAV_LEFT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT;
	public static final int NAV_RIGHT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT;
}
