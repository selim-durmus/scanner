package com.tuttoposto.scanner.data

import android.graphics.Bitmap
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tuttoposto.scanner.logic.OcrLine
import kotlinx.coroutines.tasks.await

/** On-device text recognition (Latin script) through Play services. */
class Ocr {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Thrown while Play services is still downloading the model; worth retrying later. */
    class NotReady(cause: Throwable) : Exception(cause)

    suspend fun read(bitmap: Bitmap): List<OcrLine> {
        val result = try {
            client.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (e: MlKitException) {
            if (e.errorCode == MlKitException.UNAVAILABLE) throw NotReady(e) else throw e
        }
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val out = mutableListOf<OcrLine>()
        result.textBlocks.forEachIndexed { b, block ->
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isEmpty()) continue
                out += OcrLine(
                    text = text,
                    block = b,
                    left = (box.left / w).coerceIn(0f, 1f),
                    top = (box.top / h).coerceIn(0f, 1f),
                    right = (box.right / w).coerceIn(0f, 1f),
                    bottom = (box.bottom / h).coerceIn(0f, 1f),
                )
            }
        }
        return out
    }

    companion object {
        /** About 180 dpi across an A4/Letter page: plenty for recognition, quick to render. */
        const val RENDER_WIDTH = 1500
    }
}
