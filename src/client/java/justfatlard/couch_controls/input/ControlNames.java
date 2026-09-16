package justfatlard.couch_controls.input;

import java.util.function.Function;
import org.lwjgl.sdl.SDLGamepad;

/**
 * What each control is called on one particular pad, for hints that name them.
 *
 * <p>By the pad's own printing: the bottom face button is "A" on an Xbox pad, "Cross" on a
 * PlayStation one and "B" on a Switch Pro, and SDL knows which. Shoulders and triggers are not
 * labelled through SDL, so they go by the pad's family. The words are the controls in
 * {@link Binds}, by what they do rather than where they are, so a hint says "[use]" and never
 * has to know which trigger that is.
 */
public final class ControlNames implements Function<String, String> {
	private final String south, east, west, north;
	private final String leftShoulder, rightShoulder, leftTrigger, rightTrigger;
	private final String leftStickPress, rightStickPress, start, back;

	public ControlNames(long pad) {
		south = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH, "bottom button");
		east = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST, "right button");
		west = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST, "left button");
		north = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH, "top button");

		int type = SDLGamepad.SDL_GetGamepadType(pad);
		boolean playstation = type == SDLGamepad.SDL_GAMEPAD_TYPE_PS3 || type == SDLGamepad.SDL_GAMEPAD_TYPE_PS4
			|| type == SDLGamepad.SDL_GAMEPAD_TYPE_PS5;
		boolean nintendo = type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO
			|| type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR;
		if (playstation) {
			leftShoulder = "L1"; rightShoulder = "R1"; leftTrigger = "L2"; rightTrigger = "R2";
			leftStickPress = "L3"; rightStickPress = "R3"; start = "Options"; back = "Share";
		} else if (nintendo) {
			leftShoulder = "L"; rightShoulder = "R"; leftTrigger = "ZL"; rightTrigger = "ZR";
			leftStickPress = "left stick press"; rightStickPress = "right stick press"; start = "+"; back = "-";
		} else {
			leftShoulder = "LB"; rightShoulder = "RB"; leftTrigger = "LT"; rightTrigger = "RT";
			leftStickPress = "LS"; rightStickPress = "RS"; start = "Start"; back = "Back";
		}
	}

	private static String face(long pad, int button, String fallback) {
		return switch (SDLGamepad.SDL_GetGamepadButtonLabel(pad, button)) {
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_A -> "A";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_B -> "B";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_X -> "X";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_Y -> "Y";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_CROSS -> "Cross";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_CIRCLE -> "Circle";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_SQUARE -> "Square";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_TRIANGLE -> "Triangle";
			default -> fallback;
		};
	}

	@Override
	public String apply(String control) {
		return switch (control) {
			case "confirm", "jump" -> south;
			case "back", "chat" -> east;
			case "drop", "secondary" -> west;
			case "inventory", "quick_move" -> north;
			case "use" -> leftTrigger;
			case "attack" -> rightTrigger;
			case "hotbar_prev", "scroll_down" -> leftShoulder;
			case "hotbar_next", "scroll_up" -> rightShoulder;
			case "sneak" -> leftStickPress;
			case "sprint" -> "left stick all the way forward";
			case "swap_hands" -> rightStickPress;
			case "pause" -> start;
			case "player_list" -> back;
			case "move" -> "left stick";
			case "look", "pointer" -> "right stick";
			case "navigate" -> "D-pad";
			default -> null;
		};
	}
}
