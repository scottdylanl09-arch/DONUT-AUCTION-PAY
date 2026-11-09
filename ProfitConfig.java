package com.donutprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings, stored in config/donutprofit.json. The file is created on first launch.
 * Edit it while the game is closed. Backslashes in the patterns must be doubled (\\d, not \d).
 */
public final class ProfitConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Show the HUD. Toggle in game with /donutprofit hud. */
	public boolean hudEnabled = true;
	/** HUD position in GUI pixels from the top-left corner. Change in game with /donutprofit pos x y. */
	public int hudX = 6;
	public int hudY = 6;
	/** Dark box behind the HUD text. */
	public boolean hudBackground = true;
	/** Show a "Last: +$2.5M from Steve" line. */
	public boolean showLastPayment = true;
	/** true = $1.25M, false = $1,250,000. */
	public boolean abbreviateAmounts = true;
	/** true = start from zero every time the game starts. false = keep the running total until you reset it. */
	public boolean resetOnLaunch = false;
	/** Write detected payments and unmatched money lines to the game log (latest.log). */
	public boolean debugLogging = false;
	/** Chat lines where you receive money. Needs the named groups (?<name>...) and (?<amount>...). */
	public List<String> receivedPatterns = new ArrayList<>(PaymentParser.DEFAULT_RECEIVED);
	/** Chat lines where you send money. */
	public List<String> sentPatterns = new ArrayList<>(PaymentParser.DEFAULT_SENT);

	private transient Path file;

	public static ProfitConfig load(Path file) {
		ProfitConfig config = null;
		if (Files.isRegularFile(file)) {
			try {
				config = GSON.fromJson(Files.readString(file), ProfitConfig.class);
			} catch (IOException | RuntimeException e) {
				DonutProfitClient.LOGGER.warn("Could not read {} ({}). Using defaults.", file, e.toString());
			}
		}
		if (config == null) {
			config = new ProfitConfig();
		}
		config.file = file;
		config.save(); // writes the defaults on first launch and adds any option that is new in this version
		return config;
	}

	public void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this));
		} catch (IOException e) {
			DonutProfitClient.LOGGER.warn("Could not write {}: {}", file, e.toString());
		}
	}
}
