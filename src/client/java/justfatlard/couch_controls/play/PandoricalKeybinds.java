package justfatlard.couch_controls.play;

import justfatlard.pandorical.client.actions.ActionMenus;
import justfatlard.pandorical.client.keybind.KeybindManager;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLGamepad;

import java.util.Map;

/** Pandorical's action menus and pooled keybind slots default to the d-pad, which has nothing else to do in the world. */
public final class PandoricalKeybinds {
	private PandoricalKeybinds() {}

	/**
	 * Indexed by pool index. Index 0 is the player-facing slot 1, the one servers claim today,
	 * so it gets down, the easiest to hit without looking.
	 */
	private static final int[] BUTTON_FOR_POOL_INDEX = {
		SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT,
		SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT
	};

	public static void defaults(Map<String, Integer> into) {
		// Up goes to the action menus rather than to a fourth pooled slot. One button that opens
		// every menu a server offers is worth more than one more key nobody has bound, and it is
		// the only way a pad reaches them at all: a menu's own key is matched against the keyboard,
		// which a controller never touches.
		KeyMapping menus = ActionMenus.menusKey();
		if (menus != null) into.put(menus.getName(), SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP);

		for (int index = 0; index < BUTTON_FOR_POOL_INDEX.length; index++) {
			KeyMapping mapping = KeybindManager.poolMapping(index);
			// Unclaimed slots too: a server can claim one on any join, and the mapping is stable.
			if (mapping != null) into.put(mapping.getName(), BUTTON_FOR_POOL_INDEX[index]);
		}
	}
}
