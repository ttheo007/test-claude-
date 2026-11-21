package io.github.ttheo007.lowendboost.core;

/**
 * Zielwerte einer Voreinstellung. Reine Daten ohne Minecraft-Klassen, damit die Logik
 * auch ausserhalb des Spiels getestet werden kann.
 *
 * @param renderDistance       Sichtweite in Chunks
 * @param simulationDistance   Simulationsdistanz in Chunks (Vanilla-Minimum: 5)
 * @param cloudsOff            true = Wolken ausschalten
 * @param particleLevel        0 = alle, 1 = reduziert, 2 = minimal
 * @param ambientOcclusion     weiche Beleuchtung
 * @param biomeBlend           Biomuebergang-Radius (0 = aus)
 * @param entityDistance       Entity-Sichtweite als Faktor (1.0 = 100 %)
 * @param entityShadows        Schatten unter Entities
 * @param mipmapLevels         Mipmap-Stufen (0 = aus)
 * @param chunkFadeSeconds     Einblend-Zeit neuer Chunks in Sekunden
 * @param weatherRadius        Wetter-Radius
 * @param cutoutLeaves         durchsichtige Blaetter (false = undurchsichtig, schneller)
 * @param improvedTransparency "Fabulous"-artige Transparenz
 * @param anisotropyBit        anisotrope Filterung (0 = aus)
 */
public record PresetSettings(
		int renderDistance,
		int simulationDistance,
		boolean cloudsOff,
		int particleLevel,
		boolean ambientOcclusion,
		int biomeBlend,
		double entityDistance,
		boolean entityShadows,
		int mipmapLevels,
		double chunkFadeSeconds,
		int weatherRadius,
		boolean cutoutLeaves,
		boolean improvedTransparency,
		int anisotropyBit
) {
	public static final int PARTICLES_ALL = 0;
	public static final int PARTICLES_DECREASED = 1;
	public static final int PARTICLES_MINIMAL = 2;
}
