# Scanner

A lightweight, **dark-mode** PDF scanner for Android. Capture documents with the
camera, save them as PDFs, and manage a small library — minimalist UI with a gold accent.
Built for personal use.

## Features

- **Capture** via Google ML Kit document scanner — live edge detection, auto-crop,
  perspective correction, multi-page, gallery import.
- **Saved as PDFs** to the shared `Documents/Scanner` folder (visible in the Files app and
  other apps; no storage permission needed).
- **Library** — thumbnails, page counts, search, rename, share, delete, and multi-select
  batch share/delete.
- **In-app viewer** with a Google-Lens-style **selectable OCR text overlay**: long-press to
  select recognized text on the page and copy it (or "Copy all text").
- **Append pages** to an existing PDF (re-scan and merge).
- **Extract text** via ML Kit text recognition.

## Tech

- Kotlin, Jetpack Compose, Material 3 (dark-only, gold palette)
- ML Kit document scanner + text recognition (Google Play Services)
- PdfBox-Android (PDF merge/append), Android `PdfRenderer`, MediaStore
- `minSdk` 29 · `compileSdk`/`targetSdk` 37 · AGP 9.2.1 · Kotlin 2.2.10

## Build & run

1. Open the project in **Android Studio** and let Gradle sync.
2. **Run** on a device or emulator with **Google Play Services**, Android 10 (API 29)+.
   ML Kit models download on first use, so the device needs network access.
3. **Release build** is signed with the debug key, so it installs straight from Android
   Studio (Build → Build APK(s), or select the `release` variant). R8/minify is intentionally
   disabled — it strips PdfBox/ML Kit internals at runtime.

## Notes

- Requires Google Play Services (the scanner and OCR ship through it) — a plain AOSP
  emulator without Play won't run the scan/OCR features.
- Dark mode only, by design.
