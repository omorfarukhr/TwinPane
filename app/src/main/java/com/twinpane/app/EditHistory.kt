package com.twinpane.app

import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText

class EditHistory(
    private val editor: EditText,
    private val currentTab: () -> Int,
    private val isSwitching: () -> Boolean,
    private val onStateChanged: () -> Unit
) {
    companion object {
        private const val TABS = 3
        // এর বেশি সময় থামলে নতুন undo step
        private const val GROUP_MS = 1000L
        // SECURITY: memory সীমিত রাখা (প্রতিটা tab-এ)
        private const val MAX_STEPS = 100
        private const val MAX_CHARS = 1_000_000
    }

    private class State(val text: String, val cursor: Int)

    // প্রতিটা tab-এর আলাদা history (শুধু memory-তে, disk-এ save হয় না)
    private val undoStack = List(TABS) { ArrayDeque<State>() }
    private val redoStack = List(TABS) { ArrayDeque<State>() }
    private val lastEdit = LongArray(TABS)
    private var applying = false
    private var lastCanUndo = false
    private var lastCanRedo = false

    val canUndo: Boolean get() = undoStack[tab()].isNotEmpty()
    val canRedo: Boolean get() = redoStack[tab()].isNotEmpty()

    private fun tab() = currentTab().coerceIn(0, TABS - 1)

    init {
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Undo/Redo নিজে বা tab বদলানোর সময়কার পরিবর্তন history-তে যায় না
                if (applying || isSwitching()) return
                val t = tab()
                val now = SystemClock.uptimeMillis()
                if (now - lastEdit[t] > GROUP_MS) {
                    push(undoStack[t], State(s?.toString() ?: "", editor.selectionEnd.coerceAtLeast(0)))
                }
                lastEdit[t] = now
                redoStack[t].clear()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) = notifyIfChanged()
        })

        // Hardware keyboard: Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z
        editor.setOnKeyListener { _, keyCode, e ->
            if (e.action != KeyEvent.ACTION_DOWN || !e.isCtrlPressed) return@setOnKeyListener false
            when {
                keyCode == KeyEvent.KEYCODE_Z && e.isShiftPressed -> { redo(); true }
                keyCode == KeyEvent.KEYCODE_Z -> { undo(); true }
                keyCode == KeyEvent.KEYCODE_Y -> { redo(); true }
                else -> false
            }
        }
    }

    fun undo(): Boolean {
        val t = tab()
        val state = undoStack[t].removeLastOrNull() ?: return false
        push(redoStack[t], current())
        apply(state)
        lastEdit[t] = 0
        return true
    }

    fun redo(): Boolean {
        val t = tab()
        val state = redoStack[t].removeLastOrNull() ?: return false
        push(undoStack[t], current())
        apply(state)
        lastEdit[t] = 0
        return true
    }

    /** নতুন project বা template load হলে পুরনো history মুছে ফেলা */
    fun clear() {
        undoStack.forEach { it.clear() }
        redoStack.forEach { it.clear() }
        lastEdit.fill(0)
        notifyIfChanged()
    }

    private fun current() =
        State(editor.text?.toString() ?: "", editor.selectionEnd.coerceAtLeast(0))

    private fun apply(state: State) {
        applying = true
        try {
            editor.setText(state.text)
            editor.setSelection(state.cursor.coerceIn(0, editor.length()))
        } finally {
            applying = false
        }
        editor.requestFocus()
    }

    // SECURITY: step আর মোট অক্ষরের সীমা পার হলে সবচেয়ে পুরনো step মুছে যায়
    private fun push(stack: ArrayDeque<State>, state: State) {
        if (stack.lastOrNull()?.text == state.text) return
        stack.addLast(state)
        while (stack.size > MAX_STEPS) stack.removeFirst()
        var total = stack.sumOf { it.text.length }
        while (total > MAX_CHARS && stack.size > 1) {
            total -= stack.removeFirst().text.length
        }
    }

    private fun notifyIfChanged() {
        val u = canUndo
        val r = canRedo
        if (u != lastCanUndo || r != lastCanRedo) {
            lastCanUndo = u
            lastCanRedo = r
            onStateChanged()
        }
    }
}
