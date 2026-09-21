package justfatlard.couch_controls.play;

import justfatlard.couch_controls.CouchSettings;
import justfatlard.couch_controls.Driver;
import justfatlard.couch_controls.mixin.FishingHookAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The pad shaking for what happens to the player. Only while the pad is the hands in use: a pad
 * left on a shelf to charge buzzing across the table is the thing to avoid.
 *
 * <p>SDL plays one effect at a time and a new one replaces the old, so a light effect waits out a
 * stronger one rather than cutting it short.
 */
public final class Rumble {
	private Rumble() {}

	/** A new player instance (a join, a respawn, a dimension change) arrives before its health does. */
	private static final int SETTLE_TICKS = 20;

	/** How far past its radius an explosion is still felt, as a multiple of the radius. */
	private static final double EXPLOSION_REACH = 4.0;

	private static long busyUntilMs;
	private static float busyLevel;

	private static LocalPlayer watched;
	private static int settling;
	private static float lastLife;
	private static boolean wasBiting;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Rumble::tick);
	}

	/** {@code low} is the heavy motor, {@code high} the light one; both 0 to 1 before the strength setting. */
	public static void pulse(float low, float high, int durationMs) {
		if (!CouchSettings.get(CouchSettings.Toggle.RUMBLE) || !Driver.padInHand()) return;

		long now = System.nanoTime() / 1_000_000L;
		float level = Math.max(low, high);
		if (now < busyUntilMs && level < busyLevel) return;
		busyUntilMs = now + durationMs;
		busyLevel = level;

		float strength = CouchSettings.fraction(CouchSettings.Number.RUMBLE_STRENGTH);
		Driver.gamepad().rumble(low * strength, high * strength, durationMs);
	}

	public static void stop() {
		busyUntilMs = 0L;
		Driver.gamepad().rumble(0f, 0f, 0);
	}

	/** What a setting change feels like, so the slider can be judged by hand. */
	public static void sample() {
		busyUntilMs = 0L;
		pulse(0.6f, 0.4f, 250);
	}

	public static void blockBroken() {
		pulse(0f, 0.3f, 50);
	}

	public static void hitLanded() {
		pulse(0.25f, 0.45f, 70);
	}

	public static void explosion(Vec3 center, float radius) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return;
		double felt = 1.0 - player.position().distanceTo(center) / (radius * EXPLOSION_REACH);
		if (felt <= 0.05) return;
		float level = (float) felt;
		pulse(level, level * 0.7f, 250 + (int) (level * 250));
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player != watched) {
			watched = player;
			settling = SETTLE_TICKS;
			wasBiting = false;
		}
		if (player == null) return;

		// Absorption counts: a hit it soaks is still a hit.
		float life = player.getHealth() + player.getAbsorptionAmount();
		float lost = lastLife - life;
		lastLife = life;
		if (settling > 0) {
			settling--;
			return;
		}

		if (lost > 0f) {
			if (player.isDeadOrDying()) {
				pulse(1f, 1f, 700);
			} else {
				float level = Math.clamp(0.3f + lost / 8f, 0.3f, 1f);
				pulse(level, level * 0.5f, (int) Math.clamp(120f + lost * 40f, 120f, 450f));
			}
		}

		// Vanilla's bite flag, which Minedew Fishing sets as well: the tug on the line.
		boolean biting = player.fishing != null && ((FishingHookAccessor) player.fishing).couch_controls$biting();
		if (biting && !wasBiting) pulse(0.15f, 0.8f, 180);
		wasBiting = biting;
	}
}
