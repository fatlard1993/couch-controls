package justfatlard.couch_controls.ui;

/**
 * Somewhere the cursor can land, in GUI coordinates.
 *
 * <p>Geometry and nothing else — no activate hook. A target is reached by
 * moving the pointer onto it and clicking through the screen's own mouse
 * path, so vanilla slots, vanilla widgets and Pandorical components all
 * behave identically once collected, and none of them need to know a gamepad
 * was involved.
 */
public record NavTarget(int centerX, int centerY) {
	public static NavTarget ofBounds(int x, int y, int width, int height) {
		return new NavTarget(x + width / 2, y + height / 2);
	}
}
