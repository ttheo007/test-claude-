#!/usr/bin/env bash
# Prueft die gebaute JAR auf alles, ohne das Fabric die Mod nicht laden wuerde.
# Laeuft nach "./gradlew build" (auch in GitHub Actions) und braucht nur bash und python3.
set -euo pipefail

cd "$(dirname "$0")/.."

jar="$(ls build/libs/lowendboost-*.jar 2>/dev/null | grep -v -- '-sources' | head -n 1 || true)"

if [ -z "$jar" ]; then
	echo "Keine JAR in build/libs gefunden. Zuerst ./gradlew build ausfuehren."
	exit 1
fi

# -I: ohne Suchpfad im aktuellen Ordner und ohne Umgebungsvariablen
python3 -I - "$jar" <<'PY'
import json
import sys
import zipfile

jar = sys.argv[1]

REQUIRED = [
    "fabric.mod.json",
    "lowendboost.client.mixins.json",
    "assets/lowendboost/lang/de_de.json",
    "assets/lowendboost/lang/en_us.json",
    "io/github/ttheo007/lowendboost/client/LowendBoostClient.class",
    "io/github/ttheo007/lowendboost/client/BoostCommand.class",
    "io/github/ttheo007/lowendboost/client/OptionApplier.class",
    "io/github/ttheo007/lowendboost/client/mixin/MinecraftFrameMixin.class",
    "io/github/ttheo007/lowendboost/core/AdaptiveGovernor.class",
    "io/github/ttheo007/lowendboost/core/BoostConfig.class",
    "io/github/ttheo007/lowendboost/core/Preset.class",
    "io/github/ttheo007/lowendboost/core/PresetSettings.class",
]

errors = []

with zipfile.ZipFile(jar) as z:
    names = set(z.namelist())

    for entry in REQUIRED:
        if entry not in names:
            errors.append("FEHLT in der JAR: " + entry)

    version = "?"

    if "fabric.mod.json" in names:
        meta = json.loads(z.read("fabric.mod.json").decode("utf-8"))
        version = str(meta.get("version", ""))

        if not version or "$" in version or "{" in version:
            errors.append("fabric.mod.json: Version wurde nicht ersetzt: %r" % version)

        if meta.get("id") != "lowendboost":
            errors.append("fabric.mod.json: unerwartete Mod-ID: %r" % meta.get("id"))

        if meta.get("environment") != "client":
            errors.append("fabric.mod.json: environment ist nicht 'client'")

        entrypoints = meta.get("entrypoints", {}).get("client", [])

        if not entrypoints:
            errors.append("fabric.mod.json: kein Client-Entrypoint")

        for cls in entrypoints:
            path = cls.replace(".", "/") + ".class"

            if path not in names:
                errors.append("Entrypoint ohne Klasse in der JAR: " + cls)

        for mixin in meta.get("mixins", []):
            config = mixin["config"] if isinstance(mixin, dict) else mixin

            if config not in names:
                errors.append("Mixin-Konfiguration fehlt in der JAR: " + config)
                continue

            mixin_json = json.loads(z.read(config).decode("utf-8"))
            package = mixin_json["package"].replace(".", "/")

            for section in ("mixins", "client", "server"):
                for cls in mixin_json.get(section, []):
                    path = "%s/%s.class" % (package, cls.replace(".", "/"))

                    if path not in names:
                        errors.append("Mixin-Klasse fehlt in der JAR: " + path)

    mixin_class = "io/github/ttheo007/lowendboost/client/mixin/MinecraftFrameMixin.class"

    if mixin_class in names and b"runTick" not in z.read(mixin_class):
        errors.append("MinecraftFrameMixin zielt nicht auf runTick")

    de = en = None

    if "assets/lowendboost/lang/de_de.json" in names and "assets/lowendboost/lang/en_us.json" in names:
        de = json.loads(z.read("assets/lowendboost/lang/de_de.json").decode("utf-8"))
        en = json.loads(z.read("assets/lowendboost/lang/en_us.json").decode("utf-8"))

        if set(de) != set(en):
            errors.append("Sprachdateien haben unterschiedliche Schluessel: " + ", ".join(sorted(set(de) ^ set(en))))

if errors:
    print("JAR-Pruefung fehlgeschlagen (%s):" % jar)

    for error in errors:
        print("  - " + error)

    sys.exit(1)

print("JAR ok: %s (Version %s, %d Eintraege)" % (jar, version, len(names)))
PY
