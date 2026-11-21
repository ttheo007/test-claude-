package io.github.ttheo007.lowendboost.client;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ttheo007.lowendboost.core.AdaptiveGovernor;
import io.github.ttheo007.lowendboost.core.BoostConfig;
import io.github.ttheo007.lowendboost.core.Preset;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Einstieg der Mod. Sie macht drei Dinge:
 * <ol>
 *   <li>Beim allerersten Start wird einmal eine Voreinstellung in die Vanilla-Optionen geschrieben.</li>
 *   <li>Der Befehl {@code /lowend} wendet Voreinstellungen an und steuert die Automatik.</li>
 *   <li>Optional passt die Automatik die Sichtweite an die gemessene Bildrate an.</li>
 * </ol>
 */
public final class LowendBoostClient implements ClientModInitializer {
	public static final String MOD_ID = "lowendboost";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final AdaptiveGovernor GOVERNOR = new AdaptiveGovernor();

	private static Path configFile;
	private static BoostConfig config = new BoostConfig();
	/** Die zuletzt gesehene Welt; wechselt sie, beginnt die Messung von vorn. */
	private static Object lastLevel;

	@Override
	public void onInitializeClient() {
		configFile = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
		config = BoostConfig.load(configFile);
		// Datei sofort schreiben, damit sie existiert und bearbeitet werden kann.
		saveConfig();

		ClientLifecycleEvents.CLIENT_STARTED.register(LowendBoostClient::applyFirstStartPreset);
		ClientTickEvents.END_CLIENT_TICK.register(LowendBoostClient::onTick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> BoostCommand.register(dispatcher));
	}

	/** Wird vom Mixin bei jedem gerenderten Frame aufgerufen. */
	public static void onFrame() {
		GOVERNOR.onFrame(System.nanoTime());
	}

	static BoostConfig config() {
		return config;
	}

	static AdaptiveGovernor governor() {
		return GOVERNOR;
	}

	static void saveConfig() {
		try {
			config.save(configFile);
		} catch (IOException e) {
			LOGGER.warn("Konfiguration konnte nicht gespeichert werden: {}", e.toString());
		}
	}

	private static void applyFirstStartPreset(Minecraft client) {
		Preset preset = config.firstStartPreset;

		if (config.firstStartDone || preset == null) {
			return;
		}

		try {
			OptionApplier.apply(client, preset.settings(), false);
		} catch (RuntimeException e) {
			// Nicht abstuerzen: Die Mod ist nur eine Hilfe, das Spiel muss weiterlaufen.
			LOGGER.error("Voreinstellung '{}' konnte nicht angewendet werden", preset.id(), e);
			return;
		}

		config.firstStartDone = true;
		saveConfig();
		LOGGER.info("Voreinstellung '{}' beim ersten Start angewendet. Aendern mit /lowend preset <potato|low|balanced>.", preset.id());
	}

	private static void onTick(Minecraft client) {
		long now = System.nanoTime();
		Object level = client.level;

		if (level != lastLevel) {
			lastLevel = level;
			GOVERNOR.reset(now);
		}

		if (level == null) {
			return;
		}

		if (!config.autoRenderDistance) {
			GOVERNOR.observe(now);
			return;
		}

		try {
			adjustRenderDistance(client, now);
		} catch (RuntimeException e) {
			// Bei einem Fehler die Automatik abschalten, statt jeden Tick erneut zu scheitern.
			LOGGER.error("Automatische Sichtweite wird wegen eines Fehlers abgeschaltet", e);
			config.autoRenderDistance = false;
			saveConfig();
		}
	}

	private static void adjustRenderDistance(Minecraft client, long now) {
		int current = client.options.renderDistance().get();
		int wanted = GOVERNOR.poll(now, current, config.params());

		if (wanted == current) {
			return;
		}

		OptionApplier.setRenderDistance(client, wanted);
		int applied = client.options.renderDistance().get();

		if (!config.chatMessages || client.player == null || applied == current) {
			return;
		}

		if (current > config.maxRenderDistance) {
			client.player.sendSystemMessage(Component.translatable("lowendboost.auto.capped", applied));
			return;
		}

		String key = applied < current ? "lowendboost.auto.shrunk" : "lowendboost.auto.grown";
		client.player.sendSystemMessage(Component.translatable(key, current, applied, Math.round(GOVERNOR.lastFps())));
	}
}
