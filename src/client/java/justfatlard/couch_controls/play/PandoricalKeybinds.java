package justfatlard.couch_controls.play;

import justfatlard.couch_controls.input.Binds;
import justfatlard.pandorical.client.keybind.KeybindManager;
import net.minecraft.client.KeyMapping;

import java.util.Map;

/** Pandorical's pooled keybind slots on the d-pad, which has nothing else to do in the world. */
final class PandoricalKeybinds {
	private PandoricalKeybinds() {}

	/**
	 * Indexed by pool index. Index 0 is the player-facing slot 1, the one servers claim today,
	 * so it gets down, the easiest to hit without looking.
	 */
	private static final int[] BUTTON_FOR_POOL_INDEX = {
		Binds.NAV_DOWN, Binds.NAV_UP, Binds.NAV_LEFT, Binds.NAV_RIGHT
	};

	static void bind(Map<KeyMapping, Integer> into) {
		for (int index = 0; index < BUTTON_FOR_POOL_INDEX.length; index++) {
			KeyMapping mapping = KeybindManager.poolMapping(index);
			// Unclaimed slots too: a server can claim one on any join, and the mapping is stable.
			if (mapping != null) into.put(mapping, BUTTON_FOR_POOL_INDEX[index]);
		}
	}
}
