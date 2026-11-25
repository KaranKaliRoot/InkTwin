# InkTwin

> **Turn your real handwriting into a reusable digital font.**
> Draw 52 letters (a–z, A–Z), color your notes, and export beautifully rendered handwriting pages — PNG or PDF.

Designed & Developed by **Rudra Bhardwaj**

---

## What It Does

InkTwin is a full Android application that captures your handwriting and turns it into a digital typeface you can write with. Three tightly integrated flows:

1. **Draw 52 Letters (Capture)** — Draw each letter on a canvas; strokes are normalized, quantized, and persisted per-character as font variants.
2. **Write & Color (Editor)** — Type a note and apply colored spans at selection / word / line / paragraph granularity, with a custom RGB ink picker.
3. **Preview & Export (Preview)** — Render your note using your *actual drawn glyphs* onto A4 pages, then export to PNG or PDF.

## Architecture

Built with modern, idiomatic Android tooling:

- **Kotlin 2.0** + **Jetpack Compose** (Material 3) for a fully declarative UI
- **Hilt** for dependency injection (`@HiltViewModel`, KSP-processed)
- **Room** database with custom `TypeConverters` for `PathCommand`, `ColorSpan`, and `PageSettings`
- **Kotlinx Serialization** for domain model persistence
- **ML Kit Text Recognition v2** for the Scan Plain Paper flow
- **Pure-Kotlin integral-image adaptive thresholding** for plain-paper binarization (no OpenCV)
- **Custom multi-page A4 vector/bitmap renderer** with red margins, page numbers, ruled lines, and letter-spacing
- **Android `PdfDocument`** for multi-page PDF export

### Data Flow

```
Canvas drag gestures
  → Compose `pointerInput` / `detectDragGestures`
  → `PathCommand` list (moveTo / lineTo / quadTo / cubicTo / close)
  → `CaptureViewModel.addStroke()` → Room `GlyphEntity` / `GlyphVariantEntity`
  → `StateFlow` UI updates → progress counter + glyph grid
```

## Features

- **Real-time stroke capture** with undo / clear / prev / next
- **Glyph normalization** — strokes are normalized to a consistent coordinate frame with advance-width, left-bearing, and baseline metrics
- **Multi-variant glyphs** — up to 5 variants per character
- **Category tabs** — `a-z`, `A-Z`, `0-9 & .`
- **Color spans** — selection, word, line, paragraph granularity with debounced auto-save
- **Custom RGB color picker** dialog
- **Preview rendering** with configurable font size, margins, ruled lines
- **PNG / PDF export** — current page or entire document

## Getting Started

```bash
# Build the debug APK
./gradlew assembleDebug

# Install & run on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.app.inktwin/.MainActivity
```

> Requires Android SDK 34, minSdk 26, and a device/emulator with USB debugging enabled.

## Project Structure

```
app/src/main/java/com/app/inktwin/
├── MainActivity.kt                      # NavHost: Capture → Editor → Preview
├── core/
│   ├── glyph/                           # PathCommand, Glyph, GlyphNormalizer, GlyphVariant
│   ├── note/                            # ColorSpan, NoteDraft, PageSettings
│   ├── pipeline/                        # PlainPaperScanner (integral-image thresholding)
│   ├── profile/                         # HandwritingProfile, SourceType
│   └── render/                          # HandwritingPageRenderer, PageLayout
├── data/
│   ├── room/                            # Room entities, DAOs, TypeConverters
│   └── repository/                      # GlyphRepository, NoteDraftRepository, HandwritingProfileRepository
└── ui/
    ├── screen/
    │   ├── capture/CaptureScreen.kt     # Draw 52 Letters + Scan Plain Paper
    │   ├── editor/EditorScreen.kt        # Write & Color
    │   └── preview/PreviewScreen.kt       # Preview & Export
    └── navigation/NavigationGraph.kt
```

## License

This project is provided as-is for portfolio demonstration.