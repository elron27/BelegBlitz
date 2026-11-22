"""Veröffentlicht die Release-APK in einen Update-Ordner (z. B. auf dem NAS).

Aufruf nach `./gradlew assembleRelease`:
    python tools/publish_release.py <Ordner>

<Ordner> ist der App-Ordner im Appstore, z. B. //DataTank/Data/Appstore/FotoSortierer. Sein Name wird
als App-Name in den APK-Namen übernommen. Das Skript funktioniert unverändert für jede Android-App.

Ergebnis (U-1):
    <Ordner>/final/<App>-<Version>.apk   aktuelle Version, von der App gelesen
    <Ordner>/final/latest.json           Version, APK-Name und SHA-256
    <Ordner>/archiv/<Version>/<App>-<Version>.apk   jede Version bleibt erhalten

Alte APKs in `final/` werden nur entfernt, wenn sie schon unter `archiv/` liegen (gleiche Prüfsumme).
Vorhandene Dateien im Archiv werden nie überschrieben.
"""

import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle.kts"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"


def read_version() -> tuple[int, str]:
    text = GRADLE.read_text(encoding="utf-8")
    code = int(re.search(r"versionCode\s*=\s*(\d+)", text).group(1))
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1)
    return code, name


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Aufruf: python tools/publish_release.py <Ordner>")
    target = Path(sys.argv[1])
    if not APK.exists():
        sys.exit(f"Release-APK fehlt: {APK}. Erst ./gradlew assembleRelease ausführen.")

    code, name = read_version()
    apk_name = f"{target.name}-{name}.apk"  # App-Name = Name des Zielordners
    digest = sha256(APK)
    final = target / "final"
    archive = target / "archiv" / name
    archive_file = archive / apk_name

    if archive_file.exists() and sha256(archive_file) != digest:
        sys.exit(f"{archive_file} existiert schon mit anderem Inhalt. Abbruch, nichts überschrieben.")

    # Alte APKs in final/ nur entfernen, wenn ihre Kopie im Archiv liegt
    for old in final.glob("*.apk") if final.exists() else []:
        if old.name == apk_name:
            continue
        archived = [p for p in (target / "archiv").glob(f"*/{old.name}") if sha256(p) == sha256(old)]
        if not archived:
            sys.exit(f"{old} ist nicht archiviert. Abbruch, bitte zuerst manuell prüfen.")
        old.unlink()

    final.mkdir(parents=True, exist_ok=True)
    archive.mkdir(parents=True, exist_ok=True)
    if not archive_file.exists():
        shutil.copy2(APK, archive_file)
    shutil.copy2(APK, final / apk_name)

    manifest = {"versionCode": code, "versionName": name, "apk": apk_name, "sha256": digest}
    (final / "latest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Version {name} (Code {code}) veröffentlicht: {final}")


if __name__ == "__main__":
    main()
