package justfatlard.couch_controls.play;

import justfatlard.couch_controls.CouchControls;
import justfatlard.couch_controls.Driver;
import justfatlard.couch_controls.input.Binds;
import justfatlard.couch_controls.input.Gamepad;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;

import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The pad in the world. Actions are vanilla key mappings reporting pressed ({@code KeyMappingMixin});
 * movement is the exception, because a key is binary and a stick is not.
 */
public final class WorldControls {
	private WorldControls() {}

	/** Deliberately slow: a camera too fast is unusable, one too slow merely annoying. */
	private static final float LOOK_DEGREES_PER_SECOND = 220f;

	/** {@code Entity.turn} multiplies both arguments by this before applying them. */
	private static final float TURN_SCALE = 0.15f;

	/** Above the stick deadzone, because a camera magnifies drift that walking would not show. */
	private static final float LOOK_THRESHOLD = 0.06f;

	/** Low: a walk the server is never told about is one it can refuse to let you sprint out of. */
	private static final float DIGITAL_THRESHOLD = 0.1f;

	private static final Map<KeyMapping, Integer> boundSlots = new IdentityHashMap<>();

	/** Edges wait here for {@code consumeClick}: vanilla drains clicks per tick, the pad is read per frame. */
	private static final Map<KeyMapping, Integer> pendingClicks = new IdentityHashMap<>();

	private static Vec2 padMove;

	/**
	 * While a bobber is out, a held use trigger clicks on its own at a rate that follows the pull.
	 * The range brackets what Minedew Fishing's click-driven fight asks for.
	 */
	private static final float REEL_MIN_CLICKS_PER_SECOND = 2f;
	private static final float REEL_MAX_CLICKS_PER_SECOND = 8f;
	private static boolean rodInHand;
	private static boolean reeling;
	/** The squeeze that cast the line is still on when the bobber appears; it must let go before it counts. */
	private static boolean reelArmed;
	private static boolean reelHeld;
	private static float reelDue;
	private static boolean sprintLatched;
	/** Vanilla's Sneak: Toggle. A press flips the key's own state through {@code ToggleKeyMapping}; the hold is not reported. */
	private static boolean sneakToggles;
	private static float steerPhase;

	/** False while a screen is up or the pad is gone. */
	private static boolean active;

	/** Buttons already down when the world took the pad back; they read as up until released. */
	private static final Set<Integer> spent = new HashSet<>();

	private static boolean held(Gamepad pad, int slot) {
		return pad.isDown(slot) && !spent.contains(slot);
	}

	private static void bind(Options options) {
		if (!boundSlots.isEmpty()) return;

		if (CouchControls.PANDORICAL_LOADED) PandoricalKeybinds.bind(boundSlots);

		boundSlots.put(options.keyJump, Binds.JUMP);
		boundSlots.put(options.keyShift, Binds.SNEAK);
		boundSlots.put(options.keyDrop, Binds.DROP);
		boundSlots.put(options.keyInventory, Binds.INVENTORY);
		boundSlots.put(options.keyAttack, Binds.ATTACK);
		boundSlots.put(options.keyUse, Binds.USE);
		boundSlots.put(options.keySwapOffhand, Binds.SWAP_HANDS);
		boundSlots.put(options.keyPlayerList, Binds.PLAYER_LIST);
	}

	/** Called once per frame while no screen is open. */
	public static void onFrame(Gamepad pad, Minecraft client, float frameSeconds) {
		bind(client.options);
		if (!active) {
			for (int slot : boundSlots.values()) if (pad.isDown(slot)) spent.add(slot);
		}
		spent.removeIf(slot -> !pad.isDown(slot));
		active = true;

		sneakToggles = client.options.toggleCrouch().get();
		if (sneakToggles && pad.justPressed(Binds.SNEAK)) client.options.keyShift.setDown(true);

		rodInHand = client.player != null && usesRod(client.player);
		boolean bobberOut = rodInHand && client.player.fishing != null;
		if (bobberOut && !reeling) {
			reelArmed = !pad.isDown(Binds.USE);
			reelHeld = false;
		}
		reeling = bobberOut;

		for (Map.Entry<KeyMapping, Integer> entry : boundSlots.entrySet()) {
			if (reeling && entry.getValue() == Binds.USE) continue;
			if (pad.justPressed(entry.getValue())) {
				pendingClicks.merge(entry.getKey(), 1, Integer::sum);
			}
		}
		if (reeling) applyReel(pad, client.options, frameSeconds);
		else reelHeld = false;

		// Pause has no KeyMapping; vanilla reads Escape directly. Returning keeps
		// this frame from steering the player behind the menu.
		if (pad.justPressed(Binds.PAUSE)) {
			client.pauseGame(false);
			return;
		}

		LocalPlayer player = client.player;
		if (player == null) {
			padMove = null;
			return;
		}

		applyLook(pad, player, frameSeconds);
		applyMovement(pad, player);
		applyHotbar(pad, player);
	}

	/** Whether use reaches a rod: in the main hand, or in the offhand behind an empty main hand. */
	private static boolean usesRod(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		return main.getItem() instanceof FishingRodItem
			|| (main.isEmpty() && player.getOffhandItem().getItem() instanceof FishingRodItem);
	}

	/** One click on the squeeze, then a run whose rate follows the pull. */
	private static void applyReel(Gamepad pad, Options options, float frameSeconds) {
		if (!pad.isDown(Binds.USE)) {
			reelArmed = true;
			reelHeld = false;
			return;
		}
		if (!reelArmed) return;
		if (!reelHeld) {
			reelHeld = true;
			reelDue = 0f;
			pendingClicks.merge(options.keyUse, 1, Integer::sum);
			return;
		}
		float press = Gamepad.triggerPress();
		float pull = Math.clamp((pad.leftTrigger() - press) / (1f - press), 0f, 1f);
		float rate = REEL_MIN_CLICKS_PER_SECOND + pull * (REEL_MAX_CLICKS_PER_SECOND - REEL_MIN_CLICKS_PER_SECOND);
		reelDue += frameSeconds * rate;
		while (reelDue >= 1f) {
			reelDue -= 1f;
			pendingClicks.merge(options.keyUse, 1, Integer::sum);
		}
	}

	private static void applyLook(Gamepad pad, LocalPlayer player, float frameSeconds) {
		float x = pad.rightX();
		float y = pad.rightY();
		if (Math.abs(x) < LOOK_THRESHOLD && Math.abs(y) < LOOK_THRESHOLD) return;

		// Squared, sign kept: most of the stick's travel buys small corrections.
		float yaw = x * Math.abs(x) * LOOK_DEGREES_PER_SECOND * frameSeconds;
		float pitch = y * Math.abs(y) * LOOK_DEGREES_PER_SECOND * frameSeconds;

		player.turn(yaw / TURN_SCALE, pitch / TURN_SCALE);
	}

	private static void applyMovement(Gamepad pad, LocalPlayer player) {
		float x = pad.leftX();
		float y = pad.leftY();

		if (x == 0f && y == 0f) {
			padMove = null;
			sprintLatched = false;
			return;
		}

		// The stick's Y is positive downward; Minecraft's forward is positive away.
		padMove = new Vec2(-x, -y);

		if (pad.justPressed(Binds.SPRINT)) sprintLatched = true;
	}

	/**
	 * The boolean record the server is sent: the stick as directions, and the sprint latch.
	 * Jump and sneak already arrive through their key mappings.
	 */
	public static Input mergeKeyPresses(Input keys) {
		if (!active) return keys;

		Gamepad pad = Driver.gamepad();
		float x = pad.leftX();
		float y = pad.leftY();

		boolean left = x <= -DIGITAL_THRESHOLD;
		boolean right = x >= DIGITAL_THRESHOLD;

		if (isSteeringBoat()) {
			boolean pulse = steerPulse(Math.abs(x));
			left = left && pulse;
			right = right && pulse;
		} else {
			steerPhase = 0f;
		}

		return new Input(
			keys.forward() || y <= -DIGITAL_THRESHOLD,
			keys.backward() || y >= DIGITAL_THRESHOLD,
			keys.left() || left,
			keys.right() || right,
			keys.jump(),
			keys.shift(),
			keys.sprint() || sprintLatched);
	}

	private static boolean isSteeringBoat() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.getControlledVehicle() instanceof AbstractBoat;
	}

	/**
	 * A boat turns a fixed step on every tick left or right is held, with no half press and no
	 * analog path. So the stick's magnitude, squared like the camera's, becomes the fraction of
	 * ticks the press is held, and the boat's momentum smooths the gaps into a slower turn.
	 */
	private static boolean steerPulse(float magnitude) {
		steerPhase += magnitude * magnitude;
		if (steerPhase < 1f) return false;

		steerPhase -= 1f;
		return true;
	}

	private static void applyHotbar(Gamepad pad, LocalPlayer player) {
		int step = 0;
		if (pad.justPressed(Binds.HOTBAR_NEXT)) step++;
		if (pad.justPressed(Binds.HOTBAR_PREV)) step--;
		if (step == 0) return;

		player.getInventory().setSelectedSlot(Math.floorMod(player.getInventory().getSelectedSlot() + step, 9));
	}

	/** The pad's movement vector, or null when the stick is centred and vanilla's should stand. */
	public static Vec2 moveVector() {
		return padMove;
	}

	public static boolean isPadDown(KeyMapping mapping) {
		if (!active) return false;

		Integer slot = boundSlots.get(mapping);
		if (slot == null) return false;
		// A rod is used by the click, never the hold: vanilla's held-use repeat would cast and
		// retrieve by turns, and while reeling would pile onto the cadence.
		if (rodInHand && slot == Binds.USE) return false;
		if (sneakToggles && slot == Binds.SNEAK) return false;
		return held(Driver.gamepad(), slot);
	}

	public static boolean consumePadClick(KeyMapping mapping) {
		Integer pending = pendingClicks.get(mapping);
		if (pending == null || pending == 0) return false;

		pendingClicks.put(mapping, pending - 1);
		return true;
	}

	/**
	 * Drops every held input. Mixins read this state on every tick whether or not anything
	 * recomputes it. The bindings, {@link #spent} and the reel's arming outlive it on purpose.
	 */
	public static void release() {
		active = false;
		padMove = null;
		sprintLatched = false;
		steerPhase = 0f;
		rodInHand = false;
		reeling = false;
		reelHeld = false;
		pendingClicks.clear();
	}
}
