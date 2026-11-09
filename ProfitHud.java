package com.donutprofit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the profit panel on the HUD. */
public final class ProfitHud {

	// Colours are ARGB.
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int GRAY = 0xFFAAAAAA;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GOLD = 0xFFFFAA00;
	private static final int BACKGROUND = 0x90000000;

	private static final int PADDING = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int COLUMN_GAP = 12;
	private static final String TITLE = "DonutSMP Profit";

	/** One line of the panel: a label on the left, a coloured value on the right. */
	record Row(String label, String value, int color) { }

	private ProfitHud() {
	}

	/**
	 * The Gained / Lost / Up-or-Down rows.
	 * Up -> green "+$..." | Down -> red "-$..." | exactly even -> gray "$0".
	 */
	static List<Row> buildRows(BigDecimal gained, BigDecimal lost, boolean abbreviate) {
		BigDecimal net = gained.subtract(lost);
		List<Row> rows = new ArrayList<>();
		rows.add(new Row("Gained", MoneyFormat.signed(gained, abbreviate), gained.signum() > 0 ? GREEN : GRAY));
		rows.add(new Row("Lost", MoneyFormat.signed(lost.negate(), abbreviate), lost.signum() > 0 ? RED : GRAY));
		if (net.signum() > 0) {
			rows.add(new Row("Up", MoneyFormat.signed(net, abbreviate), GREEN));
		} else if (net.signum() < 0) {
			rows.add(new Row("Down", MoneyFormat.signed(net, abbreviate), RED));
		} else {
			rows.add(new Row("Even", MoneyFormat.format(net, abbreviate), GRAY));
		}
		return rows;
	}

	public static void render(GuiGraphicsExtractor graphics) {
		ProfitConfig config = DonutProfitClient.config;
		ProfitTracker tracker = DonutProfitClient.tracker;
		if (config == null || tracker == null || !config.hudEnabled) {
			return;
		}

		var font = Minecraft.getInstance().font;
		boolean abbreviate = config.abbreviateAmounts;
		List<Row> rows = buildRows(tracker.gained(), tracker.lost(), abbreviate);

		String lastLine = null;
		int lastColor = GRAY;
		PaymentParser.Payment last = tracker.last();
		if (config.showLastPayment && last != null) {
			boolean in = last.direction() == PaymentParser.Direction.RECEIVED;
			lastLine = "Last: " + (in ? "+" : "-") + MoneyFormat.format(last.amount(), abbreviate)
				+ (in ? " from " : " to ") + last.player();
			lastColor = in ? GREEN : RED;
		}

		// Work out the size of the panel.
		int labelWidth = 0;
		int valueWidth = 0;
		for (Row row : rows) {
			labelWidth = Math.max(labelWidth, font.width(row.label()));
			valueWidth = Math.max(valueWidth, font.width(row.value()));
		}
		int contentWidth = Math.max(font.width(TITLE), labelWidth + COLUMN_GAP + valueWidth);
		if (lastLine != null) {
			contentWidth = Math.max(contentWidth, font.width(lastLine));
		}
		int width = contentWidth + PADDING * 2;
		int lineCount = 1 + rows.size() + (lastLine != null ? 1 : 0);
		int height = lineCount * LINE_HEIGHT + PADDING * 2 - 1;

		int x = config.hudX;
		int y = config.hudY;
		if (config.hudBackground) {
			graphics.fill(x, y, x + width, y + height, BACKGROUND);
		}

		int textY = y + PADDING;
		graphics.text(font, TITLE, x + PADDING, textY, GOLD, true);
		textY += LINE_HEIGHT;
		for (Row row : rows) {
			graphics.text(font, row.label(), x + PADDING, textY, WHITE, true);
			graphics.text(font, row.value(), x + width - PADDING - font.width(row.value()), textY, row.color(), true);
			textY += LINE_HEIGHT;
		}
		if (lastLine != null) {
			graphics.text(font, lastLine, x + PADDING, textY, lastColor, true);
		}
	}
}
