package io.github.ttheo007.lowendboost.core;

/**
 * Entscheidet anhand der gemessenen Bildrate, ob die Sichtweite um einen Chunk sinken oder
 * steigen soll. Reine Logik ohne Minecraft-Zugriff: Die Zeit kommt als Nanosekunden-Parameter.
 *
 * <p>Ablauf pro Durchlauf der Hauptschleife: zuerst {@link #onFrame(long)} (ein Aufruf je
 * gerendertem Frame), danach {@link #poll(long, int, Params)} oder {@link #observe(long)}
 * (z. B. einmal je Tick). Gemessen wird in Fenstern von 2 Sekunden.
 *
 * <p>Jede Aenderung der Sichtweite baut in Minecraft das Sichtfeld neu auf. Deshalb wird
 * vorsichtig vorgegangen: nur Schritte von 1 Chunk, lange Wartezeiten dazwischen, und nach
 * jeder Aenderung sowie nach dem Betreten einer Welt werden einige Messfenster ignoriert.
 */
public final class AdaptiveGovernor {
	/** Schwellen und Grenzen fuer eine Entscheidung. */
	public record Params(double lowFps, double highFps, int minDistance, int maxDistance) {
	}

	public static final long WINDOW_NANOS = 2_000_000_000L;
	/** Ein Abstand zwischen zwei Frames, der laenger dauert, gilt als Pause/Ladehaenger und verwirft das Fenster. */
	public static final long PAUSE_GAP_NANOS = 1_000_000_000L;
	/** So viele zu langsame Fenster (je 2 s) hintereinander braucht es zum Verkleinern. */
	public static final int LOW_WINDOWS_TO_SHRINK = 2;
	/** So viele fluessige Fenster (je 2 s) hintereinander braucht es zum Vergroessern. */
	public static final int HIGH_WINDOWS_TO_GROW = 10;
	public static final long SHRINK_COOLDOWN_NANOS = 8_000_000_000L;
	public static final long GROW_COOLDOWN_NANOS = 45_000_000_000L;
	/** Ignorierte Fenster nach einer Aenderung (der Neuaufbau verzerrt die Messung). */
	public static final int SETTLE_WINDOWS = 2;
	/** Ignorierte Fenster nach dem Betreten einer Welt (Chunks laden noch). */
	public static final int WARMUP_WINDOWS = 7;

	private boolean started;
	private long windowStart;
	private long lastFrame;
	private int frames;
	private int lowStreak;
	private int highStreak;
	private int ignoreWindows;
	private long lastChange;
	private boolean hasChange;
	private double lastFps = -1.0;

	/** Setzt alles zurueck, z. B. beim Betreten einer Welt. Danach folgt die Aufwaermphase. */
	public void reset(long now) {
		started = true;
		windowStart = now;
		lastFrame = now;
		frames = 0;
		lowStreak = 0;
		highStreak = 0;
		ignoreWindows = WARMUP_WINDOWS;
		hasChange = false;
		lastFps = -1.0;
	}

	/** Meldet einen gerenderten Frame. */
	public void onFrame(long now) {
		ensureStarted(now);

		if (now - lastFrame > PAUSE_GAP_NANOS) {
			discardWindow(now);
		}

		lastFrame = now;
		frames++;
	}

	/** Misst nur die Bildrate, trifft aber keine Entscheidung (Automatik aus). */
	public void observe(long now) {
		ensureStarted(now);
		closeWindow(now);
	}

	/**
	 * Wertet ein abgeschlossenes Messfenster aus und liefert die gewuenschte Sichtweite.
	 *
	 * @param current aktuelle Sichtweite
	 * @return gewuenschte Sichtweite; gleich {@code current}, wenn nichts zu aendern ist
	 */
	public int poll(long now, int current, Params params) {
		ensureStarted(now);

		if (current > params.maxDistance()) {
			// Die Obergrenze gilt sofort, ohne auf ein Messfenster zu warten.
			markChange(now);
			return params.maxDistance();
		}

		if (!closeWindow(now)) {
			return current;
		}

		if (ignoreWindows > 0) {
			ignoreWindows--;
			lowStreak = 0;
			highStreak = 0;
			return current;
		}

		if (lastFps < params.lowFps()) {
			lowStreak++;
			highStreak = 0;
		} else if (lastFps > params.highFps()) {
			highStreak++;
			lowStreak = 0;
		} else {
			lowStreak = 0;
			highStreak = 0;
		}

		int target = current;

		if (lowStreak >= LOW_WINDOWS_TO_SHRINK && current > params.minDistance() && cooledDown(now, SHRINK_COOLDOWN_NANOS)) {
			target = current - 1;
		} else if (highStreak >= HIGH_WINDOWS_TO_GROW && current < params.maxDistance() && cooledDown(now, GROW_COOLDOWN_NANOS)) {
			target = current + 1;
		}

		if (target != current) {
			markChange(now);
		}

		return target;
	}

	/** Bildrate des zuletzt abgeschlossenen Messfensters, oder -1, wenn es noch keines gab. */
	public double lastFps() {
		return lastFps;
	}

	/**
	 * Schliesst das Messfenster, wenn es voll ist.
	 *
	 * @return true, wenn ein gueltiges Fenster abgeschlossen und {@link #lastFps} aktualisiert wurde
	 */
	private boolean closeWindow(long now) {
		long elapsed = now - windowStart;

		if (elapsed < WINDOW_NANOS) {
			return false;
		}

		if (frames == 0) {
			// Keine Frames gezaehlt: das ist keine Messung (z. B. Zaehler ausgefallen), also nicht als "0 FPS" werten.
			windowStart = now;
			return false;
		}

		lastFps = frames * 1.0e9 / elapsed;
		frames = 0;
		windowStart = now;
		return true;
	}

	private void ensureStarted(long now) {
		if (!started) {
			reset(now);
		}
	}

	private void markChange(long now) {
		lastChange = now;
		hasChange = true;
		ignoreWindows = Math.max(ignoreWindows, SETTLE_WINDOWS);
		lowStreak = 0;
		highStreak = 0;
	}

	private boolean cooledDown(long now, long cooldownNanos) {
		return !hasChange || now - lastChange >= cooldownNanos;
	}

	private void discardWindow(long now) {
		windowStart = now;
		frames = 0;
		lowStreak = 0;
		highStreak = 0;
	}
}
