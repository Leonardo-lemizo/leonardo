# Leonardo – Italienisch lernen (Nuovo Espresso 1, Lezione 1–4)

Offline-Lern-App mit Erklärungen, Übungen, Karteikarten und Verben-Trainer.
Kein Login, keine Cloud, keine Werbung – der Lernstand bleibt auf dem Gerät.

Es gibt die App zweimal, mit **denselben Inhalten** aus [`content/`](content/):

| | Ordner | Für |
|---|---|---|
| Android-App (Kotlin + Compose) | [`app/`](app/) | Android-Handys (APK) |
| Web-App (PWA, reines HTML/CSS/JS) | [`web/`](web/) | iPhone (Safari) und jeden Browser |

## Inhalte ändern

Nur die JSON-Dateien in `content/` bearbeiten. Beide Apps lesen sie direkt:
die Android-App beim Bauen, die Web-App über `build-web.ps1`.

## Web-App

```powershell
./build-web.ps1          # baut _site/ (Inhalte + Schrift werden hineinkopiert)
./tools/serve-web.ps1    # Test: http://localhost:8123/leonardo/
```

Bei jedem Push auf `main` baut GitHub Actions ([`.github/workflows/pages.yml`](.github/workflows/pages.yml))
die Seite und veröffentlicht sie über GitHub Pages unter `https://<konto>.github.io/leonardo/`.

Auf dem iPhone: Seite in **Safari** öffnen → Teilen-Symbol → **Zum Home-Bildschirm**.
Danach läuft die App ohne Internet.

### Aussprache (Aufnahmen)

Alle italienischen Wörter und Sätze liegen als MP3 in [`audio/`](audio/) – aufgenommen mit der freien,
lokal laufenden Sprach-KI [Piper](https://github.com/rhasspy/piper) und der Stimme
[it_IT-paola-medium](https://huggingface.co/rhasspy/piper-voices/tree/main/it/it_IT/paola/medium)
(Datensatz: CC0). Die Web-App speichert sie beim ersten Öffnen automatisch für offline;
fehlt eine Aufnahme, liest die Stimme des Geräts vor.

Nach neuen Inhalten einfach nachaufnehmen (nur neue Texte werden erzeugt):

```powershell
./tools/make-audio.ps1 -Piper <pfad>\piper.exe -Stimme <pfad>\it_IT-paola-medium.onnx
```

## Android-App

```powershell
./gradlew testDebugUnitTest assembleRelease
```

Signieren braucht `keystore.properties` und die `.jks`-Datei – beide sind absichtlich **nicht** im Repository.

## Hinweise

- Lösungen mit `"loesung_generiert": true` wurden ergänzt und sind ohne Gewähr.
- Schrift: Atkinson Hyperlegible (SIL Open Font License, siehe `Atkinson-Hyperlegible-OFL.txt`).
