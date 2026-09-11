package justfatlard.couch_controls.input;

import justfatlard.couch_controls.CouchControls;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

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

	/** Analog triggers act as buttons: down past the press edge, up again only under the release edge. */
	private static final float TRIGGER_PRESS = 0.4f;
	private static final float TRIGGER_RELEASE = 0.3f;

	/** How often to look for pads appearing or going away. */
	private static final long RESCAN_INTERVAL_MS = 1000L;

	/**
	 * How long the pad in charge must sit untouched before another one holding it may take over.
	 *
	 * <p>Long enough that it is never a race between two people playing, short enough that
	 * picking up the other pad and pressing something just works. Nothing switches while the
	 * active pad is in use, so this can only ever fire when whoever holds it has stopped.
	 */
	private static final long IDLE_BEFORE_HANDOVER_MS = 2000L;

	private static final int BUTTON_COUNT = SDLGamepad.SDL_GAMEPAD_BUTTON_COUNT;

	/** Triggers are axes, but bind like buttons, so they get slots past the real ones. */
	public static final int VIRTUAL_LEFT_TRIGGER = BUTTON_COUNT;
	public static final int VIRTUAL_RIGHT_TRIGGER = BUTTON_COUNT + 1;
	private static final int SLOT_COUNT = BUTTON_COUNT + 2;

	private boolean subsystemReady;
	/**
	 * Every connected pad, held open; {@link #handle} is whichever one is driving.
	 *
	 * <p>All of them rather than the chosen one, because a pad has to be open to be read and
	 * the whole question is which one is being used. Keeping them open makes the handover a
	 * change of which handle is consulted, with nothing to close, reopen, or miss the moment
	 * of. SDL_UpdateGamepads refreshes all of them in the one call already being made.
	 */
	private final List<Long> open = new ArrayList<>();
	private long handle;
	private long lastScanMs;
	private long lastActivityMs;

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

		rescan(nowMs);
		chooseActive(nowMs);

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
	}

	/**
	 * Keep {@link #open} matching the pads SDL can see: open the new, close the departed.
	 *
	 * <p>Rate limited because SDL_GetGamepads allocates a buffer per call and this runs every
	 * frame.
	 */
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

	/**
	 * Decide which pad is driving: the one being used, not the one SDL happened to list first.
	 *
	 * <p>Which pad that is cannot be known when the game starts - nobody is touching anything
	 * yet - so the first one found takes it and keeps it until it goes quiet. Once it has been
	 * idle a couple of seconds, any other pad showing input takes over.
	 *
	 * <p>Choosing blind is what this replaces, and it is not a hypothetical: a controller left
	 * plugged in to charge beside the one in somebody's hands enumerated first, took the binding,
	 * and reported itself connected exactly as though it had worked. The only cure was to unplug
	 * the other pad.
	 */
	private void chooseActive(long nowMs) {
		if (handle != 0L && active(handle)) {
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
		// The count matters: with one pad this says what it found, and with more it says which
		// of them it is listening to, which is the thing that used to be invisible.
		CouchControls.LOGGER.info("Controller connected: {}{}",
			SDLGamepad.SDL_GetGamepadName(handle),
			open.size() > 1 ? " (" + open.size() + " connected)" : "");
	}

	/**
	 * How far a stick must be pushed on an idle pad before it may take control.
	 *
	 * <p>Well past {@link #STICK_DEADZONE}, because a stick resting outside the deadzone is
	 * exactly what a worn pad left on a shelf does, and that pad must never be able to take the
	 * game away from the one in somebody's hands. A deliberate push clears this easily.
	 */
	private static final float HANDOVER_STICK = 0.5f;

	/** Whether this pad is being touched right now: any button, stick or trigger off its rest. */
	private boolean active(long candidate) {
		for (int button = 0; button < BUTTON_COUNT; button++) {
			if (SDLGamepad.SDL_GetGamepadButton(candidate, button)) return true;
		}

		if (axis(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) >= TRIGGER_PRESS
			|| axis(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) >= TRIGGER_PRESS) {
			return true;
		}

		return deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX,
				SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, STICK_DEADZONE)
			|| deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX,
				SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY, STICK_DEADZONE);
	}

	/**
	 * Whether an idle pad is being asked to take over: a button, a trigger, or a stick pushed
	 * further than a resting one ever sits. Buttons and triggers need no such margin - they
	 * cannot drift - so only the sticks are held to the higher bar.
	 */
	private boolean wantsControl(long candidate) {
		for (int button = 0; button < BUTTON_COUNT; button++) {
			if (SDLGamepad.SDL_GetGamepadButton(candidate, button)) return true;
		}

		if (axis(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) >= TRIGGER_PRESS
			|| axis(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) >= TRIGGER_PRESS) {
			return true;
		}

		return deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX,
				SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY, HANDOVER_STICK)
			|| deflected(candidate, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTX,
				SDLGamepad.SDL_GAMEPAD_AXIS_RIGHTY, HANDOVER_STICK);
	}

	private boolean deflected(long candidate, int xAxis, int yAxis, float threshold) {
		float x = axis(candidate, xAxis);
		float y = axis(candidate, yAxis);
		// Radial, matching the deadzone's own test rather than a per-axis one
		return Math.sqrt(x * x + y * y) > threshold;
	}

	private float axis(long candidate, int axis) {
		return SDLGamepad.SDL_GetGamepadAxis(candidate, axis) / AXIS_MAX;
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
	/** How far the left trigger is pulled, 0 to 1, past the deadzone. */
	public float leftTrigger() { return leftTrigger; }
	/** How far the right trigger is pulled, 0 to 1, past the deadzone. */
	public float rightTrigger() { return rightTrigger; }
	/** Where a trigger counts as pressed. */
	public static float triggerPress() { return TRIGGER_PRESS; }

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
