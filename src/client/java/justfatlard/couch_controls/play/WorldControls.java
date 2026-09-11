package justfatlard.couch_controls.play;

import justfatlard.couch_controls.Driver;
import justfatlard.couch_controls.input.Binds;
import justfatlard.couch_controls.input.Gamepad;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything the pad does while the player is in the world.
 *
 * <p>Actions are not simulated here. Each one is bound to the vanilla
 * {@link KeyMapping} that already means it, and {@code KeyMappingMixin} makes
 * that mapping report pressed while the pad button is held. Block breaking
 * with its progress and cooldown, item use with its charge-up, sprint,
 * sneak — all of it then runs down the ordinary key path, unmodified and
 * unaware. The alternative, calling game methods directly, means
 * reimplementing every one of those behaviours and getting them subtly wrong.
 *
 * <p>Movement is the exception, because a key is binary and a stick is not:
 * see {@link #moveVector()}.
 */
public final class WorldControls {
	private WorldControls() {}

	/**
	 * Turn rate at full stick deflection. Camera speed is the single most
	 * personal setting on a controller and this one is not adjustable yet, so
	 * it is set deliberately toward the slower end — a camera that is too
	 * fast is unusable, one that is too slow is merely annoying.
	 */
	private static final float LOOK_DEGREES_PER_SECOND = 220f;

	/** {@code Entity.turn} multiplies both arguments by this before applying them. */
	private static final float TURN_SCALE = 0.15f;

	/**
	 * Stick deflection below which the camera holds still. Above the raw
	 * deadzone, because a camera magnifies drift that movement would not
	 * even show.
	 */
	private static final float LOOK_THRESHOLD = 0.06f;

	/**
	 * Where a stick push starts counting as "that direction" for the boolean
	 * record. Low on purpose: the deadzone has already thrown away noise, so
	 * anything that reaches here is deliberate, and a walk that the server is
	 * never told about is a walk it can refuse to let you sprint out of.
	 */
	private static final float DIGITAL_THRESHOLD = 0.1f;

	/** Pad buttons that stand in for a vanilla key, resolved once options exist. */
	private static final Map<KeyMapping, Integer> boundSlots = new IdentityHashMap<>();

	/**
	 * Presses seen but not yet claimed by {@code consumeClick}. Vanilla polls
	 * clicks on the game tick while the pad is read every frame, so an edge
	 * has to wait somewhere or a press made between two ticks is simply lost.
	 */
	private static final Map<KeyMapping, Integer> pendingClicks = new IdentityHashMap<>();

	private static Vec2 padMove;

	/**
	 * Reeling: the fishing fight is driven by right-clicks, every click one kick upward, so a
	 * mouse plays it by tapping at a rate. A trigger is a poor thing to tap at four a second and
	 * a good thing to squeeze, so while a bobber is out the use trigger becomes a cadence: a tap
	 * is still one click, and a hold clicks on its own, faster the harder it is pulled. Between
	 * these two rates, which bracket what the fight asks for.
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
	/** Vanilla's Sneak: Toggle. A press then flips the key's own state through {@code ToggleKeyMapping}, and the hold is not reported. */
	private static boolean sneakToggles;

	/**
	 * Fraction of a steering press carried over between ticks. See
	 * {@link #steerPulse(float)}.
	 */
	private static float steerPhase;

	/**
	 * False whenever the world is not what the pad is pointed at — a screen is
	 * open, or the pad went away. Every read below is gated on it, because
	 * this class holds state between frames and stale state does not stop
	 * being applied just because nothing updated it.
	 */
	private static boolean active;

	/** Buttons already down when the world took the pad back; they read as up until released. */
	private static final Set<Integer> spent = new HashSet<>();

	private static boolean held(Gamepad pad, int slot) {
		return pad.isDown(slot) && !spent.contains(slot);
	}

	/**
	 * Guarded here, in a class that always loads, so a client without Pandorical
	 * never reaches PandoricalKeybinds at all. Same shape as Targets' guard.
	 */
	private static final boolean PANDORICAL_LOADED =
		net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("pandorical");

	private static void bind(Options options) {
		if (!boundSlots.isEmpty()) return;

		if (PANDORICAL_LOADED) PandoricalKeybinds.bind(boundSlots);

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

		rodInHand = client.player != null
			&& client.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem;
		boolean bobberOut = rodInHand && client.player.fishing != null;
		if (bobberOut && !reeling) {
			// The line just went out. The finger that cast it is still down, and that press
			// has been spent: nothing more until it lifts.
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

		// Pause is the one action with no KeyMapping to borrow: vanilla drives it
		// straight off the escape key rather than through options, so it cannot ride
		// the bound-slot map above and calls the game directly instead. Returning
		// here keeps the same frame from also steering a player who just walked away
		// from the controls.
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

	/** The use trigger as a click cadence: one on the squeeze, then a run whose rate follows the pull. */
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

		// Squared response: the stick's own range is linear, but aiming wants
		// most of its travel spent on small corrections and only the last of
		// it on whipping around. Sign is restored after squaring.
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

		// Minecraft's forward axis is positive away from the player, while
		// the stick's Y is positive downward, so forward has to be negated.
		padMove = new Vec2(-x, -y);

		// Sprint is a latch, not a hold: nobody keeps a stick clicked in for
		// the length of a journey. It clears when the stick returns to centre
		// (above), which is also how the player stops sprinting.
		if (pad.justPressed(Binds.SPRINT)) sprintLatched = true;
	}

	/**
	 * Fold the pad into the boolean input record, called from the tail of
	 * {@code KeyboardInput.tick()}.
	 *
	 * <p>The timing is the whole point, and doing it anywhere else silently
	 * does nothing. This record is rebuilt from the keyboard on every game
	 * tick, so a gamepad written into it earlier in the frame is overwritten
	 * before a single consumer sees it. Writing it here, immediately after
	 * vanilla computes it, is the only point where it survives.
	 *
	 * <p>It matters more than it looks. {@link #moveVector()} is what the
	 * client walks by, so movement appears to work regardless — but this
	 * record is what gets sent to the server, and what the tutorial reads.
	 * Lose it and the server never learns the player is moving, sprinting or
	 * sneaking, and the "Move with W, A, S and D" toast never clears because
	 * as far as the game is concerned nobody ever moved.
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
	 * Turn a stick magnitude into a press pattern, for the one vehicle that reads
	 * nothing else.
	 *
	 * <p>A boat steers by adding a fixed step to its own rotation on every tick
	 * the left or right boolean is held. There is no half press, so a stick an
	 * eighth of the way over turns exactly as hard as one pinned to the edge, and
	 * the only available turn is the sharpest one. On foot this never shows,
	 * because {@link #moveVector()} carries the magnitude down a separate path;
	 * a boat has no such path.
	 *
	 * <p>So the magnitude becomes the fraction of ticks the press is held: a
	 * third of the way over presses on a third of the ticks. The boat's rotation
	 * carries momentum between ticks, which smooths the gaps back into a turn
	 * that is simply slower rather than one that stutters. Squared first, for the
	 * same reason the camera is: most of the travel should buy fine control.
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

	/**
	 * The analog half of movement, read by {@code LocalPlayerMixin}.
	 *
	 * <p>The boolean {@link Input} record above is what the server is told,
	 * and booleans are all it wants. This vector is what the client actually
	 * walks by, and keeping it analog is the difference between a stick that
	 * creeps and one that only ever sprints in eight directions.
	 *
	 * @return the pad's movement, or null when the stick is centred and
	 *         vanilla's own vector should stand.
	 */
	public static Vec2 moveVector() {
		return padMove;
	}

	public static boolean isPadDown(KeyMapping mapping) {
		if (!active) return false;

		Integer slot = boundSlots.get(mapping);
		// A rod is used by the click, never by the hold: vanilla's repeat of a held use would
		// cast and retrieve by turns, and while reeling would pile onto the cadence.
		if (rodInHand && slot != null && slot == Binds.USE) return false;
		if (sneakToggles && slot != null && slot == Binds.SNEAK) return false;
		return slot != null && held(Driver.gamepad(), slot);
	}

	public static boolean consumePadClick(KeyMapping mapping) {
		Integer pending = pendingClicks.get(mapping);
		if (pending == null || pending == 0) return false;

		pendingClicks.put(mapping, pending - 1);
		return true;
	}

	/**
	 * Drop every held input and stop answering for the pad.
	 *
	 * <p>Called when a screen opens and when the pad goes away, and both
	 * matter for the same reason: this class is read by mixins on every tick
	 * regardless of what it last computed. Without this, opening a chest
	 * while pushing the stick leaves {@link #moveVector()} returning that
	 * push forever, and the player walks away from the chest they just
	 * opened.
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
