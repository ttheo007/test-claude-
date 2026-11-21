package io.github.ttheo007.lowendboost.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Einstellungen der Mod, gespeichert als einfache .properties-Datei. */
public final class BoostConfig {
	private static final int MAX_DISTANCE_LIMIT = 32;

	/** Wurde die Start-Voreinstellung schon einmal angewendet? */
	public boolean firstStartDone = false;
	/** Voreinstellung fuer den allerersten Start; {@code null} bedeutet "nichts anwenden" ("none" in der Datei). */
	public Preset firstStartPreset = Preset.POTATO;
	/** Automatische Sichtweite (standardmaessig aus, weil jede Aenderung das Sichtfeld neu aufbaut). */
	public boolean autoRenderDistance = false;
	public int minRenderDistance = 3;
	public int maxRenderDistance = 8;
	/** Faellt die Bildrate dauerhaft unter diesen Wert, wird die Sichtweite verkleinert. */
	public double lowFps = 24.0;
	/** Liegt die Bildrate dauerhaft ueber diesem Wert, darf die Sichtweite wieder wachsen. */
	public double highFps = 50.0;
	/** Hinweise der automatischen Sichtweite im Chat anzeigen. */
	public boolean chatMessages = true;

	public AdaptiveGovernor.Params params() {
		return new AdaptiveGovernor.Params(lowFps, highFps, minRenderDistance, maxRenderDistance);
	}

	/** Bringt alle Werte in sinnvolle Grenzen, damit eine falsch editierte Datei nichts kaputt macht. */
	public void normalize() {
		minRenderDistance = clamp(minRenderDistance, 2, MAX_DISTANCE_LIMIT);
		maxRenderDistance = clamp(maxRenderDistance, minRenderDistance, MAX_DISTANCE_LIMIT);
		lowFps = clamp(lowFps, 5.0, 240.0);
		highFps = clamp(highFps, lowFps + 5.0, 300.0);
	}

	/** Liest die Datei. Fehlt sie oder ist sie kaputt, gelten die Standardwerte. */
	public static BoostConfig load(Path file) {
		BoostConfig config = new BoostConfig();

		if (Files.isRegularFile(file)) {
			Properties props = new Properties();

			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				props.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				return new BoostConfig();
			}

			config.firstStartDone = readBoolean(props, "firstStartDone", config.firstStartDone);
			config.firstStartPreset = readPreset(props, "firstStartPreset", config.firstStartPreset);
			config.autoRenderDistance = readBoolean(props, "autoRenderDistance", config.autoRenderDistance);
			config.minRenderDistance = readInt(props, "minRenderDistance", config.minRenderDistance);
			config.maxRenderDistance = readInt(props, "maxRenderDistance", config.maxRenderDistance);
			config.lowFps = readDouble(props, "lowFps", config.lowFps);
			config.highFps = readDouble(props, "highFps", config.highFps);
			config.chatMessages = readBoolean(props, "chatMessages", config.chatMessages);
		}

		config.normalize();
		return config;
	}

	public void save(Path file) throws IOException {
		normalize();

		Properties props = new Properties();
		props.setProperty("firstStartDone", Boolean.toString(firstStartDone));
		props.setProperty("firstStartPreset", firstStartPreset == null ? "none" : firstStartPreset.id());
		props.setProperty("autoRenderDistance", Boolean.toString(autoRenderDistance));
		props.setProperty("minRenderDistance", Integer.toString(minRenderDistance));
		props.setProperty("maxRenderDistance", Integer.toString(maxRenderDistance));
		props.setProperty("lowFps", Double.toString(lowFps));
		props.setProperty("highFps", Double.toString(highFps));
		props.setProperty("chatMessages", Boolean.toString(chatMessages));

		Path parent = file.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			props.store(writer, "Lowend Boost - firstStartPreset: potato, low, balanced oder none");
		}
	}

	private static Preset readPreset(Properties props, String key, Preset fallback) {
		String value = props.getProperty(key);

		if (value == null) {
			return fallback;
		}

		if (value.trim().equalsIgnoreCase("none")) {
			return null;
		}

		return Preset.byId(value).orElse(fallback);
	}

	private static boolean readBoolean(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);

		if (value == null) {
			return fallback;
		}

		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "true" -> true;
			case "false" -> false;
			default -> fallback;
		};
	}

	private static int readInt(Properties props, String key, int fallback) {
		String value = props.getProperty(key);

		if (value == null) {
			return fallback;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double readDouble(Properties props, String key, double fallback) {
		String value = props.getProperty(key);

		if (value == null) {
			return fallback;
		}

		try {
			double parsed = Double.parseDouble(value.trim());
			return Double.isFinite(parsed) ? parsed : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
