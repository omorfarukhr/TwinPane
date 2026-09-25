package com.twinpane.app

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FindReplaceDialog {

    fun show(
        context: Context,
        currentText: String,
        onTextReplaced: (String) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val (findContainer, etFind) = DialogUiHelper.createStyledInput(context, "Find text...")
        val (replaceContainer, etReplace) = DialogUiHelper.createStyledInput(context, "Replace with...")

        val btnReplaceAll = Button(context).apply {
            text = "Replace All"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(20), dp(8), dp(20), dp(12))
            }
            layoutParams = params
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(findContainer)
            addView(replaceContainer)
            addView(btnReplaceAll)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Find & Replace")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()

        btnReplaceAll.setOnClickListener {
            val find = etFind.text.toString()
            val replace = etReplace.text.toString()
            if (find.isNotEmpty()) {
                val count = currentText.split(find).size - 1
                val updated = currentText.replace(find, replace)
                onTextReplaced(updated)
                Toast.makeText(context, "Replaced $count occurrence(s)", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
