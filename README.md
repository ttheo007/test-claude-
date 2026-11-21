# Lowend Boost

Client-seitige Fabric-Mod für **Minecraft 26.3**, gedacht für schwache PCs (entwickelt für einen Pentium E5300 mit 2 Kernen und eine GeForce GT 710).

Die Mod ist **keine Render-Engine** wie Sodium. Sie ändert nur die normalen Vanilla-Videooptionen auf sparsame Werte und kann die Sichtweite nach der gemessenen Bildrate regeln. Auf einem Server muss sie nicht installiert sein.

## Was sie macht

1. **Voreinstellung beim ersten Start.** Beim allerersten Start wird einmal die Voreinstellung `potato` in die Videooptionen geschrieben. Danach fasst die Mod deine Einstellungen nicht mehr an.
2. **Befehl `/lowend`.** Damit wendest du jederzeit eine Voreinstellung an und steuerst die Automatik.
3. **Automatische Sichtweite (optional, standardmäßig aus).** Fällt die Bildrate dauerhaft unter einen Wert, wird die Sichtweite um einen Chunk kleiner. Ist das Spiel dauerhaft flüssig, darf sie wieder wachsen.

### Voreinstellungen

| Option | `potato` | `low` | `balanced` |
|---|---|---|---|
| Sichtweite (Chunks) | 4 | 6 | 8 |
| Simulationsdistanz (Chunks) | 5 | 6 | 8 |
| Wolken | aus | aus | aus |
| Partikel | minimal | reduziert | reduziert |
| Weiche Beleuchtung (AO) | aus | aus | an |
| Biomübergang | 0 | 1 | 2 |
| Entity-Sichtweite | 50 % | 75 % | 100 % |
| Entity-Schatten | aus | aus | aus |
| Mipmaps | 0 | 2 | 2 |
| Chunk-Einblendzeit | 0 s | 0 s | 0 s |
| Wetter-Radius | 3 | 5 | 7 |
| Durchsichtige Blätter (Cutout) | aus | aus | an |
| Verbesserte Transparenz | aus | aus | aus |
| Anisotrope Filterung | aus | aus | aus |

Jeder Wert wird vor dem Setzen auf den gültigen Bereich der jeweiligen Option begrenzt.

### Befehle

| Befehl | Wirkung |
|---|---|
| `/lowend` oder `/lowend status` | zeigt Sichtweite, gemessene Bildrate und Zustand der Automatik |
| `/lowend preset potato\|low\|balanced` | wendet eine Voreinstellung an und speichert die Optionen |
| `/lowend auto on\|off` | schaltet die automatische Sichtweite ein oder aus |
| `/lowend range <min> <max>` | Grenzen der automatischen Sichtweite (2 bis 32 Chunks) |

Ändert eine Voreinstellung die Mipmap-Stufe, werden die Texturen dabei einmal neu geladen.

### So arbeitet die automatische Sichtweite

- Die Bildrate wird in Fenstern von 2 Sekunden gemessen.
- Nach dem Betreten einer Welt und nach jeder Änderung werden einige Fenster übersprungen, weil das Laden der Chunks die Messung verfälscht (14 s beziehungsweise 4 s).
- **Verkleinern** um 1 Chunk: 2 Fenster hintereinander unter `lowFps` (Standard 24), mindestens 8 s nach der letzten Änderung.
- **Vergrößern** um 1 Chunk: 10 Fenster (20 s) hintereinander über `highFps` (Standard 50), mindestens 45 s nach der letzten Änderung.
- Die Sichtweite bleibt immer zwischen `minRenderDistance` und `maxRenderDistance`. Liegt sie darüber, wird sie sofort auf das Maximum gesenkt.
- Ladehänger und Pausen (ein Abstand von über 1 s zwischen zwei Frames) verwerfen das laufende Messfenster.

Jede Änderung der Sichtweite baut in Minecraft das Sichtfeld neu auf und ruckelt kurz. Deshalb ist die Automatik standardmäßig aus und arbeitet nur in Einzelschritten mit langen Pausen.

## Konfiguration

Die Datei `config/lowendboost.properties` wird beim ersten Start angelegt. Falsche oder fehlende Werte werden durch sinnvolle ersetzt.

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `firstStartPreset` | `potato` | Voreinstellung für den ersten Start: `potato`, `low`, `balanced` oder `none` (nichts ändern) |
| `firstStartDone` | `false` | wird nach dem ersten Start auf `true` gesetzt |
| `autoRenderDistance` | `false` | automatische Sichtweite |
| `minRenderDistance` | `3` | untere Grenze der Automatik |
| `maxRenderDistance` | `8` | obere Grenze der Automatik |
| `lowFps` | `24.0` | darunter wird verkleinert |
| `highFps` | `50.0` | darüber darf vergrößert werden |
| `chatMessages` | `true` | Hinweise der Automatik im Chat |

Willst du deine eigenen Videooptionen behalten, setze **vor dem ersten Start** `firstStartPreset=none`.

## Installation

Benötigt werden **Fabric Loader ab 0.20.1**, die **Fabric API** für 26.3 und Java 25.

**Variante A: fertige JAR von GitHub.** Unter *Actions* den letzten erfolgreichen Lauf `build` öffnen und das Artefakt `Artifacts` herunterladen. Darin ist `lowendboost-1.0.0.jar`.

**Variante B: selbst bauen.** Mit JDK 25:

```bash
./gradlew build
```

Die fertige Datei liegt danach in `build/libs/lowendboost-1.0.0.jar`.

In beiden Fällen gilt: **nur die Datei ohne `-sources`** in den `mods`-Ordner der Instanz legen (in Prism Launcher: Instanz wählen, *Mods*, *Mod hinzufügen*).

## Ehrliche Hinweise

- **Im Spiel getestet ist die Mod nicht.** Geprüft sind die reine Logik (Voreinstellungen, Konfiguration, Automatik) mit `tools/run-core-tests.sh`, das Kompilieren gegen Minecraft 26.3 und der Inhalt der gebauten JAR mit `tools/check-jar.sh`. Das alles läuft bei jedem Push in GitHub Actions. Nicht geprüft ist das Verhalten im laufenden Spiel (Mixin, Optionen, Befehl). Bei einem Fehler hilft die Datei `logs/latest.log`.
- **Mehr Leistung bringen Render-Mods.** Diese Mod ändert nur Einstellungen. Wie viel das auf deiner Hardware bringt, ist nicht gemessen.
- Der Mixin hängt sich an `Minecraft.runTick` und zählt nur Frames. Ändert eine neuere Minecraft-Version diese Methode, startet das Spiel mit einer Mixin-Fehlermeldung statt still falsch zu laufen.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `src/main/java/.../core` | reine Logik ohne Minecraft: Voreinstellungen, Konfiguration, Automatik (getestet) |
| `src/client/java/.../client` | Anbindung an Minecraft: Optionen setzen, Befehl, Tick-Schleife |
| `src/client/java/.../client/mixin` | Frame-Zähler |
| `tools/` | `run-core-tests.sh` (Tests der reinen Logik, ab JDK 21) und `check-jar.sh` (prüft die gebaute JAR) |

## Lizenz

MIT, siehe `LICENSE`.
