package justfatlard.couch_controls.play;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;

/**
 * Stepping through the hotbar, which vanilla gives only the scroll wheel. As key mappings it
 * reaches the pad's shoulders the way every other action does, and can be moved like one; a
 * keyboard can have it too, unbound until given a key.
 */
public final class HotbarCycle {
	private HotbarCycle() {}

	private static KeyMapping previous;
	private static KeyMapping next;

	/** From client init: the options system takes key mappings then and never later. */
	public static void register() {
		previous = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.couch-controls.hotbar_previous", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.INVENTORY));
		next = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.couch-controls.hotbar_next", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.INVENTORY));
		ClientTickEvents.END_CLIENT_TICK.register(HotbarCycle::tick);
	}

	public static KeyMapping previous() {
		return previous;
	}

	public static KeyMapping next() {
		return next;
	}

	/** Drained every tick, screen or not, so presses cannot pile up behind a menu. */
	private static void tick(Minecraft client) {
		int step = 0;
		while (next.consumeClick()) step++;
		while (previous.consumeClick()) step--;
		if (step == 0 || client.player == null || client.gui.screen() != null) return;

		Inventory inventory = client.player.getInventory();
		inventory.setSelectedSlot(Math.floorMod(inventory.getSelectedSlot() + step, 9));
	}
}
