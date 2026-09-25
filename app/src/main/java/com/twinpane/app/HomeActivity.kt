package com.twinpane.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("twinpane_projects_list", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeScroll)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.btnAbout).setOnClickListener { showAboutDialog() }
        findViewById<Button>(R.id.btnNewProject).setOnClickListener { promptNewProject() }
        findViewById<Button>(R.id.btnOpenSandbox).setOnClickListener { openEditor() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { openEditor() }
        findViewById<View>(R.id.githubCard).setOnClickListener { openGitHubRepo() }

        setupQuickTools()
        renderRecentProjects()
        renderTemplates()
    }

    companion object {
        private const val GITHUB_REPO_URL = "https://github.com/omorfarukhr/TwinPane"
        private const val AUTHOR_NAME = "Omor Faruk (@omorfarukhr)"
    }

    private fun openGitHubRepo() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, GITHUB_REPO_URL.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        renderRecentProjects()
        renderTemplates()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showAboutDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(16))

            val logo = ImageView(this@HomeActivity).apply {
                setImageResource(R.mipmap.ic_launcher)
                layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            }
            val title = TextView(this@HomeActivity).apply {
                text = getString(R.string.app_ide_title, getString(R.string.app_name))
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, R.color.text))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(4))
            }
            val version = TextView(this@HomeActivity).apply {
                text = "v1.0.0 · Created by $AUTHOR_NAME\n$GITHUB_REPO_URL"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                gravity = Gravity.CENTER
            }
            addView(logo)
            addView(title)
            addView(version)
        }

        MaterialAlertDialogBuilder(this)
            .setView(container)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openEditor(
        action: String? = null,
        projectName: String? = null,
        templateName: String? = null,
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (action != null) putExtra("action", action)
            if (projectName != null) putExtra("project_name", projectName)
            if (templateName != null) putExtra("template_name", templateName)
        }
        startActivity(intent)
    }

    private fun promptNewProject() {
        val input = EditText(this).apply {
            hint = "My Web App"
            setSingleLine()
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("New Project")
            .setMessage("Enter project name:")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveProjectName(name)
                    openEditor(action = "NEW_PROJECT", projectName = name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getProjects(): MutableList<String> {
        val set = prefs.getStringSet("projects", null) ?: mutableSetOf("TwinPane Workspace")
        return set.toMutableList()
    }

    private fun saveProjectName(name: String) {
        val list = getProjects()
        if (!list.contains(name)) list.add(0, name)
        prefs.edit { putStringSet("projects", list.toSet()) }
    }

    private fun deleteProject(name: String) {
        val list = getProjects()
        list.remove(name)
        prefs.edit { putStringSet("projects", list.toSet()) }
        renderRecentProjects()
    }

    private fun renderRecentProjects() {
        val container = findViewById<LinearLayout>(R.id.recentProjectsContainer)
        container.removeAllViews()

        val rippleBorderless = TypedValue().also {
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }.resourceId

        val projects = getProjects()
        for (proj in projects) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_container)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(8))
                }
                layoutParams = params

                val icon = TextView(context).apply {
                    text = "🌐"
                    textSize = 18f
                    setPadding(0, 0, dp(12), 0)
                }

                val info = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                    val nameTv = TextView(context).apply {
                        text = proj
                        textSize = 15f
                        setTextColor(ContextCompat.getColor(context, R.color.text))
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    val subTv = TextView(context).apply {
                        text = "HTML / CSS / JS Project"
                        textSize = 11f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    }
                    addView(nameTv)
                    addView(subTv)
                }

                val btnOpen = Button(context).apply {
                    text = "Open"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(context, R.color.accent))
                    setBackgroundResource(rippleBorderless)
                    setOnClickListener { openEditor(action = "OPEN_PROJECT", projectName = proj) }
                }

                val btnDelete = ImageButton(context).apply {
                    setImageResource(R.drawable.ic_close)
                    setBackgroundResource(rippleBorderless)
                    setColorFilter(ContextCompat.getColor(context, R.color.gutter))
                    setPadding(dp(6), dp(6), dp(6), dp(6))
                    setOnClickListener { deleteProject(proj) }
                }

                setOnClickListener { openEditor(action = "OPEN_PROJECT", projectName = proj) }

                addView(icon)
                addView(info)
                addView(btnOpen)
                addView(btnDelete)
            }
            container.addView(card)
        }
    }

    private fun renderTemplates() {
        val container = findViewById<LinearLayout>(R.id.templatesContainer)
        container.removeAllViews()

        val customTemplates = CustomTemplatesManager.getCustomTemplates(this)
        val allTemplates = (Templates.all + customTemplates).distinctBy { it.name }

        for (tmpl in allTemplates) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(context, R.drawable.bg_card_container)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                val params = LinearLayout.LayoutParams(dp(140), dp(110)).apply {
                    setMargins(0, 0, dp(10), 0)
                }
                layoutParams = params
                isClickable = true
                isFocusable = true

                val badge = TextView(context).apply {
                    text = if (tmpl in customTemplates) "⭐ Custom" else "📑 Template"
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(context, R.color.accent_2))
                }

                val nameTv = TextView(context).apply {
                    text = tmpl.name
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.text))
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 2
                    val p = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    ).apply {
                        setMargins(0, dp(6), 0, 0)
                    }
                    layoutParams = p
                }

                val actionTv = TextView(context).apply {
                    text = "Use Starter →"
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.accent))
                }

                setOnClickListener {
                    openEditor(
                        action = "LOAD_TEMPLATE",
                        templateName = tmpl.name,
                        projectName = tmpl.name
                    )
                }

                addView(badge)
                addView(nameTv)
                addView(actionTv)
            }
            container.addView(card)
        }
    }

    private fun setupQuickTools() {
        findViewById<View>(R.id.toolCssGen).setOnClickListener {
            CssGenerator.showDialog(this) { openEditor(action = "OPEN_PROJECT") }
        }
        findViewById<View>(R.id.toolCdn).setOnClickListener {
            openEditor(action = "OPEN_PROJECT")
        }
        findViewById<View>(R.id.toolServer).setOnClickListener {
            val ip = LocalWebServer.getLocalIpAddress() ?: "127.0.0.1"
            Toast.makeText(this, "Local Wi-Fi IP: $ip (Open editor to start server)", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.toolStorage).setOnClickListener {
            openEditor(action = "OPEN_PROJECT")
        }
    }
}
