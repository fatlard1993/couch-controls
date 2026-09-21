package justfatlard.couch_controls.ui;

import com.mojang.blaze3d.platform.InputConstants;
import justfatlard.couch_controls.Driver;
import justfatlard.couch_controls.input.Binds;
import justfatlard.couch_controls.input.ControlNames;
import justfatlard.couch_controls.input.Gamepad;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Typing from the pad, into whatever text the screen has focused: chat, an anvil's name, a book,
 * a command block. Keys reach the screen as {@code charTyped} and {@code keyPressed}, the path a
 * keyboard's take, so no text widget needs to know the pad exists. A sign is the one screen
 * that is all text with no box to click into.
 *
 * <p>Drawn over the screen and never a screen itself: opening one would take the text's own
 * screen down with it.
 */
public final class ScreenKeyboard {
	private ScreenKeyboard() {}

	private enum Action { CHAR, SHIFT, PAGE, SPACE, DELETE, ENTER }

	/** {@code x} and {@code width} in key units; a row is ten units wide. */
	private record Key(String label, Action action, int x, int width) {
		boolean covers(double unit) {
			return unit >= x && unit < x + width;
		}
	}

	private static final int ROW_UNITS = 10;
	private static final List<List<Key>> LETTERS = List.of(
		chars("1234567890"),
		chars("qwertyuiop"),
		chars("asdfghjkl/"),
		row(new Key("Aa", Action.SHIFT, 0, 1), chars("zxcvbnm,.", 1)),
		bottom("?123"));
	private static final List<List<Key>> SYMBOLS = List.of(
		chars("1234567890"),
		chars("!@#$%^&*()"),
		chars("-_=+[]{};:"),
		chars("\"'<>\\|~`?."),
		bottom("abc"));

	private static List<Key> chars(String keys) {
		return chars(keys, 0);
	}

	private static List<Key> chars(String keys, int from) {
		Key[] row = new Key[keys.length()];
		for (int i = 0; i < keys.length(); i++) row[i] = new Key(keys.substring(i, i + 1), Action.CHAR, from + i, 1);
		return List.of(row);
	}

	private static List<Key> row(Key first, List<Key> rest) {
		Key[] row = new Key[rest.size() + 1];
		row[0] = first;
		for (int i = 0; i < rest.size(); i++) row[i + 1] = rest.get(i);
		return List.of(row);
	}

	private static List<Key> bottom(String pageLabel) {
		return List.of(
			new Key(pageLabel, Action.PAGE, 0, 2),
			new Key("Space", Action.SPACE, 2, 4),
			new Key("Delete", Action.DELETE, 6, 2),
			new Key("Enter", Action.ENTER, 8, 2));
	}

	private static final int KEY_WIDTH = 20;
	private static final int KEY_HEIGHT = 16;
	private static final int GAP = 2;
	private static final int PADDING = 4;
	private static final int MARGIN = 4;
	private static final int LEGEND_LINE = 11;

	private static final int PANEL = 0xE0101010;
	private static final int KEY = 0xFF3A3A3A;
	private static final int KEY_SELECTED = 0xFF7A7A7A;
	private static final int OUTLINE_SELECTED = 0xFFFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_ON = 0xFFFFFF55;
	private static final int LEGEND = 0xFFA0A0A0;

	/** Like the navigator's, so a held direction feels the same on the keyboard as off it. */
	private static final float STEP_THRESHOLD = 0.5f;
	private static final float STEP_RELEASE = 0.4f;
	private static final float REPEAT_DELAY_SECONDS = 0.35f;
	private static final float REPEAT_INTERVAL_SECONDS = 0.11f;
	/** Faster than stepping: a held delete should clear a line in a moment. */
	private static final float DELETE_REPEAT_INTERVAL_SECONDS = 0.05f;

	/** The screen being typed into; null while closed. */
	private static Screen on;
	private static List<List<Key>> page = LETTERS;
	private static int row;
	private static int column;
	/** For one letter, as on a phone. */
	private static boolean shifted;

	private static float stepCooldown;
	private static boolean stepping;
	private static float deleteCooldown;

	private static final Set<Screen> drawnOn = Collections.newSetFromMap(new WeakHashMap<>());

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			// Init runs again on every resize; one drawing per screen is enough.
			if (drawnOn.add(screen)) ScreenEvents.afterExtract(screen).register(ScreenKeyboard::draw);
		});
	}

	/** After the pad clicks at {@code x, y}: open if that click put the screen into text. */
	public static void clicked(Screen screen, double x, double y) {
		if (screen instanceof AbstractSignEditScreen) {
			if (!overWidget(screen, x, y)) open(screen);
			return;
		}
		if (focusedText(screen) instanceof GuiEventListener text && text.isMouseOver(x, y)) open(screen);
	}

	/** A screen that is text through and through, opened while the pad is in hand. */
	public static void screenOpened(Screen screen) {
		if (screen instanceof AbstractSignEditScreen) open(screen);
	}

	private static void open(Screen screen) {
		on = screen;
		page = LETTERS;
		row = 1;
		column = 0;
		shifted = false;
		stepCooldown = 0f;
		stepping = false;
		deleteCooldown = 0f;
	}

	public static void close() {
		on = null;
	}

	/** True when the frame's pad input belongs to the keyboard rather than the navigator. */
	public static boolean onFrame(Gamepad pad, Minecraft client, float frameSeconds) {
		if (on == null) return false;
		Screen screen = client.gui.screen();
		if (screen != on || !typing(screen)) {
			close();
			return false;
		}

		if (pad.justPressed(Binds.KEYBOARD_CLOSE)) {
			close();
			return true;
		}

		step(pad, frameSeconds);
		if (pad.justPressed(Binds.TYPE)) press(screen, page.get(row).get(column));
		if (pad.justPressed(Binds.SPACE)) type(screen, " ");
		if (pad.justPressed(Binds.SHIFT)) shifted = !shifted;
		if (pad.justPressed(Binds.CURSOR_LEFT)) key(screen, InputConstants.KEY_LEFT);
		if (pad.justPressed(Binds.CURSOR_RIGHT)) key(screen, InputConstants.KEY_RIGHT);
		repeatDelete(pad, screen, frameSeconds);
		// Last: Enter in chat sends and closes the screen, and nothing should follow it there.
		if (pad.justPressed(Binds.ENTER)) key(screen, InputConstants.KEY_RETURN);
		return true;
	}

	private static void press(Screen screen, Key key) {
		switch (key.action()) {
			case CHAR -> type(screen, shifted ? key.label().toUpperCase() : key.label());
			case SHIFT -> shifted = !shifted;
			case PAGE -> turnPage();
			case SPACE -> type(screen, " ");
			case DELETE -> key(screen, InputConstants.KEY_BACKSPACE);
			case ENTER -> key(screen, InputConstants.KEY_RETURN);
		}
	}

	private static void type(Screen screen, String text) {
		text.codePoints().forEach(codepoint -> screen.charTyped(new CharacterEvent(codepoint)));
		if (!text.isBlank()) shifted = false;
	}

	private static void key(Screen screen, int key) {
		screen.keyPressed(new KeyEvent(key, 0, 0));
	}

	private static void turnPage() {
		double center = page.get(row).get(column).x() + page.get(row).get(column).width() / 2.0;
		page = page == LETTERS ? SYMBOLS : LETTERS;
		column = keyAt(page.get(row), center);
	}

	private static void repeatDelete(Gamepad pad, Screen screen, float frameSeconds) {
		if (!pad.isDown(Binds.DELETE)) return;
		if (pad.justPressed(Binds.DELETE)) {
			key(screen, InputConstants.KEY_BACKSPACE);
			deleteCooldown = REPEAT_DELAY_SECONDS;
			return;
		}
		deleteCooldown -= frameSeconds;
		if (deleteCooldown > 0f) return;
		deleteCooldown = DELETE_REPEAT_INTERVAL_SECONDS;
		key(screen, InputConstants.KEY_BACKSPACE);
	}

	private static void step(Gamepad pad, float frameSeconds) {
		float reach = stepping ? STEP_RELEASE : STEP_THRESHOLD;
		int dx = 0;
		int dy = 0;
		if (pad.isDown(Binds.NAV_LEFT) || pad.leftX() <= -reach) dx--;
		if (pad.isDown(Binds.NAV_RIGHT) || pad.leftX() >= reach) dx++;
		if (pad.isDown(Binds.NAV_UP) || pad.leftY() <= -reach) dy--;
		if (pad.isDown(Binds.NAV_DOWN) || pad.leftY() >= reach) dy++;

		if (dx == 0 && dy == 0) {
			stepCooldown = 0f;
			stepping = false;
			return;
		}
		if (stepCooldown > 0f) {
			stepCooldown -= frameSeconds;
			return;
		}
		stepCooldown = stepping ? REPEAT_INTERVAL_SECONDS : REPEAT_DELAY_SECONDS;
		stepping = true;

		// Across the edges, as a phone's does: the far side is one step, not nine.
		List<Key> keys = page.get(row);
		if (dx != 0) column = Math.floorMod(column + dx, keys.size());
		if (dy != 0) {
			Key from = keys.get(column);
			row = Math.floorMod(row + dy, page.size());
			column = keyAt(page.get(row), from.x() + from.width() / 2.0);
		}
	}

	/** The key in {@code keys} under a point along the row, in key units. */
	private static int keyAt(List<Key> keys, double unit) {
		for (int i = 0; i < keys.size(); i++) {
			if (keys.get(i).covers(unit)) return i;
		}
		return keys.size() - 1;
	}

	private static boolean typing(Screen screen) {
		return screen instanceof AbstractSignEditScreen || focusedText(screen) != null;
	}

	/** The focused text box, or null. Focus nests: a list's row holds the box, not the screen. */
	private static GuiEventListener focusedText(Screen screen) {
		GuiEventListener focused = screen.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
			focused = container.getFocused();
		}
		if (focused instanceof EditBox box) return box.canConsumeInput() ? box : null;
		return focused instanceof MultiLineEditBox ? focused : null;
	}

	private static boolean overWidget(Screen screen, double x, double y) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(x, y)) return true;
		}
		return false;
	}

	private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		if (screen != on) return;
		Font font = Minecraft.getInstance().font;

		String[] legend = legend(Driver.controlNames());
		int gridWidth = ROW_UNITS * (KEY_WIDTH + GAP) - GAP;
		int legendWidth = 0;
		for (String line : legend) legendWidth = Math.max(legendWidth, font.width(line));
		int width = Math.max(gridWidth, legendWidth) + 2 * PADDING;
		int height = page.size() * (KEY_HEIGHT + GAP) - GAP + legend.length * LEGEND_LINE + 2 * PADDING + 2;

		int left = (graphics.guiWidth() - width) / 2;
		int top = belowMiddle(screen, graphics.guiHeight()) ? MARGIN : graphics.guiHeight() - height - MARGIN;

		graphics.nextStratum();
		graphics.fill(left, top, left + width, top + height, PANEL);

		int gridLeft = left + (width - gridWidth) / 2;
		for (int r = 0; r < page.size(); r++) {
			int y = top + PADDING + r * (KEY_HEIGHT + GAP);
			for (int c = 0; c < page.get(r).size(); c++) {
				Key key = page.get(r).get(c);
				int x = gridLeft + key.x() * (KEY_WIDTH + GAP);
				int w = key.width() * (KEY_WIDTH + GAP) - GAP;
				boolean selected = r == row && c == column;
				graphics.fill(x, y, x + w, y + KEY_HEIGHT, selected ? KEY_SELECTED : KEY);
				if (selected) graphics.outline(x, y, w, KEY_HEIGHT, OUTLINE_SELECTED);
				String label = key.action() == Action.CHAR && shifted ? key.label().toUpperCase() : key.label();
				int color = key.action() == Action.SHIFT && shifted ? TEXT_ON : TEXT;
				graphics.centeredText(font, label, x + w / 2, y + (KEY_HEIGHT - 8) / 2, color);
			}
		}

		int legendTop = top + PADDING + page.size() * (KEY_HEIGHT + GAP) + 2;
		for (int i = 0; i < legend.length; i++) {
			graphics.centeredText(font, legend[i], left + width / 2, legendTop + i * LEGEND_LINE, LEGEND);
		}
	}

	/** Whether the text sits low, so the keyboard goes above it rather than over it. */
	private static boolean belowMiddle(Screen screen, int guiHeight) {
		return focusedText(screen) instanceof AbstractWidget text && text.getY() + text.getHeight() / 2 > guiHeight / 2;
	}

	private static String[] legend(ControlNames names) {
		return new String[] {
			names.label(Binds.TYPE) + " type   " + names.label(Binds.DELETE) + " delete   "
				+ names.label(Binds.SPACE) + " space   " + names.label(Binds.SHIFT) + " shift",
			names.label(Binds.CURSOR_LEFT) + " / " + names.label(Binds.CURSOR_RIGHT) + " move   "
				+ names.label(Binds.ENTER) + " enter   " + names.label(Binds.KEYBOARD_CLOSE) + " close"
		};
	}
}
