package justfatlard.couch_controls;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** The player's preferences, from a file the mod menu writes back to, so the two cannot disagree. */
public final class CouchSettings {
	private CouchSettings() {}

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
		.resolve(CouchControls.MOD_ID).resolve("settings.properties");

	public enum Toggle {
		RUMBLE("rumble", true),
		INVERT_LOOK("invert_look", false);

		public final String key;
		public final boolean fallback;

		Toggle(String key, boolean fallback) {
			this.key = key;
			this.fallback = fallback;
		}
	}

	/** Every number is a percent. */
	public enum Number {
		RUMBLE_STRENGTH("rumble_strength", 10, 100, 10, 100),
		/**
		 * Of a stick's travel, and ignored as rest; one each, since sticks wear unevenly. Kept under
		 * the half push a waiting pad needs to take over.
		 */
		LEFT_DEADZONE("left_deadzone", 4, 40, 2, 18),
		RIGHT_DEADZONE("right_deadzone", 4, 40, 2, 18),
		/** Of 220 degrees a second at full push. */
		LOOK_SPEED("look_speed", 25, 300, 25, 100),
		/** Of 500 GUI pixels a second at full push. */
		POINTER_SPEED("pointer_speed", 25, 300, 25, 100);

		public final String key;
		public final int min, max, step, fallback;

		Number(String key, int min, int max, int step, int fallback) {
			this.key = key;
			this.min = min;
			this.max = max;
			this.step = step;
			this.fallback = fallback;
		}
	}

	private static final Map<Toggle, Boolean> toggles = new EnumMap<>(Toggle.class);
	private static final Map<Number, Integer> numbers = new EnumMap<>(Number.class);

	static {
		for (Toggle toggle : Toggle.values()) toggles.put(toggle, toggle.fallback);
		for (Number number : Number.values()) numbers.put(number, number.fallback);
	}

	public static boolean get(Toggle toggle) {
		return toggles.get(toggle);
	}

	public static void set(Toggle toggle, boolean on) {
		toggles.put(toggle, on);
		save();
	}

	public static int get(Number number) {
		return numbers.get(number);
	}

	/** {@link #get} over 100. */
	public static float fraction(Number number) {
		return numbers.get(number) / 100f;
	}

	public static void set(Number number, int value) {
		numbers.put(number, Math.clamp(value, number.min, number.max));
		save();
	}

	public static void load() {
		if (!Files.exists(FILE)) return;
		Properties read = new Properties();
		try (Reader reader = Files.newBufferedReader(FILE)) {
			read.load(reader);
		} catch (IOException e) {
			CouchControls.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
			return;
		}
		for (Toggle toggle : Toggle.values()) {
			String value = read.getProperty(toggle.key);
			if (value != null) toggles.put(toggle, Boolean.parseBoolean(value.trim()));
		}
		for (Number number : Number.values()) {
			String value = read.getProperty(number.key);
			if (value == null) continue;
			try {
				numbers.put(number, Math.clamp(Integer.parseInt(value.trim()), number.min, number.max));
			} catch (NumberFormatException e) {
				CouchControls.LOGGER.warn("{}: {} is not a number, kept at {}", FILE, number.key, numbers.get(number));
			}
		}
	}

	private static void save() {
		Properties out = new Properties();
		toggles.forEach((toggle, on) -> out.setProperty(toggle.key, String.valueOf(on)));
		numbers.forEach((number, value) -> out.setProperty(number.key, String.valueOf(value)));
		StringBuilder ranges = new StringBuilder("Couch Controls; numbers are percents:");
		for (Number number : Number.values()) ranges.append(' ').append(number.key).append(' ').append(number.min).append('-').append(number.max);
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				out.store(writer, ranges.toString());
			}
		} catch (IOException e) {
			CouchControls.LOGGER.warn("Could not write {}: {}", FILE, e.toString());
		}
	}
}
