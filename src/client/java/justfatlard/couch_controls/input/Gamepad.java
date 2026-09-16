package justfatlard.couch_controls.input;

import justfatlard.couch_controls.CouchControls;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** SDL's raw pad state as deadzoned floats and edge-triggered buttons, from whichever pad is in use. */
public final class Gamepad {
	private static final float AXIS_MAX = 32767f;

	/** Radial, and sized for a worn stick: a new one loses nothing to it. */
	private static final float STICK_DEADZONE = 0.18f;

	/** Analog triggers act as buttons: down past the press edge, up again only under the release edge. */
	private static final float TRIGGER_PRESS = 0.4f;
	private static final float TRIGGER_RELEASE = 0.3f;

	private static final long RESCAN_INTERVAL_MS = 1000L;

	/** How long the driving pad must sit untouched before another may take over. */
	private static final long IDLE_BEFORE_HANDOVER_MS = 2000L;

	/** Well past the deadzone: a worn pad on a shelf rests outside it, and must never take the game. */
	private static final float HANDOVER_STICK = 0.5f;

	private static final int BUTTON_COUNT = SDLGamepad.SDL_GAMEPAD_BUTTON_COUNT;

	public static final int VIRTUAL_LEFT_TRIGGER = BUTTON_COUNT;
	public static final int VIRTUAL_RIGHT_TRIGGER = BUTTON_COUNT + 1;
	private static final int SLOT_COUNT = BUTTON_COUNT + 2;

	private boolean subsystemReady;
	/** Every connected pad, held open, because a pad must be open to be read and any of them may be the one in use. */
	private final List<Long> open = new ArrayList<>();
	private long handle;
	/** True for the one poll where {@link #handle} changed; that pad's held buttons press nothing. */
	private boolean handedOver;
	private long lastScanMs;
	private long lastActivityMs;

	private final boolean[] down = new boolean[SLOT_COUNT];
	private final boolean[] wasDown = new boolean[SLOT_COUNT];

	private float leftX, leftY, rightX, rightY, leftTrigger, rightTrigger;

	public void init() {
		if (!SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
			CouchControls.LOGGER.warn("SDL gamepad subsystem failed to start; controller support is off");
			return;
		}

		// Minecraft owns the SDL event pump, and pad events queued there would be drained
		// by it or crowd out its input. State is polled instead.
		SDLGamepad.SDL_SetGamepadEventsEnabled(false);
		subsystemReady = true;
	}

	/** Once per frame, before anything reads state. */
	public void poll(long nowMs) {
		if (!subsystemReady) return;

		SDLGamepad.SDL_UpdateGamepads();

		long driving = handle;
		rescan(nowMs);
		chooseActive(nowMs);
		handedOver = handle != 0L && handle != driving;

		System.arraycopy(down, 0, wasDown, 0, SLOT_COUNT);

		if (handle == 0L) {
			// Cleared, not frozen: a pad unplugged mid-press would leave that button stuck down.
			java.util.Arrays.fill(down, false);
			leftX = leftY = rightX = rightY = leftTrigger = rightTrigger = 0f;
			return;
		}

		for (int button = 0; button < BUTTON_COUNT; button++) {
			down[button] = SDLGamepad.SDL_GetGamepadButton(handle, button);
		}

		leftTrigger = normalizeTrigger(SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER);
		rightTrigger = normalizeTrigger(SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER);
		down[VIRTUAL_LEFT_TRIGGER] = leftTrigger >= (wasDown[VIRTUAL_LEFT_TRIGGER] ? TRIGGER_RELEASE : TRIGGER_PRESS);
		down[VIRTUAL_RIGHT_TRIGGER] = rightTrigger >= (wasDown[VIRTUAL_RIGHT_TRIGGER] ? TRIGGER_RELEASE : TRIGGER_PRESS);

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

		if (handedOver) System.arraycopy(down, 0, wasDown, 0, SLOT_COUNT);
	}

	/** Keep {@link #open} matching what SDL sees. Rate limited: SDL_GetGamepads allocates on every call. */
	private void rescan(long nowMs) {
		for (int i = open.size() - 1; i >= 0; i--) {
			long candidate = open.get(i);
			if (SDLGamepad.SDL_GamepadConnected(candidate)) continue;

			if (candidate == handle) {
				handle = 0L;
				CouchControls.LOGGER.info("Controller disconnected");
			}
			SDLGamepad.SDL_CloseGamepad(candidate);
			open.remove(i);
		}

		if (nowMs - lastScanMs < RESCAN_INTERVAL_MS) return;
		lastScanMs = nowMs;

		IntBuffer ids = SDLGamepad.SDL_GetGamepads();
		if (ids == null) return;

		while (ids.hasRemaining()) {
			int id = ids.get();
			if (isOpen(id)) continue;

			long opened = SDLGamepad.SDL_OpenGamepad(id);
			if (opened != 0L) open.add(opened);
		}
	}

	private boolean isOpen(int id) {
		for (long candidate : open) {
			if (SDLGamepad.SDL_GetGamepadID(candidate) == id) return true;
		}
		return false;
	}

	private void chooseActive(long nowMs) {
		if (handle != 0L && inUse(handle)) {
			lastActivityMs = nowMs;
			return;
		}

		if (handle == 0L) {
			if (open.isEmpty()) return;
			bind(open.get(0), nowMs);
			return;
		}

		if (open.size() < 2 || nowMs - lastActivityMs < IDLE_BEFORE_HANDOVER_MS) return;

		for (long candidate : open) {
			if (candidate == handle || !wantsControl(candidate)) continue;
			bind(candidate, nowMs);
			return;
		}
	}

	private void bind(long candidate, long nowMs) {
		handle = candidate;
		lastActivityMs = nowMs;
		CouchControls.LOGGER.info("Controller connected: {}{}",
			SDLGamepad.SDL_GetGamepadName(handle),
			open.size() > 1 ? " (" + open.size() + " connected)" : "");
	}

	private boolean inUse(long candidate) {
		return anyButtonOrTrigger(candidate)
			|| deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, STICK_DEADZONE)
			|| deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY, STICK_DEADZONE);
	}

	private boolean wantsControl(long candidate) {
		return anyButtonOrTrigger(candidate)
			|| deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, HANDOVER_STICK)
			|| deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY, HANDOVER_STICK);
	}

	private boolean anyButtonOrTrigger(long candidate) {
		for (int button = 0; button < BUTTON_COUNT; button++) {
			if (SDLGamepad.SDL_GetGamepadButton(candidate, button)) return true;
		}
		return axis(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) >= TRIGGER_PRESS
			|| axis(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) >= TRIGGER_PRESS;
	}

	private boolean deflected(long candidate, int xAxis, int yAxis, float threshold) {
		float x = axis(candidate, xAxis);
		float y = axis(candidate, yAxis);
		return Math.sqrt(x * x + y * y) > threshold;
	}

	private float axis(long candidate, int axis) {
		return SDLGamepad.SDL_GetGamepadAxis(candidate, axis) / AXIS_MAX;
	}

	private float raw(int axis) {
		return SDLGamepad.SDL_GetGamepadAxis(handle, axis) / AXIS_MAX;
	}

	private float normalizeTrigger(int axis) {
		// Triggers only travel positive; the negative half is noise on some pads.
		return Math.max(0f, raw(axis));
	}

	/** Rescales a stick so the deadzone edge reads as zero and full deflection still reads as one. */
	private static float deadzoneScale(float x, float y) {
		float magnitude = (float) Math.sqrt(x * x + y * y);
		if (magnitude <= STICK_DEADZONE) return 0f;

		float adjusted = (magnitude - STICK_DEADZONE) / (1f - STICK_DEADZONE);
		return Math.min(adjusted, 1f) / magnitude;
	}

	public boolean isConnected() {
		return handle != 0L;
	}

	public boolean handedOver() {
		return handedOver;
	}

	public boolean isDown(int slot) {
		return down[slot];
	}

	/** True on the frame a button goes down, and not again until it is released. */
	public boolean justPressed(int slot) {
		return down[slot] && !wasDown[slot];
	}

	/** Whether the pad was touched this poll: a button going down, or a stick or trigger off rest. */
	public boolean used() {
		if (handle == 0L || handedOver) return false;
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			if (down[slot] && !wasDown[slot]) return true;
		}
		return leftX != 0f || leftY != 0f || rightX != 0f || rightY != 0f;
	}

	/** The pad being driven, for asking SDL about it; 0 when there is none. */
	public long handle() {
		return handle;
	}

	public float leftX() { return leftX; }
	public float leftY() { return leftY; }
	public float rightX() { return rightX; }
	public float rightY() { return rightY; }
	public float leftTrigger() { return leftTrigger; }
	public float rightTrigger() { return rightTrigger; }
	public static float triggerPress() { return TRIGGER_PRESS; }

	public void rumble(float low, float high, int durationMs) {
		if (handle == 0L) return;
		SDLGamepad.SDL_RumbleGamepad(
			handle,
			(short) (Math.clamp(low, 0f, 1f) * 0xFFFF),
			(short) (Math.clamp(high, 0f, 1f) * 0xFFFF),
			durationMs);
	}

	public void close() {
		for (long candidate : open) SDLGamepad.SDL_CloseGamepad(candidate);
		open.clear();
		handle = 0L;
		if (subsystemReady) {
			SDLInit.SDL_QuitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
			subsystemReady = false;
		}
	}
}
