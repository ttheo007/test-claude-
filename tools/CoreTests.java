import io.github.ttheo007.lowendboost.core.AdaptiveGovernor;
import io.github.ttheo007.lowendboost.core.BoostConfig;
import io.github.ttheo007.lowendboost.core.Preset;
import io.github.ttheo007.lowendboost.core.PresetSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests fuer die reine Logik der Mod (Paket "core"). Sie brauchen weder Gradle noch Minecraft:
 * {@code tools/run-core-tests.sh} kompiliert und startet sie mit dem installierten JDK.
 */
public final class CoreTests {
	private static final long TICK_NANOS = 50_000_000L;
	private static final AdaptiveGovernor.Params PARAMS = new AdaptiveGovernor.Params(24.0, 50.0, 3, 8);

	private static int checks;
	private static int failures;

	/** Simuliert die Spielschleife: 20 Ticks pro Sekunde, dazwischen eine feste Anzahl Frames. */
	private static final class Sim {
		final AdaptiveGovernor governor = new AdaptiveGovernor();
		final AdaptiveGovernor.Params params;
		final List<Long> changes = new ArrayList<>();
		final boolean observeOnly;
		long now = 5_000_000_000L;
		int distance;
		double carry;

		Sim(int distance, AdaptiveGovernor.Params params, boolean observeOnly) {
			this.distance = distance;
			this.params = params;
			this.observeOnly = observeOnly;
			governor.reset(now);
		}

		Sim(int distance) {
			this(distance, PARAMS, false);
		}

		/** Laeuft {@code seconds} Sekunden mit der angegebenen Bildrate. */
		void run(double seconds, double fps) {
			int ticks = (int) Math.round(seconds * 20.0);

			for (int i = 0; i < ticks; i++) {
				long tickStart = now;
				now += TICK_NANOS;
				carry += fps / 20.0;
				int frames = (int) carry;
				carry -= frames;

				for (int f = 1; f <= frames; f++) {
					governor.onFrame(tickStart + TICK_NANOS * f / frames);
				}

				step();
			}
		}

		/** Laeuft mit Ticks, aber ohne dass ein einziger Frame gemeldet wird (defekter Zaehler). */
		void runWithoutFrames(double seconds) {
			int ticks = (int) Math.round(seconds * 20.0);

			for (int i = 0; i < ticks; i++) {
				now += TICK_NANOS;
				step();
			}
		}

		/** Das ganze Spiel steht {@code seconds} Sekunden still: keine Frames, keine Ticks. */
		void hitch(double seconds) {
			now += (long) (seconds * 1.0e9);
		}

		private void step() {
			if (observeOnly) {
				governor.observe(now);
				return;
			}

			int next = governor.poll(now, distance, params);

			if (next != distance) {
				changes.add(now);
				distance = next;
			}
		}

		long minGapSeconds() {
			long min = Long.MAX_VALUE;

			for (int i = 1; i < changes.size(); i++) {
				min = Math.min(min, (changes.get(i) - changes.get(i - 1)) / 1_000_000_000L);
			}

			return min;
		}
	}

	public static void main(String[] args) throws IOException {
		governorWarmupAndFirstShrink();
		governorShrinksStepwiseDownToMinimum();
		governorKeepsDistanceInsideHysteresisBand();
		governorGrowsSlowlyUpToMaximum();
		governorDiscardsMeasurementAfterHitch();
		governorIgnoresWindowsWithoutFrames();
		governorCapsAtMaximumImmediately();
		governorNeverRaisesBelowMinimum();
		governorResetRestartsWarmup();
		governorObserveOnlyMeasuresWithoutDeciding();
		governorWorksWithoutExplicitReset();
		configDefaultsRoundTripAndGarbage();
		presetsAreConsistent();

		System.out.println();
		System.out.println(checks + " Pruefungen, " + failures + " fehlgeschlagen");

		if (failures > 0) {
			System.exit(1);
		}
	}

	private static void governorWarmupAndFirstShrink() {
		Sim sim = new Sim(8);

		sim.run(17, 10);
		check(sim.distance == 8, "Aufwaermphase: nach 17 s bei 10 FPS noch keine Aenderung");

		sim.run(1, 10);
		check(sim.distance == 7, "nach Aufwaermphase und 2 langsamen Fenstern: genau -1 Chunk (bei 18 s)");
		check(sim.changes.size() == 1, "genau eine Aenderung");
	}

	private static void governorShrinksStepwiseDownToMinimum() {
		Sim sim = new Sim(8);

		sim.run(80, 10);
		check(sim.distance == 3, "bei dauerhaft 10 FPS sinkt die Sichtweite bis zum Minimum (3)");
		check(sim.changes.size() == 5, "dafuer genau 5 Einzelschritte (8 -> 3)");
		check(sim.minGapSeconds() >= 8, "zwischen zwei Verkleinerungen liegen mindestens 8 s");

		sim.run(60, 10);
		check(sim.distance == 3 && sim.changes.size() == 5, "unter dem Minimum geht es nicht weiter");
	}

	private static void governorKeepsDistanceInsideHysteresisBand() {
		Sim sim = new Sim(6);

		sim.run(200, 35);
		check(sim.changes.isEmpty() && sim.distance == 6, "zwischen den Schwellen (24..50 FPS) bleibt alles unveraendert");
	}

	private static void governorGrowsSlowlyUpToMaximum() {
		Sim sim = new Sim(4);

		sim.run(40, 100);
		check(sim.distance == 5, "nach Aufwaermphase und 10 fluessigen Fenstern: +1 Chunk");

		sim.run(160, 100);
		check(sim.distance == 8, "danach weiter in Einzelschritten bis zum Maximum (8)");
		check(sim.changes.size() == 4, "von 4 bis 8 sind es genau 4 Schritte");
		check(sim.minGapSeconds() >= 45, "zwischen zwei Vergroesserungen liegen mindestens 45 s");

		sim.run(120, 100);
		check(sim.distance == 8, "ueber dem Maximum geht es nicht weiter");
	}

	private static void governorDiscardsMeasurementAfterHitch() {
		Sim sim = new Sim(8);

		sim.run(20, 60);
		sim.hitch(3.0);
		sim.run(5, 60);

		check(sim.changes.isEmpty(), "ein einzelner Ladehaenger loest keine Aenderung aus");
		check(Math.abs(sim.governor.lastFps() - 60.0) < 6.0,
				"der Ladehaenger verfaelscht die Messung nicht (gemessen: " + sim.governor.lastFps() + " FPS)");
	}

	private static void governorIgnoresWindowsWithoutFrames() {
		Sim sim = new Sim(8);

		sim.runWithoutFrames(120);
		check(sim.changes.isEmpty() && sim.distance == 8, "ohne gezaehlte Frames wird nie verkleinert");
		check(sim.governor.lastFps() < 0.0, "ohne Frames gibt es keine Messung");
	}

	private static void governorCapsAtMaximumImmediately() {
		Sim sim = new Sim(12);

		sim.run(0.05, 60);
		check(sim.distance == 8 && sim.changes.size() == 1, "eine Sichtweite ueber dem Maximum wird sofort begrenzt");
	}

	private static void governorNeverRaisesBelowMinimum() {
		Sim sim = new Sim(2);

		sim.run(60, 10);
		check(sim.distance == 2 && sim.changes.isEmpty(), "eine Sichtweite unter dem Minimum wird nie angefasst");
	}

	private static void governorResetRestartsWarmup() {
		Sim sim = new Sim(8);

		sim.run(30, 10);
		check(sim.distance == 6, "Vorbedingung: nach 30 s bei 10 FPS ist die Sichtweite 6");

		int changesBefore = sim.changes.size();
		sim.governor.reset(sim.now);
		sim.run(13, 10);

		check(sim.changes.size() == changesBefore && sim.distance == 6, "nach reset() beginnt die Aufwaermphase von vorn");
	}

	private static void governorObserveOnlyMeasuresWithoutDeciding() {
		Sim sim = new Sim(8, PARAMS, true);

		sim.run(30, 10);
		check(sim.distance == 8, "observe() aendert nie die Sichtweite");
		check(Math.abs(sim.governor.lastFps() - 10.0) < 0.5, "observe() misst die Bildrate (" + sim.governor.lastFps() + " FPS)");
	}

	private static void governorWorksWithoutExplicitReset() {
		AdaptiveGovernor governor = new AdaptiveGovernor();
		long now = 9_000_000_000L;
		int distance = 8;

		// Kein reset(): Der Governor muss sich selbst starten und darf nicht sofort verkleinern.
		for (int i = 0; i < 40 * 5; i++) {
			now += TICK_NANOS;
			governor.onFrame(now);
			distance = governor.poll(now, distance, PARAMS);
		}

		check(distance == 8, "ohne reset() startet der Governor sicher mit Aufwaermphase");
	}

	private static void configDefaultsRoundTripAndGarbage() throws IOException {
		Path dir = Files.createTempDirectory("lowendboost-test");
		Path file = dir.resolve("lowendboost.properties");

		BoostConfig defaults = BoostConfig.load(dir.resolve("gibt-es-nicht.properties"));
		check(defaults.firstStartPreset == Preset.POTATO && !defaults.firstStartDone && !defaults.autoRenderDistance,
				"fehlende Datei: Standardwerte");
		check(defaults.minRenderDistance == 3 && defaults.maxRenderDistance == 8 && defaults.chatMessages,
				"fehlende Datei: Standard-Grenzen");

		BoostConfig custom = new BoostConfig();
		custom.firstStartDone = true;
		custom.firstStartPreset = Preset.LOW;
		custom.autoRenderDistance = true;
		custom.minRenderDistance = 4;
		custom.maxRenderDistance = 10;
		custom.lowFps = 20.0;
		custom.highFps = 60.0;
		custom.chatMessages = false;
		custom.save(file);

		BoostConfig loaded = BoostConfig.load(file);
		check(loaded.firstStartDone && loaded.firstStartPreset == Preset.LOW && loaded.autoRenderDistance
				&& loaded.minRenderDistance == 4 && loaded.maxRenderDistance == 10
				&& loaded.lowFps == 20.0 && loaded.highFps == 60.0 && !loaded.chatMessages,
				"speichern und wieder laden ergibt dieselben Werte");

		custom.firstStartPreset = null;
		custom.save(file);
		check(BoostConfig.load(file).firstStartPreset == null, "\"none\" bedeutet: keine Start-Voreinstellung");

		Files.writeString(file, "minRenderDistance=abc\nmaxRenderDistance=1000\nlowFps=NaN\nhighFps=-5\n"
				+ "firstStartPreset=banane\nautoRenderDistance=vielleicht\n");
		BoostConfig garbage = BoostConfig.load(file);
		check(garbage.minRenderDistance == 3, "kaputte Zahl: Standardwert");
		check(garbage.maxRenderDistance == 32, "zu grosse Zahl: auf 32 begrenzt");
		check(garbage.lowFps == 24.0, "NaN: Standardwert");
		check(garbage.highFps == 29.0, "zu kleine obere Schwelle: auf lowFps + 5 angehoben");
		check(garbage.firstStartPreset == Preset.POTATO, "unbekannte Voreinstellung: Standardwert");
		check(!garbage.autoRenderDistance, "kaputter Wahrheitswert: Standardwert");

		Files.writeString(file, "minRenderDistance=10\nmaxRenderDistance=5\n");
		BoostConfig swapped = BoostConfig.load(file);
		check(swapped.minRenderDistance == 10 && swapped.maxRenderDistance == 10, "Minimum ueber Maximum: Maximum wird angehoben");

		Files.writeString(file, "autoRenderDistance=\\u12zz\n");
		BoostConfig malformed = BoostConfig.load(file);
		check(!malformed.autoRenderDistance && malformed.firstStartPreset == Preset.POTATO,
				"unlesbare Datei: Standardwerte statt Absturz");
	}

	private static void presetsAreConsistent() {
		check(Preset.byId("POTATO").orElse(null) == Preset.POTATO, "Name wird ohne Beachtung der Gross-/Kleinschreibung gefunden");
		check(Preset.byId("  low ").orElse(null) == Preset.LOW, "Leerzeichen werden ignoriert");
		check(Preset.byId("banane").isEmpty() && Preset.byId(null).isEmpty(), "unbekannter Name und null ergeben nichts");

		for (Preset preset : Preset.values()) {
			PresetSettings s = preset.settings();
			String name = preset.id();

			check(s.renderDistance() >= 2 && s.renderDistance() <= 32, name + ": Sichtweite im gueltigen Bereich");
			check(s.simulationDistance() >= 5 && s.simulationDistance() <= 32, name + ": Simulationsdistanz im gueltigen Bereich");
			check(s.particleLevel() >= 0 && s.particleLevel() <= 2, name + ": Partikelstufe im gueltigen Bereich");
			check(s.biomeBlend() >= 0 && s.biomeBlend() <= 7, name + ": Biomuebergang im gueltigen Bereich");
			check(s.entityDistance() >= 0.5 && s.entityDistance() <= 5.0, name + ": Entity-Sichtweite im gueltigen Bereich");
			check(s.mipmapLevels() >= 0 && s.mipmapLevels() <= 4, name + ": Mipmaps im gueltigen Bereich");
			check(s.chunkFadeSeconds() >= 0.0, name + ": Einblendzeit nicht negativ");
			check(s.anisotropyBit() >= 0, name + ": Anisotropie nicht negativ");
			check(Preset.byId(name).orElse(null) == preset, name + ": Name fuehrt zurueck zur Voreinstellung");
		}

		check(Preset.POTATO.settings().renderDistance() < Preset.LOW.settings().renderDistance()
				&& Preset.LOW.settings().renderDistance() < Preset.BALANCED.settings().renderDistance(),
				"die Sichtweiten der Voreinstellungen steigen von potato ueber low zu balanced");
	}

	private static void check(boolean ok, String name) {
		checks++;

		if (ok) {
			System.out.println("  ok       " + name);
		} else {
			failures++;
			System.out.println("  FEHLER   " + name);
		}
	}
}
