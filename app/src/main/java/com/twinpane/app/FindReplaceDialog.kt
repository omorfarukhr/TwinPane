package com.twinpane.app

import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FindReplaceDialog {

    fun show(
        context: Context,
        currentText: String,
        onTextReplaced: (String) -> Unit,
    ) {
        val layout = LinearLayoutView(context)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Find & Replace")
            .setView(layout.root)
            .setNegativeButton("Close", null)
            .create()

        layout.btnReplaceAll.setOnClickListener {
            val find = layout.etFind.text.toString()
            val replace = layout.etReplace.text.toString()
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

    private class LinearLayoutView(context: Context) {
        val etFind = EditText(context).apply {
            hint = "Find text..."
            setSingleLine()
        }
        val etReplace = EditText(context).apply {
            hint = "Replace with..."
            setSingleLine()
        }
        val btnReplaceAll = Button(context).apply {
            text = "Replace All"
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            addView(etFind)
            addView(etReplace)
            addView(btnReplaceAll)
        }
    }
}
