package io.github.ttheo007.lowendboost.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import io.github.ttheo007.lowendboost.core.BoostConfig;
import io.github.ttheo007.lowendboost.core.Preset;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Der Client-Befehl {@code /lowend}. Er laeuft komplett auf dem Client und sendet nichts an einen Server.
 *
 * <pre>
 * /lowend                          Status anzeigen
 * /lowend preset potato|low|balanced
 * /lowend auto on|off              automatische Sichtweite
 * /lowend range &lt;min&gt; &lt;max&gt;      Grenzen der automatischen Sichtweite
 * </pre>
 */
public final class BoostCommand {
	private BoostCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		var root = ClientCommands.literal("lowend")
				.executes(context -> status(context.getSource()));

		root.then(ClientCommands.literal("status")
				.executes(context -> status(context.getSource())));

		var preset = ClientCommands.literal("preset");

		for (Preset value : Preset.values()) {
			preset.then(ClientCommands.literal(value.id())
					.executes(context -> applyPreset(context.getSource(), value)));
		}

		root.then(preset);

		root.then(ClientCommands.literal("auto")
				.then(ClientCommands.literal("on").executes(context -> setAuto(context.getSource(), true)))
				.then(ClientCommands.literal("off").executes(context -> setAuto(context.getSource(), false))));

		root.then(ClientCommands.literal("range")
				.then(ClientCommands.argument("min", IntegerArgumentType.integer(2, 32))
						.then(ClientCommands.argument("max", IntegerArgumentType.integer(2, 32))
								.executes(context -> setRange(
										context.getSource(),
										IntegerArgumentType.getInteger(context, "min"),
										IntegerArgumentType.getInteger(context, "max"))))));

		dispatcher.register(root);
	}

	private static int status(FabricClientCommandSource source) {
		Minecraft client = source.getClient();
		BoostConfig config = LowendBoostClient.config();
		double fps = LowendBoostClient.governor().lastFps();

		source.sendFeedback(Component.translatable("lowendboost.status.title"));
		source.sendFeedback(Component.translatable("lowendboost.status.view",
				client.options.renderDistance().get(), client.options.simulationDistance().get()));

		if (fps >= 0.0) {
			source.sendFeedback(Component.translatable("lowendboost.status.fps", Math.round(fps)));
		} else {
			source.sendFeedback(Component.translatable("lowendboost.status.fps_none"));
		}

		if (config.autoRenderDistance) {
			source.sendFeedback(Component.translatable("lowendboost.status.auto_on",
					config.minRenderDistance, config.maxRenderDistance, Math.round(config.lowFps), Math.round(config.highFps)));
		} else {
			source.sendFeedback(Component.translatable("lowendboost.status.auto_off"));
		}

		source.sendFeedback(Component.translatable("lowendboost.status.presets"));
		return Command.SINGLE_SUCCESS;
	}

	private static int applyPreset(FabricClientCommandSource source, Preset preset) {
		Minecraft client = source.getClient();

		OptionApplier.apply(client, preset.settings(), true);
		// Die Sichtweite hat sich geaendert: Die Messung der Automatik beginnt neu.
		LowendBoostClient.governor().reset(System.nanoTime());

		source.sendFeedback(Component.translatable("lowendboost.preset.applied",
				preset.id(), client.options.renderDistance().get(), client.options.simulationDistance().get()));
		return Command.SINGLE_SUCCESS;
	}

	private static int setAuto(FabricClientCommandSource source, boolean enabled) {
		BoostConfig config = LowendBoostClient.config();

		config.autoRenderDistance = enabled;
		LowendBoostClient.saveConfig();
		LowendBoostClient.governor().reset(System.nanoTime());

		if (enabled) {
			source.sendFeedback(Component.translatable("lowendboost.auto.enabled",
					config.minRenderDistance, config.maxRenderDistance, Math.round(config.lowFps), Math.round(config.highFps)));
		} else {
			source.sendFeedback(Component.translatable("lowendboost.auto.disabled"));
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int setRange(FabricClientCommandSource source, int min, int max) {
		if (min > max) {
			source.sendError(Component.translatable("lowendboost.range.invalid"));
			return 0;
		}

		BoostConfig config = LowendBoostClient.config();

		config.minRenderDistance = min;
		config.maxRenderDistance = max;
		LowendBoostClient.saveConfig();

		source.sendFeedback(Component.translatable("lowendboost.range.set", config.minRenderDistance, config.maxRenderDistance));
		return Command.SINGLE_SUCCESS;
	}
}
