package com.twinpane.app

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import androidx.core.graphics.toColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ColorPickerDialog {

    private val presetColors = listOf(
        "#8B5CF6", "#06B6D4", "#10B981", "#F59E0B", "#F43F5E",
        "#3B82F6", "#EC4899", "#6366F1", "#14B8A6", "#84CC16",
        "#EAB308", "#EF4444", "#000000", "#1E293B", "#64748B",
        "#94A3B8", "#E2E8F0", "#FFFFFF", "#FF5722", "#00BCD4",
    )

    fun show(context: Context, onColorSelected: (String) -> Unit) {
        val gridView = GridView(context).apply {
            numColumns = 5
            verticalSpacing = 16
            horizontalSpacing = 16
            setPadding(32, 32, 32, 32)
            adapter = ColorAdapter(context, presetColors)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Select Color")
            .setView(gridView)
            .setNegativeButton("Cancel", null)
            .create()

        gridView.setOnItemClickListener { _, _, position, _ ->
            onColorSelected(presetColors[position])
            dialog.dismiss()
        }

        dialog.show()
    }

    private class ColorAdapter(
        private val context: Context,
        private val colors: List<String>,
    ) : BaseAdapter() {

        override fun getCount() = colors.size
        override fun getItem(position: Int) = colors[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val size = (44 * context.resources.displayMetrics.density).toInt()
            val view = convertView ?: View(context).apply {
                layoutParams = ViewGroup.LayoutParams(size, size)
            }
            try {
                view.setBackgroundColor(colors[position].toColorInt())
            } catch (_: Exception) {
                view.setBackgroundColor(Color.GRAY)
            }
            return view
        }
    }
}
