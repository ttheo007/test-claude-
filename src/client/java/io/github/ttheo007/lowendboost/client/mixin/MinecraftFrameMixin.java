package io.github.ttheo007.lowendboost.client.mixin;

import io.github.ttheo007.lowendboost.client.LowendBoostClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

/**
 * Zaehlt die gerenderten Frames fuer die automatische Sichtweite. {@code runTick} wird einmal je
 * Durchlauf der Hauptschleife aufgerufen, also einmal je Frame. Der Einstieg am Methodenanfang
 * kostet nur einen Aufruf von {@link System#nanoTime()}.
 */
@Mixin(Minecraft.class)
public class MinecraftFrameMixin {
	@Inject(at = @At("HEAD"), method = "runTick")
	private void lowendboost$countFrame(CallbackInfo info) {
		LowendBoostClient.onFrame();
	}
}
