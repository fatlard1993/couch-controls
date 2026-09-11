package justfatlard.couch_controls.input;

import org.lwjgl.sdl.SDLGamepad;

/**
 * Which physical control does what, by position (SOUTH, not A). The world and menu tables
 * overlap on purpose: SOUTH is jump and confirm, EAST is sneak and back out.
 */
public final class Binds {
	private Binds() {}

	// --- In world ---

	public static final int JUMP = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
	public static final int SNEAK = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;
	public static final int DROP = SDLGamepad.SDL_GAMEPAD_BUTTON_WEST;
	public static final int INVENTORY = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;

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
	public static final int QUICK_MOVE = SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH;
	public static final int CLOSE = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;

	public static final int SCROLL_UP = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;
	public static final int SCROLL_DOWN = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;

	public static final int NAV_UP = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP;
	public static final int NAV_DOWN = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN;
	public static final int NAV_LEFT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT;
	public static final int NAV_RIGHT = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT;
}
