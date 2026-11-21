package io.github.ttheo007.lowendboost.core;

import java.util.Locale;
import java.util.Optional;

/** Fertige Voreinstellungen, von "sehr sparsam" bis "moderat". Alle Werte liegen in den Vanilla-Grenzen. */
public enum Preset {
	/** Maximale Entlastung fuer sehr schwache Hardware. */
	POTATO(new PresetSettings(4, 5, true, PresetSettings.PARTICLES_MINIMAL, false, 0, 0.5, false, 0, 0.0, 3, false, false, 0)),
	/** Deutlich entlastet, aber noch ansehnlich. */
	LOW(new PresetSettings(6, 6, true, PresetSettings.PARTICLES_DECREASED, false, 1, 0.75, false, 2, 0.0, 5, false, false, 0)),
	/** Mittelweg: weiche Beleuchtung und Blaetter bleiben an, der Rest ist gespart. */
	BALANCED(new PresetSettings(8, 8, true, PresetSettings.PARTICLES_DECREASED, true, 2, 1.0, false, 2, 0.0, 7, true, false, 0));

	private final PresetSettings settings;

	Preset(PresetSettings settings) {
		this.settings = settings;
	}

	public PresetSettings settings() {
		return settings;
	}

	/** Kleingeschriebener Name, wie er im Befehl und in der Konfiguration steht. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<Preset> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}

		String wanted = id.trim().toLowerCase(Locale.ROOT);

		for (Preset preset : values()) {
			if (preset.id().equals(wanted)) {
				return Optional.of(preset);
			}
		}

		return Optional.empty();
	}
}
