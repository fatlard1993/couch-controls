package justfatlard.couch_controls.play;

import justfatlard.couch_controls.input.Binds;
import justfatlard.pandorical.client.keybind.KeybindManager;
import net.minecraft.client.KeyMapping;

import java.util.Map;

/**
 * The Pandorical half of the world bindings, kept in its own class so the rest
 * of the mod never mentions Pandorical's types.
 *
 * <p>Server mods in this suite declare their own keybinds through Pandorical's
 * pooled slots rather than shipping client code: poopsmith's poop key is slot 1,
 * defaulting to G. Those are real {@code KeyMapping}s, so a pad button reaches
 * them exactly the way it reaches jump or sneak, and the press travels to the
 * server down Pandorical's ordinary path with no extra protocol.
 *
 * <p>The d-pad carries them because it is the only cluster with nothing to do in
 * the world: {@code Binds.NAV_*} steer menus, and menus are the one place these
 * bindings are not read.
 */
final class PandoricalKeybinds {
	private PandoricalKeybinds() {}

	/**
	 * Pool slot per d-pad direction. Down first because slot 1 is the one a
	 * server actually claims today, and down is the easiest to hit without
	 * looking.
	 */
	private static final int[] SLOT_FOR_DIRECTION = {0, 1, 2, 3};
	private static final int[] DIRECTION_BUTTON = {
		Binds.NAV_DOWN, Binds.NAV_UP, Binds.NAV_LEFT, Binds.NAV_RIGHT
	};

	static void bind(Map<KeyMapping, Integer> into) {
		for (int i = 0; i < SLOT_FOR_DIRECTION.length; i++) {
			KeyMapping mapping = KeybindManager.poolMapping(SLOT_FOR_DIRECTION[i]);
			// Unclaimed slots are bound anyway: a server can claim one at any
			// join, and the mapping object is stable for the client's lifetime,
			// so binding once at startup beats rechecking every frame.
			if (mapping != null) into.put(mapping, DIRECTION_BUTTON[i]);
		}
	}
}
