package justfatlard.couch_controls.input;

import justfatlard.couch_controls.CouchControls;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.lwjgl.sdl.SDLGamepad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which pad control drives each key mapping, by the mapping's name. Only the player's changes
 * from {@link Binds#worldDefaults} are saved, so a default moved in a later version reaches
 * everyone who never touched it.
 */
public final class PadBinds {
	private PadBinds() {}

	public static final int NONE = -1;

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
		.resolve(CouchControls.MOD_ID).resolve("pad-binds.properties");

	/**
	 * Read straight off the keyboard event rather than through {@code isDown} or
	 * {@code consumeClick}, so a pad bound to one would do nothing.
	 */
	private static final Set<String> KEYBOARD_ONLY = Set.of("key.screenshot", "key.fullscreen", "key.friends");

	private static Map<String, Integer> defaults;
	private static final Map<String, Integer> changed = new HashMap<>();
	private static int revision;

	private static Map<String, Integer> defaults() {
		if (defaults == null) {
			defaults = new HashMap<>();
			Binds.worldDefaults(Minecraft.getInstance().options, defaults);
		}
		return defaults;
	}

	public static int slotOf(KeyMapping mapping) {
		Integer slot = changed.get(mapping.getName());
		return slot != null ? slot : defaultSlotOf(mapping);
	}

	public static int defaultSlotOf(KeyMapping mapping) {
		return defaults().getOrDefault(mapping.getName(), NONE);
	}

	public static boolean isDefault(KeyMapping mapping) {
		return slotOf(mapping) == defaultSlotOf(mapping);
	}

	public static boolean reachable(KeyMapping mapping) {
		return mapping.getCategory() != KeyMapping.Category.DEBUG && !KEYBOARD_ONLY.contains(mapping.getName());
	}

	public static void set(KeyMapping mapping, int slot) {
		if (slot == defaultSlotOf(mapping)) changed.remove(mapping.getName());
		else changed.put(mapping.getName(), slot);
		revision++;
		save();
	}

	public static void reset(KeyMapping mapping) {
		set(mapping, defaultSlotOf(mapping));
	}

	public static void resetAll() {
		changed.clear();
		revision++;
		save();
	}

	public static boolean anyChanged(Options options) {
		for (KeyMapping mapping : options.keyMappings) {
			if (!isDefault(mapping)) return true;
		}
		return false;
	}

	/** Bumped on every change, for anything holding a copy of the bindings. */
	public static int revision() {
		return revision;
	}

	public static void load() {
		if (!Files.exists(FILE)) return;
		try {
			for (String line : Files.readAllLines(FILE)) {
				int split = line.indexOf('=');
				if (line.isBlank() || line.startsWith("#") || split < 0) continue;
				String mapping = line.substring(0, split).trim();
				String control = line.substring(split + 1).trim();
				int slot = parse(control);
				if (slot == NONE && !control.equals("none")) {
					CouchControls.LOGGER.warn("{}: no controller button called '{}', left {} at its default", FILE, control, mapping);
					continue;
				}
				changed.put(mapping, slot);
			}
			revision++;
		} catch (IOException e) {
			CouchControls.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
		}
	}

	private static void save() {
		List<String> lines = new ArrayList<>();
		new TreeMap<>(changed).forEach((mapping, slot) -> lines.add(mapping + "=" + fileName(slot)));
		try {
			Files.createDirectories(FILE.getParent());
			Files.write(FILE, lines);
		} catch (IOException e) {
			CouchControls.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
		}
	}

	private static int parse(String control) {
		for (int slot = 0; slot < Gamepad.SLOT_COUNT; slot++) {
			if (fileName(slot).equals(control)) return slot;
		}
		return NONE;
	}

	/** By position, like SDL's constants, and never renamed: a saved file names controls by these. */
	private static String fileName(int slot) {
		if (slot == Gamepad.VIRTUAL_LEFT_TRIGGER) return "left_trigger";
		if (slot == Gamepad.VIRTUAL_RIGHT_TRIGGER) return "right_trigger";
		return switch (slot) {
			case NONE -> "none";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH -> "south";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_EAST -> "east";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_WEST -> "west";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH -> "north";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_BACK -> "back";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE -> "guide";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_START -> "start";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK -> "left_stick";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK -> "right_stick";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER -> "left_shoulder";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER -> "right_shoulder";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP -> "dpad_up";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN -> "dpad_down";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT -> "dpad_left";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT -> "dpad_right";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE1 -> "right_paddle1";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE1 -> "left_paddle1";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_PADDLE2 -> "right_paddle2";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_PADDLE2 -> "left_paddle2";
			case SDLGamepad.SDL_GAMEPAD_BUTTON_TOUCHPAD -> "touchpad";
			default -> "button" + slot;
		};
	}
}
