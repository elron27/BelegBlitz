# CLAUDE.md – BelegBlitz

Android-App (Kotlin, Jetpack Compose) zur Ablage und Nachverfolgung von Arztrechnungen und Kassen-Bescheiden
(DAK, VKB). Alle Daten bleiben lokal auf dem Handy, keine Netzwerkberechtigung, keine Kassen-Schnittstellen.

## Anforderungen (ReqSeq)
- Die Anforderungen liegen im ReqSeq-Workspace im Projektstamm (`need/`, `system-requirement/`, `metamodel/`, `evidence/`) (Needs `N-…`, System-Requirements `SR-…`,
  alle SR sind **approved**). Lesen mit `reqseq element get SR-xxxxxx`.
- Tests tragen die Kennung ihres Requirements bzw. Akzeptanzkriteriums im Namen; Code-Kommentare nennen die SR-ID.
- Eine approvte Anforderung wird nie stillschweigend geändert: erst Stefan fragen.
- Testdokumente: `testdata/` (`echt/` mit geschwärzten Originalen, **nie committen**;
  `README.md` mit Erwartungswerten).

## Stufen
- **Stufe 1 (Ziel: Sonntag 29.11.2026):** Fotos → mehrseitiges PDF, lokale OCR (Rechnung und Bescheid), Titel
  `Fachrichtung_Praxis_JJJJMMTT`, Einreichungen mit Status je Kasse, Bescheide erfassen und zuordnen, Hinweis speichern,
  Live-Auswertung, VKB-Beitragsverlauf und Jahresbilanz, PDF-Import (Teilen), Volltextsuche.
- **Stufe 2:** Fingerabdruck-Sperre, Erinnerung, Export/Wiederherstellung (Ordner `Kasse/Jahr/Dokumente` + Datendatei).

## Zusammenarbeit
- Stefan programmiert nicht selbst. Kommunikation, Kommentare, Commit-Messages und Chat auf **Deutsch**.
- UI-Texte in `res/values/strings.xml` (Englisch) **und** `res/values-de/strings.xml` (Deutsch).
- Nach jedem abgeschlossenen Schritt lokal committen. **Push und Release nur auf Stefans Zuruf.**
- Datenmodell von Anfang an mit Personen (Stufe 1: genau eine Person).

## Arbeiten mit Sub-Agenten (Token sparen)
Die Hauptsitzung plant, verteilt und prüft. Die Arbeit machen Sub-Agenten in `.claude/agents/`:
| Agent | Modell | Aufgabe |
|---|---|---|
| `android-dev` | sonnet | Kotlin/Compose-Code für genau ein Requirement oder einen Bildschirm |
| `ocr-parser` | sonnet | OCR-Anbindung und reine Parser-Logik (domain/), Unit-Tests gegen `testdata` |
| `emulator-tester` | haiku | Bauen, im Emulator installieren, Abläufe prüfen, Screenshots, kurzer Bericht |
| `req-checker` | haiku | `reqseq workspace check`, Verifikationsgrad, offene Lücken |

Regeln:
1. Ein Auftrag = ein klar umrissenes Stück mit Dateien und „fertig, wenn …" (Requirement-ID nennen). Kein „mach die App".
2. Nie zwei Agenten gleichzeitig an denselben Dateien. Parallel nur bei getrennten Paketen (z. B. `domain/ocr` und `ui/`).
3. Agenten antworten knapp (höchstens ~15 Zeilen: was geändert, was getestet, was offen). Keine Code-Dumps.
4. Die Hauptsitzung liest keine großen Dateien und keine Build-Logs; dafür gibt es den Agenten.
5. Bauen und Gradle nur durch einen Agenten zur Zeit (gemeinsamer `build/`-Ordner).

## Umgebung (Windows)
- JDK: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
- SDK/adb: `/c/Users/Stefan/AppData/Local/Android/Sdk/platform-tools/adb.exe`
- Emulator: `/c/Users/Stefan/AppData/Local/Android/Sdk/emulator/emulator.exe -avd capybara_api36` (im Hintergrund starten)
- Pfade in adb-Befehlen mit `MSYS_NO_PATHCONV=1` aufrufen.
- Screenshot: `MSYS_NO_PATHCONV=1 adb exec-out screencap -p > "$TEMP/x.png"`.

## Befehle (nach dem Gerüst)
```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew assembleRelease     # Release-Schlüssel wie FotoSortierer (signing/ nicht in Git), R8
python tools/publish_release.py "//DataTank/Data/Appstore/BelegBlitz"   # nach jedem Release; versionCode vorher erhöhen
```
Beim ersten Release `tools/publish_release.py` und Update-Funktion („App-Update" in den Einstellungen) wie FotoSortierer einbauen.

## Wichtige Erfahrungen aus FotoSortierer
- NavHost: eigene kurze Übergänge setzen (`fade`/`scaleOut`, `tween(220)`, `predictivePop…`), sonst hakt die Zurück-Geste.
- R8-Release: ML-Kit-Keep-Regel `-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); public java.util.List getComponents(); }`.
  Fehler nie still verschlucken (`runCatching{}.getOrNull()` nur mit Log). Wichtige Abläufe auch im Release-Build testen.
- Baseline Profile `app/src/main/baseline-prof.txt` anlegen.
- Room: Schema-Änderung → `version` erhöhen + `AutoMigration`; Schemas nach `app/schemas/` committen.

## Stolperfallen der Werkzeuge
- Bash-Heredocs mit `${` brechen ab → Skripte per Write-Tool anlegen.
- Python-Dateien immer mit `encoding='utf-8'`.
