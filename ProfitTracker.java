package com.donutprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/** Running totals of money gained and lost. Saved to config/donutprofit-stats.json after every payment. */
public final class ProfitTracker {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** What is written to disk. Amounts are strings so no precision is lost. */
	static final class Saved {
		String gained = "0";
		String lost = "0";
		int received;
		int sent;
	}

	private final Path file;
	private BigDecimal gained = BigDecimal.ZERO;
	private BigDecimal lost = BigDecimal.ZERO;
	private int receivedCount;
	private int sentCount;
	private PaymentParser.Payment last;

	public ProfitTracker(Path file) {
		this.file = file;
	}

	/** Loads the saved totals, if there are any. */
	public synchronized void load() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			Saved saved = GSON.fromJson(Files.readString(file), Saved.class);
			if (saved == null) {
				return;
			}
			gained = new BigDecimal(saved.gained);
			lost = new BigDecimal(saved.lost);
			receivedCount = saved.received;
			sentCount = saved.sent;
		} catch (IOException | RuntimeException e) {
			DonutProfitClient.LOGGER.warn("Could not read {} ({}). Starting from zero.", file, e.toString());
		}
	}

	public synchronized void record(PaymentParser.Payment payment) {
		if (payment.direction() == PaymentParser.Direction.RECEIVED) {
			gained = gained.add(payment.amount());
			receivedCount++;
		} else {
			lost = lost.add(payment.amount());
			sentCount++;
		}
		last = payment;
		save();
	}

	public synchronized void reset() {
		gained = BigDecimal.ZERO;
		lost = BigDecimal.ZERO;
		receivedCount = 0;
		sentCount = 0;
		last = null;
		save();
	}

	public synchronized BigDecimal gained() {
		return gained;
	}

	public synchronized BigDecimal lost() {
		return lost;
	}

	/** Positive = you are up, negative = you are down. */
	public synchronized BigDecimal net() {
		return gained.subtract(lost);
	}

	public synchronized int receivedCount() {
		return receivedCount;
	}

	public synchronized int sentCount() {
		return sentCount;
	}

	/** The most recent payment seen this session, or null. */
	public synchronized PaymentParser.Payment last() {
		return last;
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Saved saved = new Saved();
			saved.gained = gained.toPlainString();
			saved.lost = lost.toPlainString();
			saved.received = receivedCount;
			saved.sent = sentCount;
			Files.writeString(file, GSON.toJson(saved));
		} catch (IOException e) {
			DonutProfitClient.LOGGER.warn("Could not write {}: {}", file, e.toString());
		}
	}
}
