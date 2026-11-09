package com.donutprofit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Formats money for display: $1.25M, $950, +$3.4B ... Pure Java, no Minecraft dependencies. */
public final class MoneyFormat {

	private static final String[] UNITS = {"", "K", "M", "B", "T"};
	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

	private MoneyFormat() {
	}

	/** "$1.25M". Always positive: the caller decides about the sign. */
	public static String format(BigDecimal value, boolean abbreviate) {
		BigDecimal abs = value.abs();
		return "$" + (abbreviate ? abbreviated(abs) : full(abs));
	}

	/** "+$1.25M", "-$300" or "$0". */
	public static String signed(BigDecimal value, boolean abbreviate) {
		String text = format(value, abbreviate);
		if (value.signum() > 0) {
			return "+" + text;
		}
		if (value.signum() < 0) {
			return "-" + text;
		}
		return text;
	}

	private static String abbreviated(BigDecimal abs) {
		BigDecimal scaled = abs;
		int unit = 0;
		while (unit < UNITS.length - 1 && scaled.compareTo(THOUSAND) >= 0) {
			scaled = scaled.divide(THOUSAND, 8, RoundingMode.HALF_UP);
			unit++;
		}
		BigDecimal rounded = scaled.setScale(2, RoundingMode.HALF_UP);
		if (rounded.compareTo(THOUSAND) >= 0 && unit < UNITS.length - 1) {
			// 999.999K rounds up to 1000.00K, which should read 1M
			rounded = rounded.divide(THOUSAND, 2, RoundingMode.HALF_UP);
			unit++;
		}
		return trimZeros(rounded) + UNITS[unit];
	}

	private static String full(BigDecimal abs) {
		String text = String.format(Locale.US, "%,.2f", abs);
		return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
	}

	private static String trimZeros(BigDecimal value) {
		String text = value.toPlainString();
		if (text.indexOf('.') >= 0) {
			text = text.replaceAll("0+$", "");
			if (text.endsWith(".")) {
				text = text.substring(0, text.length() - 1);
			}
		}
		return text;
	}
}
