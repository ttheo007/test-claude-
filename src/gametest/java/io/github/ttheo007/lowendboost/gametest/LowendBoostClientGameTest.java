package io.github.ttheo007.lowendboost.gametest;

import java.nio.file.Files;
import java.nio.file.Path;

import org.spongepowered.asm.mixin.MixinEnvironment;

import io.github.ttheo007.lowendboost.client.LowendBoostClient;
import io.github.ttheo007.lowendboost.core.Preset;

import net.minecraft.client.CloudStatus;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Prueft die Mod in einem echten Minecraft-Client: Sie wird geladen, der Mixin greift, die Befehle laufen
 * und die Voreinstellungen landen in den Optionen. Gerendert wird in der CI in Software; ueber die
 * Geschwindigkeit sagt der Test deshalb nichts aus, nur ueber das Verhalten.
 */
public final class LowendBoostClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		check(FabricLoader.getInstance().isModLoaded("lowendboost"), "Die Mod 'lowendboost' wurde nicht geladen");

		Path configFile = FabricLoader.getInstance().getConfigDir().resolve("lowendboost.properties");
		check(Files.exists(configFile), "Konfigurationsdatei fehlt: " + configFile);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(20);

			// 1) Der Mixin in Minecraft.runTick zaehlt Frames.
			long framesBefore = context.computeOnClient(client -> LowendBoostClient.framesSeen());
			context.waitTicks(20);
			long framesAfter = context.computeOnClient(client -> LowendBoostClient.framesSeen());
			check(framesAfter > framesBefore, "Der Frame-Zaehler steht still: " + framesBefore + " -> " + framesAfter);

			// 2) Alle Mixins sind angewendet worden.
			MixinEnvironment.getCurrentEnvironment().audit();

			// 3) Voreinstellungen ueber den echten Befehlsweg.
			runCommand(context, "lowend preset balanced");
			checkView(context, Preset.BALANCED);

			runCommand(context, "lowend preset low");
			checkView(context, Preset.LOW);

			runCommand(context, "lowend preset potato");
			checkView(context, Preset.POTATO);

			CloudStatus clouds = context.computeOnClient(client -> client.options.cloudStatus().get());
			check(clouds == CloudStatus.OFF, "Wolken sind nach 'potato' nicht aus: " + clouds);

			// 4) Die uebrigen Befehle duerfen nichts kaputt machen.
			runCommand(context, "lowend status");
			runCommand(context, "lowend range 3 6");
			runCommand(context, "lowend auto on");
			context.waitTicks(10);
			runCommand(context, "lowend auto off");

			// 5) Die Optionen sind nach all dem noch gueltig und das Spiel laeuft weiter.
			context.waitTicks(20);
			long framesEnd = context.computeOnClient(client -> LowendBoostClient.framesSeen());
			check(framesEnd > framesAfter, "Das Spiel rendert nach den Befehlen nicht mehr: " + framesAfter + " -> " + framesEnd);
		}
	}

	private static void runCommand(ClientGameTestContext context, String command) {
		context.runOnClient(client -> client.player.connection.sendCommand(command));
		// Eine Voreinstellung kann die Texturen neu laden; das braucht einige Ticks.
		context.waitTicks(30);
	}

	private static void checkView(ClientGameTestContext context, Preset preset) {
		int render = context.computeOnClient(client -> client.options.renderDistance().get());
		int simulation = context.computeOnClient(client -> client.options.simulationDistance().get());

		check(render == preset.settings().renderDistance(),
				"Sichtweite nach '" + preset.id() + "': erwartet " + preset.settings().renderDistance() + ", ist " + render);
		check(simulation == preset.settings().simulationDistance(),
				"Simulationsdistanz nach '" + preset.id() + "': erwartet " + preset.settings().simulationDistance() + ", ist " + simulation);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
