# Scanner

A dark, black-and-gold document scanner for Android. Scans are plain PDFs in a folder you
choose, every page's text is searchable, and pages can be rearranged after the fact.
Built for personal use.

Version 2 is a rewrite of the first (Codex-built) version. It installs as a separate app,
`com.tuttoposto.scanner`, so both can sit side by side while you switch over.

## Features

- **Scan** with Google's ML Kit document scanner: edge detection, crop, perspective
  correction, filters, multi-page. Auto-enhance is off by default (plain crop), switchable
  in Settings.
- **Your folder, your files.** On first launch you pick a folder (Documents/Scanner is
  suggested). Every PDF in it shows up, whichever app made it, and the files stay put if
  the app is uninstalled. No storage permission needed.
- **Search inside scans.** Text is recognized on the device in the background; search
  matches names and page text and opens the matching page.
- **Viewer** with pinch and double-tap zoom. **Text mode**: tap or drag across lines to
  select and copy them, or see the whole document's text.
- **Arrange pages**: hold and drag to reorder, rotate, remove. Changes are saved only when
  you tap Save.
- **Add pages** to a document by scanning more; **merge** documents; **import** PDFs and
  photos (photos become one PDF).
- **Name suggestions** read off the first page right after a scan.
- **Delete with undo**; grid or list view; sort by date, name or size.

## How it's built

- Kotlin, Jetpack Compose, Material 3. `minSdk` 31, `targetSdk` 36, AGP 9.0.1, Gradle 9.1.
- `data/LibraryFolder`: the chosen folder through the Storage Access Framework.
- `data/Library`: syncs the folder into a Room index (page counts, recognized text),
  runs the background text reader, and does all writes. Edited PDFs are built in a work
  file, checked, and only then copied over the original (with a backup kept until it
  succeeds).
- `data/PdfEditor`: PdfBox-Android for append, merge, reorder/rotate/remove and
  photos→PDF. `PdfRenderer` for display.
- `logic/`: pure, unit-tested helpers (names, search snippets, folder sync plan, page edits).

## Build & run

1. Open in Android Studio, or build with JDK 17: `./gradlew :app:assembleRelease`.
2. Needs a device with Google Play services (the scanner and text recognition come from it).
3. Release signing reads `scannerKeystore`, `scannerKeystorePassword` and
   `scannerKeyAlias` from `~/.gradle/gradle.properties`. Without them the release APK is
   unsigned. **Back up the keystore**: updates only install over an APK signed with the
   same key.
4. Debug builds install as `com.tuttoposto.scanner.debug`, separate from the real app.

R8 is on for release. ML Kit and PdfBox need the keep rules in `app/proguard-rules.pro`;
without them text recognition fails in release only.

## Tests

`./gradlew :app:testDebugUnitTest` runs the unit tests for the `logic/` package.
