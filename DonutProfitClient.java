package com.donutprofit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DonutSMP Profit Tracker (client side only).
 *
 * <p>Reads the chat lines the server already sends you ("Steve paid you $5K", "You paid Steve $5K"),
 * adds them up and draws the result on the HUD. It never sends anything to the server.
 */
public final class DonutProfitClient implements ClientModInitializer {

	public static final String MOD_ID = "donutprofit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Shared with ProfitHud.
	static ProfitConfig config;
	static ProfitTracker tracker;
	static PaymentParser parser;

	// Chat lines that mention money and paying but were not recognised. Shown by /donutprofit lines.
	private static final Deque<String> UNMATCHED = new ArrayDeque<>();
	private static final int UNMATCHED_LIMIT = 10;
	private static final int MAX_LINE_LENGTH = 300;

	// Legacy chat colour codes for command feedback.
	private static final String GOLD = "\u00a76";
	private static final String GREEN = "\u00a7a";
	private static final String RED = "\u00a7c";
	private static final String GRAY = "\u00a77";

	@Override
	public void onInitializeClient() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		config = ProfitConfig.load(configDir.resolve("donutprofit.json"));

		parser = new PaymentParser(config.receivedPatterns, config.sentPatterns);
		for (String problem : parser.problems()) {
			LOGGER.warn(problem);
		}

		tracker = new ProfitTracker(configDir.resolve("donutprofit-stats.json"));
		if (config.resetOnLaunch) {
			tracker.reset();
		} else {
			tracker.load();
		}

		// Payment messages arrive as game (system) messages. The action bar ("overlay") can show the same
		// payment a second time, so overlay messages are skipped to avoid counting it twice.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handleLine(message.getString());
			}
		});

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(MOD_ID, "profit_hud"),
			(graphics, deltaTracker) -> ProfitHud.render(graphics));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));

		LOGGER.info("DonutSMP Profit Tracker loaded. Use /donutprofit in game.");
	}

	// ---------------------------------------------------------------- chat

	private static void handleLine(String raw) {
		if (raw == null || raw.length() > MAX_LINE_LENGTH) {
			return;
		}

		Optional<PaymentParser.Payment> payment = parser.parse(raw);
		if (payment.isPresent()) {
			tracker.record(payment.get());
			if (config.debugLogging) {
				LOGGER.info("Payment detected: {}", payment.get());
			}
			return;
		}

		String text = PaymentParser.normalize(raw);
		if (looksLikePaymentLine(text)) {
			synchronized (UNMATCHED) {
				if (!text.equals(UNMATCHED.peekLast())) {
					UNMATCHED.addLast(text);
					while (UNMATCHED.size() > UNMATCHED_LIMIT) {
						UNMATCHED.removeFirst();
					}
				}
			}
			if (config.debugLogging) {
				LOGGER.info("Money line not counted: {}", text);
			}
		}
	}

	private static boolean looksLikePaymentLine(String text) {
		return text.indexOf('$') >= 0 && text.toLowerCase(Locale.ROOT).contains("pay");
	}

	// ---------------------------------------------------------------- commands

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("donutprofit")
			.executes(DonutProfitClient::cmdStatus)
			.then(ClientCommands.literal("reset").executes(DonutProfitClient::cmdReset))
			.then(ClientCommands.literal("hud").executes(DonutProfitClient::cmdToggleHud))
			.then(ClientCommands.literal("pos")
				.then(ClientCommands.argument("x", IntegerArgumentType.integer(0, 10000))
					.then(ClientCommands.argument("y", IntegerArgumentType.integer(0, 10000))
						.executes(DonutProfitClient::cmdPosition))))
			.then(ClientCommands.literal("lines").executes(DonutProfitClient::cmdLines))
			.then(ClientCommands.literal("test")
				.then(ClientCommands.argument("text", StringArgumentType.greedyString())
					.executes(DonutProfitClient::cmdTest))));
	}

	private static void say(CommandContext<FabricClientCommandSource> context, String text) {
		context.getSource().sendFeedback(Component.literal(text));
	}

	private static int cmdStatus(CommandContext<FabricClientCommandSource> context) {
		boolean abbreviate = config.abbreviateAmounts;
		BigDecimal gained = tracker.gained();
		BigDecimal lost = tracker.lost();
		BigDecimal net = gained.subtract(lost);

		say(context, GOLD + "DonutSMP profit tracker");
		say(context, GREEN + "Gained: " + MoneyFormat.signed(gained, abbreviate)
			+ GRAY + " (" + tracker.receivedCount() + " payments)");
		say(context, RED + "Lost: " + MoneyFormat.signed(lost.negate(), abbreviate)
			+ GRAY + " (" + tracker.sentCount() + " payments)");
		if (net.signum() > 0) {
			say(context, GREEN + "You are up " + MoneyFormat.format(net, abbreviate));
		} else if (net.signum() < 0) {
			say(context, RED + "You are down " + MoneyFormat.format(net, abbreviate));
		} else {
			say(context, GRAY + "You are exactly even");
		}
		return 1;
	}

	private static int cmdReset(CommandContext<FabricClientCommandSource> context) {
		tracker.reset();
		say(context, GREEN + "Profit tracker reset to $0.");
		return 1;
	}

	private static int cmdToggleHud(CommandContext<FabricClientCommandSource> context) {
		config.hudEnabled = !config.hudEnabled;
		config.save();
		say(context, GRAY + "HUD " + (config.hudEnabled ? "shown." : "hidden."));
		return 1;
	}

	private static int cmdPosition(CommandContext<FabricClientCommandSource> context) {
		config.hudX = IntegerArgumentType.getInteger(context, "x");
		config.hudY = IntegerArgumentType.getInteger(context, "y");
		config.save();
		say(context, GRAY + "HUD moved to " + config.hudX + ", " + config.hudY + ".");
		return 1;
	}

	private static int cmdLines(CommandContext<FabricClientCommandSource> context) {
		List<String> lines;
		synchronized (UNMATCHED) {
			lines = new ArrayList<>(UNMATCHED);
		}
		if (lines.isEmpty()) {
			say(context, GRAY + "No unrecognised payment-looking lines so far.");
			return 1;
		}
		say(context, GOLD + "Recent lines that mention $ and pay but were not counted:");
		for (String line : lines) {
			say(context, GRAY + "- " + line);
		}
		return 1;
	}

	private static int cmdTest(CommandContext<FabricClientCommandSource> context) {
		String text = StringArgumentType.getString(context, "text");
		Optional<PaymentParser.Payment> result = parser.parse(text);
		if (result.isPresent()) {
			PaymentParser.Payment payment = result.get();
			boolean in = payment.direction() == PaymentParser.Direction.RECEIVED;
			say(context, GREEN + "Match: " + (in ? "received " : "sent ")
				+ MoneyFormat.format(payment.amount(), false) + (in ? " from " : " to ") + payment.player()
				+ GRAY + " (nothing was added to your totals)");
		} else {
			say(context, RED + "No match for: " + PaymentParser.normalize(text));
		}
		return 1;
	}
}
