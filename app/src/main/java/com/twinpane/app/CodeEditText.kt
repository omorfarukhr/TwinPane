package com.twinpane.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat

class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val gutterWidth = 46 * density

    /** যে line-গুলোতে error আছে (1 থেকে শুরু) */
    var errorLines: Set<Int> = emptySet()
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val gutterPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gutter_bg)
    }
    private val gutterBorderPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gutter_border)
        strokeWidth = 1 * density
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gutter)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }
    private val activeNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val errorNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.error)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val activeLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.active_line)
    }
    private val activeLineBarPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.active_line_border)
    }
    private val errorLinePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.error_line)
    }
    private val errorMarkPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.error)
    }

    init {
        typeface = Typeface.MONOSPACE
        textSize = 14f
        letterSpacing = 0.02f
        setPadding((gutterWidth + (10 * density)).toInt(), paddingTop, paddingRight, paddingBottom)
        setHorizontallyScrolling(false)
    }

    /** Cursor নির্দিষ্ট line-এর শুরুতে নিয়ে যাওয়া */
    fun goToLine(lineNumber: Int) {
        val t = text ?: return
        var line = 1
        var idx = 0
        while ((line < lineNumber) && (idx < t.length)) {
            if (t[idx] == '\n') line++
            idx++
        }
        requestFocus()
        setSelection(idx.coerceIn(0, t.length))
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
    }

    var isAutoCloseEnabled = true
    private var isFormatting = false

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        if (!isAutoCloseEnabled || isFormatting || (lengthAfter != 1) || (text == null)) return

        val inserted = text[start]
        val editable = this.text ?: return

        isFormatting = true
        when (inserted) {
            '{' -> insertPair(start, editable, "}")
            '(' -> insertPair(start, editable, ")")
            '[' -> insertPair(start, editable, "]")
            '"' -> insertPair(start, editable, "\"")
            '\'' -> insertPair(start, editable, "'")
            '>' -> handleTagClosing(start, editable)
        }
        isFormatting = false
    }

    private fun insertPair(start: Int, editable: Editable, closing: String) {
        if (((start + 1) < editable.length) && (editable[start + 1].toString() == closing)) return
        editable.insert(start + 1, closing)
        setSelection(start + 1)
    }

    private fun handleTagClosing(start: Int, editable: Editable) {
        val sub = editable.subSequence(0, start).toString()
        val lastOpen = sub.lastIndexOf('<')
        if (lastOpen < 0) return

        val tagContent = sub.substring(lastOpen + 1).trim()
        if (tagContent.startsWith('/') || tagContent.startsWith('!') || tagContent.startsWith('?')) return

        val tagName = tagContent.split(Regex("""\s+""")).firstOrNull() ?: return
        val voidTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr",
        )

        if ((tagName.lowercase() !in voidTags) && tagName.isNotBlank()) {
            val closingTag = "</$tagName>"
            editable.insert(start + 1, closingTag)
            setSelection(start + 1)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val lay = layout
        val content = text
        if ((lay != null) && (content != null)) {
            // 1. Draw Active Line Background
            val selStart = selectionStart
            if (selStart in (0..content.length)) {
                val currentLine = lay.getLineForOffset(selStart)
                val top = (lay.getLineTop(currentLine) + totalPaddingTop).toFloat()
                val bottom = (lay.getLineBottom(currentLine) + totalPaddingTop).toFloat()
                canvas.drawRect(
                    scrollX + gutterWidth, top,
                    (scrollX + width).toFloat(), bottom, activeLinePaint,
                )
                canvas.drawRect(
                    scrollX + gutterWidth, top,
                    scrollX + gutterWidth + (3 * density), bottom, activeLineBarPaint,
                )
            }

            // 2. Draw Gutter Background & Border Line
            canvas.drawRect(
                scrollX.toFloat(), scrollY.toFloat(),
                scrollX + gutterWidth, (scrollY + height).toFloat(), gutterPaint,
            )
            canvas.drawLine(
                scrollX + gutterWidth, scrollY.toFloat(),
                scrollX + gutterWidth, (scrollY + height).toFloat(), gutterBorderPaint,
            )

            numberPaint.textSize = textSize * 0.8f
            activeNumberPaint.textSize = numberPaint.textSize
            errorNumberPaint.textSize = numberPaint.textSize

            val first = lay.getLineForVertical(scrollY)
            val last = lay.getLineForVertical(scrollY + height)
            fun isLineStart(l: Int) = (l == 0) || (content[lay.getLineStart(l) - 1] == '\n')

            var number = 0
            for (i in 0 until lay.getLineStart(first)) if (content[i] == '\n') number++
            if (!isLineStart(first)) number++

            val activeLineIndex = if (selStart in (0..content.length)) lay.getLineForOffset(selStart) else -1

            for (line in first..last) {
                if (isLineStart(line)) {
                    number++
                    val isError = number in errorLines
                    val isActive = line == activeLineIndex

                    if (isError) {
                        val top = (lay.getLineTop(line) + totalPaddingTop).toFloat()
                        val bottom = (lay.getLineBottom(line) + totalPaddingTop).toFloat()
                        canvas.drawRect(scrollX.toFloat(), top, (scrollX + width).toFloat(), bottom, errorLinePaint)
                        canvas.drawRect(scrollX.toFloat(), top, scrollX + (3 * density), bottom, errorMarkPaint)
                    }

                    val y = (lay.getLineBaseline(line) + totalPaddingTop).toFloat()
                    val p = when {
                        isError -> errorNumberPaint
                        isActive -> activeNumberPaint
                        else -> numberPaint
                    }
                    canvas.drawText(
                        number.toString(), ((scrollX + gutterWidth) - (8 * density)), y, p,
                    )
                }
            }
        }
        super.onDraw(canvas)
    }
}
