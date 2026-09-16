package justfatlard.couch_controls.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.font.ActiveArea;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/**
 * Every clickable run of text in the open chat, where the chat draws it: a tp request's accept, a
 * trade's, a link. The chat hands its lines to a collector for clicks the same way it does for
 * drawing, so this is the chat's own layout, scroll and scale, not a copy of them.
 */
final class ChatLinks implements ActiveTextCollector {
	private final Font font;
	private final List<NavTarget> into;
	private Parameters defaults = new Parameters(new Matrix3x2f());

	private ChatLinks(Font font, List<NavTarget> into) {
		this.font = font;
		this.into = into;
	}

	static void collect(Minecraft client, List<NavTarget> into) {
		client.gui.hud.getChat().captureClickableText(new ChatLinks(client.font, into),
			client.getWindow().getGuiScaledHeight(), client.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
	}

	@Override
	public Parameters defaultParameters() {
		return defaults;
	}

	@Override
	public void defaultParameters(Parameters parameters) {
		defaults = parameters;
	}

	@Override
	public void accept(TextAlignment alignment, int anchorX, int y, Parameters parameters, FormattedCharSequence text) {
		int left = alignment.calculateLeft(anchorX, font, text);
		GuiTextRenderState line = new GuiTextRenderState(font, text, parameters.pose(), left, y,
			ARGB.white(parameters.opacity()), 0, true, true, parameters.scissor());
		Runs runs = new Runs(parameters.pose(), parameters.scissor());
		line.ensurePrepared().visit(runs);
		runs.end();
	}

	@Override
	public void acceptScrolling(Component message, int centerX, int left, int right, int top, int bottom, Parameters parameters) {
		defaultScrollingHelper(message, centerX, left, right, top, bottom, font.width(message), font.lineHeight, parameters);
	}

	/** Glyphs sharing one click, joined into the run a player would call a link. */
	private final class Runs implements Font.GlyphVisitor {
		private final Matrix3x2fc pose;
		private final ScreenRectangle scissor;
		private ClickEvent click;
		private float left, top, right, bottom;

		Runs(Matrix3x2fc pose, ScreenRectangle scissor) {
			this.pose = pose;
			this.scissor = scissor;
		}

		@Override
		public void acceptGlyph(TextRenderable.Styled glyph) {
			take(glyph);
		}

		@Override
		public void acceptEmptyArea(EmptyArea empty) {
			take(empty);
		}

		private void take(ActiveArea area) {
			ClickEvent here = area.style().getClickEvent();
			if (here != click) {
				end();
				click = here;
				left = area.activeLeft();
				top = area.activeTop();
			}
			right = area.activeRight();
			bottom = area.activeBottom();
		}

		void end() {
			if (click != null) {
				Vector2f centre = pose.transformPosition(new Vector2f((left + right) / 2F, (top + bottom) / 2F));
				int x = Math.round(centre.x);
				int y = Math.round(centre.y);
				if (scissor == null || scissor.containsPoint(x, y)) into.add(new NavTarget(x, y));
			}
			click = null;
		}
	}
}
