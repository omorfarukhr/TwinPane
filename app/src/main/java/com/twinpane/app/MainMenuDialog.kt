package com.twinpane.app

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.ArrayDeque

data class MenuItemData(
    val title: String,
    val subtitle: String? = null,
    val icon: String? = null,
    val isSubMenu: Boolean = false,
    val isChecked: Boolean? = null,
    val subItems: List<MenuItemData>? = null,
    val dismissOnClick: Boolean = true,
    val action: (() -> Unit)? = null,
)

object MainMenuDialog {

    fun show(context: Context, mainMenuItems: List<MenuItemData>) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val stack = ArrayDeque<Pair<String, List<MenuItemData>>>()
        stack.push("Menu" to mainMenuItems)

        val rippleBorderless = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId

        // Header Views
        val btnBack = ImageButton(context).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setBackgroundResource(rippleBorderless)
            setColorFilter(ContextCompat.getColor(context, R.color.text))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }

        val tvTitle = TextView(context).apply {
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text))
            typeface = Typeface.DEFAULT_BOLD
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(dp(8), 0, dp(8), 0)
            layoutParams = params
        }

        val btnClose = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(rippleBorderless)
            setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(btnBack)
            addView(tvTitle)
            addView(btnClose)
        }

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
        }

        val listView = ListView(context).apply {
            dividerHeight = 0
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerLayout)
            addView(divider)
            addView(listView)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(root)
            .create()

        fun updateUI() {
            val (currentTitle, currentItems) = stack.peek() ?: ("Menu" to emptyList())
            tvTitle.text = currentTitle
            btnBack.visibility = if (stack.size > 1) View.VISIBLE else View.INVISIBLE

            listView.adapter = MenuAdapter(context, currentItems)
            listView.setOnItemClickListener { _, _, position, _ ->
                val item = currentItems[position]
                if (item.isSubMenu && (item.subItems != null)) {
                    stack.push(item.title to item.subItems)
                    updateUI()
                } else {
                    item.action?.invoke()
                    if (item.dismissOnClick) {
                        dialog.dismiss()
                    } else {
                        updateUI()
                    }
                }
            }
        }

        btnBack.setOnClickListener {
            if (stack.size > 1) {
                stack.pop()
                updateUI()
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.action == KeyEvent.ACTION_UP)) {
                if (stack.size > 1) {
                    stack.pop()
                    updateUI()
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        updateUI()
        dialog.show()
    }

    private class MenuAdapter(
        private val context: Context,
        private val items: List<MenuItemData>,
    ) : BaseAdapter() {

        private val density = context.resources.displayMetrics.density
        private fun dp(v: Int) = (v * density).toInt()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            val container = LinearLayout(context).apply {
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
                setBackgroundResource(ripple)
            }

            if (!item.icon.isNullOrBlank()) {
                val iconTv = TextView(context).apply {
                    text = item.icon
                    textSize = 18f
                    setPadding(0, 0, dp(12), 0)
                }
                container.addView(iconTv)
            }

            val infoLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val titleView = TextView(context).apply {
                    text = item.title
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(context, R.color.text))
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(titleView)

                if (!item.subtitle.isNullOrBlank()) {
                    val subView = TextView(context).apply {
                        text = item.subtitle
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(0, dp(2), 0, 0)
                    }
                    addView(subView)
                }
            }
            container.addView(infoLayout)

            if (item.isChecked != null) {
                val checkView = TextView(context).apply {
                    text = if (item.isChecked) "✓" else ""
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(context, R.color.accent))
                }
                container.addView(checkView)
            } else if (item.isSubMenu) {
                val arrowView = TextView(context).apply {
                    text = "›"
                    textSize = 18f
                    setTextColor(ContextCompat.getColor(context, R.color.gutter))
                }
                container.addView(arrowView)
            }

            return container
        }
    }
}
