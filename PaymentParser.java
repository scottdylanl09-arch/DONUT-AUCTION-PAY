package com.donutprofit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns one chat line into a {@link Payment}, or nothing if the line is not a payment.
 *
 * <p>This class has no Minecraft dependencies on purpose, so it is easy to test.
 *
 * <p>Patterns must match the <b>whole</b> line. That keeps other players from faking a payment in public
 * chat: if someone types "Steve paid you $1M", the chat line starts with their name or rank, so it does not
 * match.
 */
public final class PaymentParser {

	public enum Direction { RECEIVED, SENT }

	/** One detected payment. {@code player} is the other side of the payment. */
	public record Payment(Direction direction, String player, BigDecimal amount) { }

	// A player name. A leading dot is allowed because Bedrock players show up as ".Name".
	private static final String NAME = "(?<name>\\.?\\w{1,32})";

	// "$5", "$ 5", "$1,500", "$250.50", "$2.5K", "$ 25M" ...  K = thousand, M = million, B = billion, T = trillion
	private static final String AMOUNT = "\\$\\s?(?<amount>\\d[\\d,]*(?:\\.\\d+)?[KMBT]?)";

	private static final String END = "[.!]?";

	/** Lines where money comes to you. Written to config/donutprofit.json so you can edit them. */
	public static final List<String> DEFAULT_RECEIVED = List.of(
		NAME + " paid you " + AMOUNT + END,
		"You were paid " + AMOUNT + " by " + NAME + END
	);

	/** Lines where money leaves you. */
	public static final List<String> DEFAULT_SENT = List.of(
		"You paid " + NAME + " " + AMOUNT + END
	);

	private final List<Pattern> received;
	private final List<Pattern> sent;
	private final List<String> problems = new ArrayList<>();

	public PaymentParser(List<String> receivedPatterns, List<String> sentPatterns) {
		this.received = compile(receivedPatterns, DEFAULT_RECEIVED);
		this.sent = compile(sentPatterns, DEFAULT_SENT);
	}

	/** Patterns from the config file that could not be compiled (they are skipped). */
	public List<String> problems() {
		return problems;
	}

	private List<Pattern> compile(List<String> sources, List<String> fallback) {
		List<Pattern> out = new ArrayList<>();
		if (sources != null) {
			for (String source : sources) {
				if (source == null) {
					continue;
				}
				try {
					out.add(Pattern.compile(source, Pattern.CASE_INSENSITIVE));
				} catch (PatternSyntaxException e) {
					problems.add("Ignoring a bad pattern in the config: " + source + " (" + e.getDescription() + ")");
				}
			}
		}
		if (out.isEmpty()) {
			for (String source : fallback) {
				out.add(Pattern.compile(source, Pattern.CASE_INSENSITIVE));
			}
		}
		return List.copyOf(out);
	}

	/** Returns the payment described by this chat line, if it is one. */
	public Optional<Payment> parse(String rawMessage) {
		String text = normalize(rawMessage);
		if (text.isEmpty()) {
			return Optional.empty();
		}
		Optional<Payment> hit = match(text, received, Direction.RECEIVED);
		return hit.isPresent() ? hit : match(text, sent, Direction.SENT);
	}

	private static Optional<Payment> match(String text, List<Pattern> patterns, Direction direction) {
		for (Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(text);
			if (!matcher.matches()) {
				continue;
			}
			BigDecimal amount = parseAmount(group(matcher, "amount"));
			if (amount == null || amount.signum() <= 0) {
				continue;
			}
			String name = group(matcher, "name");
			return Optional.of(new Payment(direction, name == null ? "?" : name, amount));
		}
		return Optional.empty();
	}

	private static String group(Matcher matcher, String name) {
		try {
			return matcher.group(name);
		} catch (IllegalArgumentException e) {
			return null; // the pattern has no group with this name
		}
	}

	/**
	 * Cleans a chat line before matching: removes colour codes, invisible characters and icon glyphs,
	 * and squeezes whitespace.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String text = raw
			.replaceAll("\u00a7.", "")                          // legacy colour codes (section sign + one character)
			.replaceAll("[\\u200B-\\u200F\\u2060\\uFEFF]", "")    // zero-width characters
			.replaceAll("[\\uE000-\\uF8FF]", " ")                // private-use glyphs that servers use as icons
			.replace('\u00a0', ' ')
			.replaceAll("\\s+", " ")
			.trim();
		return text.replaceFirst("^\\.\\s+", "");               // a lone leading "." used as a notification marker
	}

	/** "1,500" -> 1500, "2.5K" -> 2500, "7.7M" -> 7700000. Returns null if it is not a number. */
	public static BigDecimal parseAmount(String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.replace(",", "").replace(" ", "");
		if (text.isEmpty()) {
			return null;
		}
		BigDecimal multiplier = BigDecimal.ONE;
		char last = Character.toUpperCase(text.charAt(text.length() - 1));
		if (Character.isLetter(last)) {
			switch (last) {
				case 'K' -> multiplier = new BigDecimal("1000");
				case 'M' -> multiplier = new BigDecimal("1000000");
				case 'B' -> multiplier = new BigDecimal("1000000000");
				case 'T' -> multiplier = new BigDecimal("1000000000000");
				default -> {
					return null;
				}
			}
			text = text.substring(0, text.length() - 1);
		}
		try {
			return new BigDecimal(text).multiply(multiplier);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
