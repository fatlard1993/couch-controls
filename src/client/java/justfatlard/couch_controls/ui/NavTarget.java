package justfatlard.couch_controls.ui;

/** Somewhere the cursor can land, in GUI-scaled coordinates. */
public record NavTarget(int centerX, int centerY) {
	public static NavTarget ofBounds(int x, int y, int width, int height) {
		return new NavTarget(x + width / 2, y + height / 2);
	}
}
