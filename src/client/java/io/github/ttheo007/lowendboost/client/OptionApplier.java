package io.github.ttheo007.lowendboost.client;

import io.github.ttheo007.lowendboost.core.PresetSettings;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ParticleStatus;

/**
 * Schreibt Werte in die Vanilla-Optionen. Jeder Wert wird vorher auf den gueltigen Bereich der
 * jeweiligen Option begrenzt (dieselbe Abfrage nutzt auch Sodium), damit Minecraft nie einen
 * ungueltigen Wert zurueckweisen muss.
 */
public final class OptionApplier {
	private OptionApplier() {
	}

	/**
	 * Wendet eine Voreinstellung an und speichert die options.txt.
	 *
	 * @param reloadTexturesNow true = geaenderte Mipmap-Stufen sofort uebernehmen (die Texturen werden dann neu
	 *                          geladen); false = die Stufe gilt ab dem naechsten Spielstart
	 */
	public static void apply(Minecraft client, PresetSettings settings, boolean reloadTexturesNow) {
		var options = client.options;
		int mipmapsBefore = options.mipmapLevels().get();

		setInt(options.renderDistance(), settings.renderDistance());
		setInt(options.simulationDistance(), settings.simulationDistance());

		if (settings.cloudsOff()) {
			options.cloudStatus().set(CloudStatus.OFF);
		}

		options.particles().set(particleStatus(settings.particleLevel()));
		options.ambientOcclusion().set(settings.ambientOcclusion());
		setInt(options.biomeBlendRadius(), settings.biomeBlend());
		setDouble(options.entityDistanceScaling(), settings.entityDistance());
		options.entityShadows().set(settings.entityShadows());
		setInt(options.mipmapLevels(), settings.mipmapLevels());
		setDouble(options.chunkSectionFadeInTime(), settings.chunkFadeSeconds());
		setInt(options.weatherRadius(), settings.weatherRadius());
		options.cutoutLeaves().set(settings.cutoutLeaves());
		options.improvedTransparency().set(settings.improvedTransparency());
		setInt(options.maxAnisotropyBit(), settings.anisotropyBit());

		options.save();

		int mipmapsAfter = options.mipmapLevels().get();

		if (reloadTexturesNow && mipmapsAfter != mipmapsBefore) {
			client.updateMaxMipLevel(mipmapsAfter);
			client.delayTextureReload();
		}
	}

	/** Setzt nur die Sichtweite (fuer die automatische Anpassung) und speichert die options.txt. */
	public static void setRenderDistance(Minecraft client, int distance) {
		setInt(client.options.renderDistance(), distance);
		client.options.save();
	}

	/**
	 * Waehlt den Partikel-Modus ueber den Namen der Konstante. Fehlt der Name, gilt die Reihenfolge
	 * (alle, reduziert, minimal). So kompiliert der Code unabhaengig von den genauen Namen.
	 */
	private static ParticleStatus particleStatus(int level) {
		String wanted = switch (level) {
			case PresetSettings.PARTICLES_MINIMAL -> "MINIMAL";
			case PresetSettings.PARTICLES_DECREASED -> "DECREASED";
			default -> "ALL";
		};

		ParticleStatus[] all = ParticleStatus.values();

		for (ParticleStatus status : all) {
			if (status.name().equals(wanted)) {
				return status;
			}
		}

		return all[Math.max(0, Math.min(level, all.length - 1))];
	}

	private static void setInt(OptionInstance<Integer> option, int wanted) {
		int value = wanted;

		if (option.values() instanceof OptionInstance.SliderableValueSet<Integer> range) {
			int min = range.fromSliderValue(0.0);
			int max = range.fromSliderValue(1.0);
			value = Math.max(min, Math.min(max, wanted));
		}

		option.set(value);
	}

	private static void setDouble(OptionInstance<Double> option, double wanted) {
		double value = wanted;

		if (option.values() instanceof OptionInstance.SliderableValueSet<Double> range) {
			double min = range.fromSliderValue(0.0);
			double max = range.fromSliderValue(1.0);
			value = Math.max(min, Math.min(max, wanted));
		}

		option.set(value);
	}
}
