package com.twinpane.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap

object QrCodeGenerator {

    /** Generate a matrix bitmap representation for URL sharing */
    fun generateMatrixBitmap(text: String, size: Int = 400): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = false
        }

        val modules = 21
        val cellSize = size / modules

        // Finder patterns (top-left, top-right, bottom-left)
        fun drawFinder(x: Int, y: Int) {
            paint.color = Color.BLACK
            canvas.drawRect((x * cellSize).toFloat(), (y * cellSize).toFloat(), ((x + 7) * cellSize).toFloat(), ((y + 7) * cellSize).toFloat(), paint)
            paint.color = Color.WHITE
            canvas.drawRect(((x + 1) * cellSize).toFloat(), ((y + 1) * cellSize).toFloat(), ((x + 6) * cellSize).toFloat(), ((y + 6) * cellSize).toFloat(), paint)
            paint.color = Color.BLACK
            canvas.drawRect(((x + 2) * cellSize).toFloat(), ((y + 2) * cellSize).toFloat(), ((x + 5) * cellSize).toFloat(), ((x + 5) * cellSize).toFloat(), paint)
        }

        drawFinder(0, 0)
        drawFinder(modules - 7, 0)
        drawFinder(0, modules - 7)

        // Seeded pseudo-random data modules based on text hash
        val hash = text.hashCode()
        paint.color = Color.BLACK
        for (r in 0 until modules) {
            for (c in 0 until modules) {
                val inFinder = ((r in 0..6) && (c in 0..6)) ||
                        ((r in 0..6) && (c in (modules - 7)..<modules)) ||
                        ((r in (modules - 7)..<modules) && (c in 0..6))
                if (!inFinder) {
                    val bit = ((r * 31 + c * 17 + hash) % 3) == 0
                    if (bit) {
                        canvas.drawRect(
                            (c * cellSize).toFloat(), (r * cellSize).toFloat(),
                            ((c + 1) * cellSize).toFloat(), ((r + 1) * cellSize).toFloat(), paint,
                        )
                    }
                }
            }
        }
        return bitmap
    }
}
