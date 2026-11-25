package com.app.inktwin.core.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Scanned character candidate from a photo of handwritten notes.
 */
data class ScannedCharCandidate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val proposedChar: String,          // ML Kit's best guess
    val alphaMask: Bitmap,             // Transparent PNG with ink in opaque black
    val advanceWidth: Float,           // Estimated advance width in pixels
    val baselineY: Float,              // Baseline offset from top of crop
    var isSelected: Boolean = true     // User can deselect bad crops
) {
    fun toGlyphVariant(variantIndex: Int): com.app.inktwin.core.glyph.GlyphVariant {
        return com.app.inktwin.core.glyph.GlyphVariant(
            variantIndex = variantIndex,
            vectorCommands = emptyList(),
            alphaMaskBitmap = alphaMask,
            bitmapWidth = alphaMask.width,
            bitmapHeight = alphaMask.height,
            advanceWidth = advanceWidth,
            leftBearing = 0f,
            topBearing = 0f,
            ascent = baselineY,
            descent = alphaMask.height.toFloat() - baselineY
        )
    }
}

/**
 * Pure-Kotlin image processing pipeline for plain-paper handwriting scans.
 * Uses Integral Image (Summed-Area Table) for fast adaptive thresholding
 * without any native OpenCV .so dependencies.
 */
object PlainPaperScanner {

    private const val MAX_DIMENSION = 2048
    private const val MIN_CHAR_WIDTH = 6
    private const val MIN_CHAR_HEIGHT = 8
    private const val DEFAULT_WINDOW_RATIO = 1f / 24f  // ~width/24 window size

    /**
     * Binarizes a page image using adaptive Bradley-Roth / Sauvola thresholding.
     * Returns a bitmap where dark ink becomes opaque black (0xFF000000)
     * and paper/shadows become fully transparent (0x00000000).
     *
     * @param bitmap Input bitmap (color or grayscale)
     * @param sensitivity Sensitivity factor (0.05-0.25), lower = more sensitive to faint ink
     * @return ARGB_8888 bitmap with transparent background and opaque ink
     */
    fun binarizePage(bitmap: Bitmap, sensitivity: Float = 0.15f): Bitmap {
        // Downscale if too large
        val scaledBitmap = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val width = scaledBitmap.width
        val height = scaledBitmap.height

        // Extract grayscale luminance (0.0 - 1.0)
        val gray = IntArray(width * height)
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in 0 until pixels.size) {
            val p = pixels[i]
            // Luminance: 0.299*R + 0.587*G + 0.114*B
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            gray[i] = (lum * 1000).toInt() // Scale to 0-1000 for integer math
        }

        // Build integral image (summed-area table)
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0L
            val rowOffset = y * width
            val intRowOffset = (y + 1) * (width + 1)
            val intPrevRowOffset = y * (width + 1)
            for (x in 0 until width) {
                val idx = rowOffset + x
                rowSum += gray[idx].toLong()
                integral[intRowOffset + x + 1] = integral[intPrevRowOffset + x + 1] + rowSum
            }
        }

        // Adaptive thresholding
        val windowSize = maxOf(3, (width * DEFAULT_WINDOW_RATIO).toInt())
        val halfWindow = windowSize / 2
        val k = sensitivity // sensitivity factor

        val output = IntArray(width * height)
        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                // Window bounds
                val x1 = maxOf(0, x - halfWindow)
                val y1 = maxOf(0, y - halfWindow)
                val x2 = minOf(width - 1, x + halfWindow)
                val y2 = minOf(height - 1, y + halfWindow)

                // Get sum from integral image
                val count = ((x2 - x1 + 1) * (y2 - y1 + 1)).toLong()
                val sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)]
                    - integral[y1 * (width + 1) + (x2 + 1)]
                    - integral[(y2 + 1) * (width + 1) + x1]
                    + integral[y1 * (width + 1) + x1]

                val mean = sum.toFloat() / count
                val threshold = mean * (1f - k)

                val pixelValue = gray[y * width + x].toFloat()
                if (pixelValue < threshold) {
                    // Ink: opaque black
                    output[y * width + x] = -0x1000000
                } else {
                    // Paper: transparent
                    output[y * width + x] = 0
                }
            }
        }

        outBitmap.setPixels(output, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Extracts character candidates from a binarized page using ML Kit Text Recognition.
     *
     * @param originalBitmap Original color image (for context)
     * @param binarizedBitmap Binarized transparent bitmap from binarizePage()
     * @return List of ScannedCharCandidate with alpha masks
     */
    suspend fun extractCandidates(
        originalBitmap: Bitmap,
        binarizedBitmap: Bitmap
    ): List<ScannedCharCandidate> {
        val candidates = mutableListOf<ScannedCharCandidate>()

        // ML Kit Text Recognition
        val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(originalBitmap, 0)
        val result: Text = recognizer.process(inputImage).await()

        // Process each line -> element (word) -> symbol (character)
        for (block in result.textBlocks) {
            for (line in block.lines) {
                // Get line baseline for vertical alignment
                val lineBox = line.boundingBox ?: continue
                val lineBottom = lineBox.bottom
                val lineTop = lineBox.top

                for (element in line.elements) {
                    val elementText = element.text
                    val elementBox = element.boundingBox ?: continue

                    // Try to get individual symbols
                    val symbols = element.symbols

                    if (symbols.isNotEmpty()) {
                        // ML Kit provided individual character boxes
                        for (symbol in symbols) {
                            val symbolText = symbol.text
                            val symbolBox = symbol.boundingBox ?: continue

                            val crop = cropAndTrim(
                                binarizedBitmap,
                                symbolBox,
                                lineBottom
                            )
                            crop?.let { candidates.add(it) }
                        }
                    } else if (elementText.length > 1) {
                        // No symbols - split word into characters using vertical projection
                        val wordCandidates = splitWordByProjection(
                            binarizedBitmap,
                            elementBox,
                            elementText,
                            lineBottom
                        )
                        candidates.addAll(wordCandidates)
                    } else {
                        // Single character word
                        val crop = cropAndTrim(
                            binarizedBitmap,
                            elementBox,
                            lineBottom
                        )
                        crop?.let { candidates.add(it) }
                    }
                }
            }
        }

        recognizer.close()
        return candidates
    }

    /**
     * Crops a character from the binarized bitmap and trims transparent padding.
     * Preserves vertical baseline offset relative to the line's bottom.
     */
    private fun cropAndTrim(
        binarized: Bitmap,
        box: Rect,
        lineBottom: Int
    ): ScannedCharCandidate? {
        val cropRect = Rect(
            maxOf(0, box.left),
            maxOf(0, box.top),
            minOf(binarized.width, box.right),
            minOf(binarized.height, box.bottom)
        )

        if (cropRect.width() < MIN_CHAR_WIDTH || cropRect.height() < MIN_CHAR_HEIGHT) {
            return null
        }

        // Find actual ink bounds to trim excess transparent space
        val pixels = IntArray(cropRect.width() * cropRect.height())
        binarized.getPixels(pixels, 0, cropRect.width(), cropRect.left, cropRect.top, cropRect.width(), cropRect.height())

        var minX = cropRect.width()
        var maxX = 0
        var minY = cropRect.height()
        var maxY = 0
        var inkFound = false

        for (y in 0 until cropRect.height()) {
            for (x in 0 until cropRect.width()) {
                val p = pixels[y * cropRect.width() + x]
                if (Color.alpha(p) > 0) { // Opaque pixel (ink)
                    inkFound = true
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        if (!inkFound || minX > maxX || minY > maxY) return null

        // Trim to ink bounds with small padding
        val padding = 2
        val trimmedRect = Rect(
            maxOf(0, minX - padding),
            maxOf(0, minY - padding),
            minOf(cropRect.width(), maxX + padding + 1),
            minOf(cropRect.height(), maxY + padding + 1)
        )

        val trimmedWidth = trimmedRect.width()
        val trimmedHeight = trimmedRect.height()

        if (trimmedWidth < MIN_CHAR_WIDTH || trimmedHeight < MIN_CHAR_HEIGHT) return null

        // Create trimmed bitmap
        val trimmedPixels = IntArray(trimmedWidth * trimmedHeight)
        for (y in 0 until trimmedHeight) {
            for (x in 0 until trimmedWidth) {
                val srcX = trimmedRect.left + x
                val srcY = trimmedRect.top + y
                if (srcX < cropRect.width() && srcY < cropRect.height()) {
                    trimmedPixels[y * trimmedWidth + x] = pixels[srcY * cropRect.width() + srcX]
                }
            }
        }

        val alphaMask = Bitmap.createBitmap(trimmedWidth, trimmedHeight, Bitmap.Config.ARGB_8888)
        alphaMask.setPixels(trimmedPixels, 0, trimmedWidth, 0, 0, trimmedWidth, trimmedHeight)

        // Baseline Y relative to top of trimmed crop
        val baselineY = (lineBottom - (cropRect.top + trimmedRect.top)).toFloat()

        return ScannedCharCandidate(
            proposedChar = "", // Will be set by caller
            alphaMask = alphaMask,
            advanceWidth = trimmedWidth.toFloat(),
            baselineY = baselineY
        )
    }

    /**
     * Splits a word box into individual characters using vertical projection valley finding.
     */
    private fun splitWordByProjection(
        binarized: Bitmap,
        wordBox: Rect,
        wordText: String,
        lineBottom: Int
    ): List<ScannedCharCandidate> {
        val candidates = mutableListOf<ScannedCharCandidate>()

        // Crop word region
        val cropRect = Rect(
            maxOf(0, wordBox.left),
            maxOf(0, wordBox.top),
            minOf(binarized.width, wordBox.right),
            minOf(binarized.height, wordBox.bottom)
        )

        if (cropRect.width() < MIN_CHAR_WIDTH || cropRect.height() < MIN_CHAR_HEIGHT) {
            return candidates
        }

        val pixels = IntArray(cropRect.width() * cropRect.height())
        binarized.getPixels(pixels, 0, cropRect.width(), cropRect.left, cropRect.top, cropRect.width(), cropRect.height())

        // Vertical projection: count ink pixels per column
        val projection = IntArray(cropRect.width())
        for (x in 0 until cropRect.width()) {
            var count = 0
            for (y in 0 until cropRect.height()) {
                val p = pixels[y * cropRect.width() + x]
                if (Color.alpha(p) > 0) count++
            }
            projection[x] = count
        }

        // Find valleys (local minima in projection) - character boundaries
        val valleys = findValleys(projection)

        // Map valleys to character positions
        // Distribute characters evenly between valleys
        val charCount = wordText.length
        if (charCount == 0) return candidates

        var prevX = 0
        var charIndex = 0

        for (valleyX in valleys) {
            if (charIndex >= charCount) break

            val charBox = Rect(
                cropRect.left + prevX,
                cropRect.top,
                cropRect.left + valleyX,
                cropRect.bottom
            )

            val crop = cropAndTrim(binarized, charBox, lineBottom)
            crop?.let {
                candidates.add(it.copy(proposedChar = wordText[charIndex].toString()))
            }

            prevX = valleyX
            charIndex++
        }

        // Last character
        if (charIndex < charCount) {
            val charBox = Rect(
                cropRect.left + prevX,
                cropRect.top,
                cropRect.right,
                cropRect.bottom
            )
            val crop = cropAndTrim(binarized, charBox, lineBottom)
            crop?.let {
                candidates.add(it.copy(proposedChar = wordText[charIndex].toString()))
            }
        }

        return candidates
    }

    private fun findValleys(projection: IntArray): List<Int> {
        val valleys = mutableListOf<Int>()
        val window = maxOf(3, projection.size / 20)
        val maxVal = projection.maxOrNull() ?: 0
        val threshold = (maxVal * 0.1f).toInt()

        for (i in window until projection.size - window) {
            val isLocalMin = (i - window until i + window).all { projection[i] <= projection[it] }
            val isLow = projection[i] <= threshold
            if (isLocalMin && isLow) {
                // Avoid too-close valleys
                if (valleys.isEmpty() || i - valleys.last() > window) {
                    valleys.add(i)
                }
            }
        }

        return valleys
    }
}