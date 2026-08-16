package justfatlard.couch_controls.input;

import justfatlard.couch_controls.CouchControls;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;

import java.nio.IntBuffer;

/**
 * The pad: opening it, polling it, and turning SDL's raw shorts into the
 * deadzoned floats and edge-triggered booleans everything else here wants.
 *
 * <p>Minecraft 26.3 runs on SDL3 rather than GLFW ({@code Window} takes an
 * {@code SDL_Event}, and {@code InputConstants}' key codes are SDL scancodes),
 * so the gamepad API is already loaded in this process. There is no native
 * library to ship and no second input backend to reconcile.
 *
 * <p><b>The event queue is not ours.</b> Minecraft owns the SDL event pump,
 * and draining it here would eat input the game needs. So gamepad events are
 * switched off entirely and state is refreshed on demand with
 * {@link SDLGamepad#SDL_UpdateGamepads()}: polling only, nothing queued,
 * nothing consumed. That also makes freshness independent of when — or
 * whether — the game happens to pump.
 */
public final class Gamepad {
	/** SDL reports stick and trigger axes over the signed short range. */
	private static final float AXIS_MAX = 32767f;

	/**
	 * Radial, not per-axis: a square deadzone lets a stick pushed hard along
	 * one axis leak a few degrees of the other, which reads as drift on a
	 * camera and as a diagonal on a menu. Sized for a worn stick rather than
	 * a new one, since a new one costs nothing here and a worn one is
	 * unusable without it.
	 */
	private static final float STICK_DEADZONE = 0.18f;

	/** Analog triggers act as buttons; this is where they latch. */
	private static final float TRIGGER_THRESHOLD = 0.4f;

	/** How often to look for a pad when none is open. */
	private static final long RESCAN_INTERVAL_MS = 1000L;

	private static final int BUTTON_COUNT = SDLGamepad.SDL_GAMEPAD_BUTTON_COUNT;

	/** Triggers are axes, but bind like buttons, so they get slots past the real ones. */
	public static final int VIRTUAL_LEFT_TRIGGER = BUTTON_COUNT;
	public static final int VIRTUAL_RIGHT_TRIGGER = BUTTON_COUNT + 1;
	private static final int SLOT_COUNT = BUTTON_COUNT + 2;

	private boolean subsystemReady;
	private long handle;
	private long lastScanMs;

	private final boolean[] down = new boolean[SLOT_COUNT];
	private final boolean[] wasDown = new boolean[SLOT_COUNT];

	private float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger;

	/**
	 * Bring up the gamepad subsystem. Safe to call when the game already
	 * started SDL — subsystems are reference counted, and video/events being
	 * up says nothing about whether gamepads are.
	 */
	public void init() {
		if (!SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
			CouchControls.LOGGER.warn("SDL gamepad subsystem failed to start; controller support is off");
			return;
		}

		SDLGamepad.SDL_SetGamepadEventsEnabled(false);
		subsystemReady = true;
	}

	/** Refresh every button and axis. Call once per frame, before anything reads state. */
	public void poll(long nowMs) {
		if (!subsystemReady) return;

		SDLGamepad.SDL_UpdateGamepads();

		if (handle == 0L || !SDLGamepad.SDL_GamepadConnected(handle)) {
			acquire(nowMs);
		}

		System.arraycopy(down, 0, wasDown, 0, SLOT_COUNT);

		if (handle == 0L) {
			// Clear rather than freeze: a pad unplugged mid-press would
			// otherwise leave that button stuck down forever, and "stuck
			// sneak" outlives the unplug in a way the player cannot undo.
			java.util.Arrays.fill(down, false);
			leftX = leftY = rightX = rightY = leftTrigger = rightTrigger = 0f;
			return;
		}

		for (int button = 0; button < BUTTON_COUNT; button++) {
			down[button] = SDLGamepad.SDL_GetGamepadButton(handle, button);
		}

		leftTrigger = normalizeTrigger(SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER);
		rightTrigger = normalizeTrigger(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER);
		down[VIRTUAL_LEFT_TRIGGER] = leftTrigger >= TRIGGER_THRESHOLD;
		down[VIRTUAL_RIGHT_TRIGGER] = rightTrigger >= TRIGGER_THRESHOLD;

		float rawLeftX = raw(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX);
		float rawLeftY = raw(SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY);
		float rawRightX = raw(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX);
		float rawRightY = raw(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY);

		float leftScale = deadzoneScale(rawLeftX, rawLeftY);
		leftX = rawLeftX * leftScale;
		leftY = rawLeftY * leftScale;

		float rightScale = deadzoneScale(rawRightX, rawRightY);
		rightX = rawRightX * rightScale;
		rightY = rawRightY * rightScale;
	}

	private void acquire(long nowMs) {
		if (handle != 0L) {
			SDLGamepad.SDL_CloseGamepad(handle);
			handle = 0L;
			CouchControls.LOGGER.info("Controller disconnected");
		}

		// Rate limited because this runs every frame while no pad is present,
		// and SDL_GetGamepads allocates a buffer on each call.
		if (nowMs - lastScanMs < RESCAN_INTERVAL_MS) return;
		lastScanMs = nowMs;

		IntBuffer ids = SDLGamepad.SDL_GetGamepads();
		if (ids == null || ids.remaining() == 0) return;

		handle = SDLGamepad.SDL_OpenGamepad(ids.get(0));
		if (handle == 0L) return;

		CouchControls.LOGGER.info("Controller connected: {}", SDLGamepad.SDL_GetGamepadName(handle));
	}

	private float raw(int axis) {
		return SDLGamepad.SDL_GetGamepadAxis(handle, axis) / AXIS_MAX;
	}

	private float normalizeTrigger(int axis) {
		// Triggers rest at 0 and only travel positive, so they get no deadzone
		// scaling — just a clamp, since the negative half of the range is
		// noise on some pads.
		return Math.max(0f, raw(axis));
	}

	/**
	 * Rescales a stick so the deadzone edge reads as zero and full deflection
	 * still reads as one. Without the rescale, the first {@value
	 * #STICK_DEADZONE} of travel past the threshold jumps straight to that
	 * value, and fine aim near centre becomes impossible.
	 */
	private static float deadzoneScale(float x, float y) {
		float magnitude = (float) Math.sqrt(x * x + y * y);
		if (magnitude <= STICK_DEADZONE) return 0f;

		float adjusted = (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE);
		return Math.min(adjusted, 1f) / magnitude;
	}

	public boolean isConnected() {
		return handle != 0L;
	}

	public boolean isDown(int slot) {
		return down[slot];
	}

	/** True on the frame a button goes down, and not again until it is released. */
	public boolean justPressed(int slot) {
		return down[slot] && !wasDown[slot];
	}

	public float leftX() { return leftX; }
	public float leftY() { return leftY; }
	public float rightX() { return rightX; }
	public float rightY() { return rightY; }

	/**
	 * Fire the rumble motors. Free here — SDL owns them, so unlike the
	 * GLFW-era controller mods this needs no extra native library.
	 */
	public void rumble(float low, float high, int durationMs) {
		if (handle == 0L) return;
		SDLGamepad.SDL_RumbleGamepad(
			handle,
			(short) (Math.clamp(low, 0f, 1f) * 0xFFFF),
			(short) (Math.clamp(high, 0f, 1f) * 0xFFFF),
			durationMs);
	}

	public void close() {
		if (handle != 0L) {
			SDLGamepad.SDL_CloseGamepad(handle);
			handle = 0L;
		}
		if (subsystemReady) {
			SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
			subsystemReady = false;
		}
	}
}
