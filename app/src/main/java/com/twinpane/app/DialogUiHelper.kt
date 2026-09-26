package com.twinpane.app

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class CustomListItem(
    val title: String,
    val subtitle: String? = null,
    val iconRes: Int? = null,
    val badgeText: String? = null,
    val action: (() -> Unit)? = null,
)

object DialogUiHelper {

    fun createStyledInput(context: Context, hintText: String, initialText: String = ""): Pair<View, EditText> {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = FrameLayout(context).apply {
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }

        val input = EditText(context).apply {
            hint = hintText
            setText(initialText)
            textSize = 15f
            setSingleLine()
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setHintTextColor(ContextCompat.getColor(context, R.color.gutter))
            background = ContextCompat.getDrawable(context, R.drawable.bg_symbol_key)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        container.addView(input)
        return container to input
    }

    fun showCustomListDialog(
        context: Context,
        title: String,
        items: List<CustomListItem>,
    ) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val listView = ListView(context).apply {
            dividerHeight = 0
            setBackgroundColor(ContextCompat.getColor(context, R.color.bg))
            setPadding(dp(12), dp(8), dp(12), dp(12))
            adapter = CustomListAdapter(context, items)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            items[position].action?.invoke()
        }

        dialog.show()
    }

    private class CustomListAdapter(
        private val context: Context,
        private val items: List<CustomListItem>,
    ) : BaseAdapter() {

        private val density = context.resources.displayMetrics.density
        private fun dp(v: Int) = (v * density).toInt()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_container)
                setPadding(dp(14), dp(12), dp(14), dp(12))

                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    setMargins(0, 0, 0, dp(8))
                }
                layoutParams = params

                val ripple = TypedValue().also {
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }.resourceId
                foreground = ContextCompat.getDrawable(context, ripple)
            }

            if (item.iconRes != null) {
                val iconView = ImageView(context).apply {
                    setImageResource(item.iconRes)
                    setColorFilter(ContextCompat.getColor(context, R.color.accent))
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_symbol_key)
                    val p = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                        setMargins(0, 0, dp(12), 0)
                    }
                    layoutParams = p
                }
                card.addView(iconView)
            }

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val titleTv = TextView(context).apply {
                    text = item.title
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(context, R.color.text))
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(titleTv)

                if (!item.subtitle.isNullOrBlank()) {
                    val subTv = TextView(context).apply {
                        text = item.subtitle
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(0, dp(2), 0, 0)
                    }
                    addView(subTv)
                }
            }
            card.addView(info)

            if (!item.badgeText.isNullOrBlank()) {
                val badgeTv = TextView(context).apply {
                    text = item.badgeText
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.accent_2))
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    background = ContextCompat.getDrawable(context, R.drawable.bg_symbol_key)
                }
                card.addView(badgeTv)
            }

            return card
        }
    }
}
