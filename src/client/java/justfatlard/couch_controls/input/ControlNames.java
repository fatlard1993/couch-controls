package justfatlard.couch_controls.input;

import java.util.function.Function;
import justfatlard.couch_controls.play.HotbarCycle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.sdl.SDLGamepad;

/**
 * What each control is called on one particular pad, for hints and the key binds screen.
 *
 * <p>By the pad's own printing: the bottom face button is "A" on an Xbox pad, "Cross" on a
 * PlayStation one and "B" on a Switch Pro, and SDL knows which. Shoulders and triggers are not
 * labelled through SDL, so they go by the pad's family. With no pad at all, an Xbox pad's names.
 * The hint words are actions, not controls, so a hint says "[use]" and names whichever control
 * use is bound to.
 */
public final class ControlNames implements Function<String, String> {
	private final String south, east, west, north;
	private final String leftShoulder, rightShoulder, leftTrigger, rightTrigger;
	private final String leftStickPress, rightStickPress, start, back, guide;

	/** {@code pad} 0 for no pad. */
	public ControlNames(long pad) {
		south = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH, "A", "bottom button");
		east = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST, "B", "right button");
		west = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST, "X", "left button");
		north = face(pad, SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH, "Y", "top button");

		int type = pad == 0L ? SDLGamepad.SDL_GAMEPAD_TYPE_UNKNOWN : SDLGamepad.SDL_GetGamepadType(pad);
		boolean playstation = type == SDLGamepad.SDL_GAMEPAD_TYPE_PS3 || type == SDLGamepad.SDL_GAMEPAD_TYPE_PS4
			|| type == SDLGamepad.SDL_GAMEPAD_TYPE_PS5;
		boolean nintendo = type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO
			|| type == SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR;
		if (playstation) {
			leftShoulder = "L1"; rightShoulder = "R1"; leftTrigger = "L2"; rightTrigger = "R2";
			leftStickPress = "L3"; rightStickPress = "R3"; start = "Options"; back = "Share"; guide = "PS";
		} else if (nintendo) {
			leftShoulder = "L"; rightShoulder = "R"; leftTrigger = "ZL"; rightTrigger = "ZR";
			leftStickPress = "LS"; rightStickPress = "RS"; start = "+"; back = "-"; guide = "Home";
		} else {
			leftShoulder = "LB"; rightShoulder = "RB"; leftTrigger = "LT"; rightTrigger = "RT";
			leftStickPress = "LS"; rightStickPress = "RS"; start = "Start"; back = "Back"; guide = "Guide";
		}
	}

	private static String face(long pad, int button, String noPad, String unlabelled) {
		if (pad == 0L) return noPad;
		return switch (SDLGamepad.SDL_GetGamepadButtonLabel(pad, button)) {
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_A -> "A";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_B -> "B";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_X -> "X";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_Y -> "Y";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_CROSS -> "Cross";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_CIRCLE -> "Circle";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_SQUARE -> "Square";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LABEL_TRIANGLE -> "Triangle";
			default -> unlabelled;
		};
	}

	/** A {@link Gamepad} slot's name; null for {@link PadBinds#NONE}. */
	public String label(int slot) {
		if (slot == PadBinds.NONE) return null;
		if (slot == Gamepad.VIRTUAL_LEFT_TRIGGER) return leftTrigger;
		if (slot == Gamepad.VIRTUAL_RIGHT_TRIGGER) return rightTrigger;
		return switch (slot) {
			case SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH -> south;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_EAST -> east;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_WEST -> west;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH -> north;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_BACK -> back;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE -> guide;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_START -> start;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK -> leftStickPress;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK -> rightStickPress;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER -> leftShoulder;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER -> rightShoulder;
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP -> "D-pad up";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN -> "D-pad down";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT -> "D-pad left";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT -> "D-pad right";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1 -> "Paddle R1";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1 -> "Paddle L1";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2 -> "Paddle R2";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2 -> "Paddle L2";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_TOUCHPAD -> "Touchpad";
			default -> "Button " + slot;
		};
	}

	@Override
	public String apply(String control) {
		return switch (control) {
			case "confirm" -> label(Binds.CLICK);
			case "back" -> label(Binds.CLOSE);
			case "secondary" -> label(Binds.RIGHT_CLICK);
			case "quick_move" -> label(Binds.QUICK_MOVE);
			case "scroll_up" -> label(Binds.SCROLL_UP);
			case "scroll_down" -> label(Binds.SCROLL_DOWN);
			case "pause" -> label(Binds.PAUSE);
			case "sprint" -> "left stick all the way forward";
			case "move" -> "left stick";
			case "look", "pointer" -> "right stick";
			case "navigate" -> "D-pad";
			default -> bound(control);
		};
	}

	/** Null when the action is unbound, so the hint keeps its word rather than naming a wrong control. */
	private String bound(String control) {
		Options options = Minecraft.getInstance().options;
		KeyMapping mapping = switch (control) {
			case "jump" -> options.keyJump;
			case "chat" -> options.keyChat;
			case "drop" -> options.keyDrop;
			case "inventory" -> options.keyInventory;
			case "use" -> options.keyUse;
			case "attack" -> options.keyAttack;
			case "hotbar_prev" -> HotbarCycle.previous();
			case "hotbar_next" -> HotbarCycle.next();
			case "sneak" -> options.keyShift;
			case "swap_hands" -> options.keySwapOffhand;
			case "player_list" -> options.keyPlayerList;
			default -> null;
		};
		return mapping == null ? null : label(PadBinds.slotOf(mapping));
	}
}
