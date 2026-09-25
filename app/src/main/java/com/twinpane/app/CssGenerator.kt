package com.twinpane.app

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CssGenerator {

    val flexboxPresets = listOf(
        "Center Alignment" to """display: flex;
justify-content: center;
align-items: center;""",
        "Space Between Row" to """display: flex;
justify-content: space-between;
align-items: center;""",
        "Column Stack" to """display: flex;
flex-direction: column;
gap: 16px;""",
        "Grid 2 Columns" to """display: grid;
grid-template-columns: repeat(2, 1fr);
gap: 16px;""",
    )

    val shadowPresets = listOf(
        "Soft Elevation" to "box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);",
        "Glow Purple" to "box-shadow: 0 0 20px rgba(139, 92, 246, 0.5);",
        "Neumorphism Dark" to "box-shadow: 5px 5px 10px #080a0e, -5px -5px 10px #141822;",
        "Glassmorphism Backdrop" to """background: rgba(255, 255, 255, 0.05);
backdrop-filter: blur(12px);
-webkit-backdrop-filter: blur(12px);
border: 1px solid rgba(255, 255, 255, 0.1);""",
    )

    val gradientPresets = listOf(
        "Cyberpunk Sunset" to "background: linear-gradient(135deg, #FF007A, #7C3AED);",
        "Neon Emerald" to "background: linear-gradient(135deg, #059669, #06B6D4);",
        "Midnight Blue" to "background: linear-gradient(135deg, #0F172A, #1E1B4B);",
        "Warm Amber" to "background: linear-gradient(135deg, #F59E0B, #EF4444);",
    )

    fun showDialog(context: Context, onCssSelected: (String) -> Unit) {
        val categories = arrayOf("Flexbox & Grid", "Box Shadow & Glass", "Gradients")
        MaterialAlertDialogBuilder(context)
            .setTitle("Visual CSS Generator")
            .setItems(categories) { _, categoryIndex ->
                val list = when (categoryIndex) {
                    0 -> flexboxPresets
                    1 -> shadowPresets
                    else -> gradientPresets
                }
                val titles = list.map { it.first }.toTypedArray()
                MaterialAlertDialogBuilder(context)
                    .setTitle(categories[categoryIndex])
                    .setItems(titles) { _, itemIndex ->
                        onCssSelected(list[itemIndex].second)
                    }
                    .show()
            }
            .show()
    }
}
