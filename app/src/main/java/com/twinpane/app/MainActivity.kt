package com.twinpane.app

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    companion object {
        // SECURITY: আলাদা, অস্তিত্বহীন origin (.invalid কখনো resolve হয় না)
        private const val SANDBOX_URL = "https://sandbox.twinpane.invalid/"
        private const val MAX_LOG_CHARS = 8000
        private const val MAX_SHARE_CHARS = 200_000
        private const val RENDER_DELAY_MS = 600L
        private const val HIGHLIGHT_DELAY_MS = 150L
        private const val TAB = "⇥"
        private val KEYS = listOf("html", "css", "js")
        private val SYMBOLS = listOf(
            "<", ">", "/", "=", "\"", "'", TAB,
            "{", "}", ";", ":", "(", ")", ".", "#",
            "[", "]", "!", "+", "-", "*", "&",
        )
    }

    // SECURITY: MODE_PRIVATE, অন্য কোনো app এই data পড়তে পারবে না
    private val prefs by lazy { getSharedPreferences("twinpane_code", MODE_PRIVATE) }

    private val code = mutableListOf<String>()
    private var currentTab = 0
    private var switching = false
    private var autoRun = true
    private var sideBySidePref = false
    private var splitRatio = 0.5f

    // Preview-এর line থেকে আসল tab/line বের করার জন্য
    private var cssLineOffset = 0
    private var htmlLineOffset = 0
    private var jsLineOffset = 0

    private var viewportMode = 0 // 0 = Fluid, 1 = Mobile (375dp), 2 = Tablet (600dp)
    private var webGeoEnabled = true
    private var webCamEnabled = true
    private var webPopupEnabled = false

    private val staticProblems = mutableListOf<Problem>()
    private val runtimeProblems = mutableListOf<Problem>()

    private val handler = Handler(Looper.getMainLooper())
    private val renderRunnable = Runnable {
        if (autoRun) render() else runLint()
        saveCode()
    }
    private val highlightRunnable = Runnable { highlightNow() }
    private val logBuffer = StringBuilder()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabs: TabLayout
    private lateinit var problemsBar: TextView
    private lateinit var split: LinearLayout
    private lateinit var editorPane: LinearLayout
    private lateinit var divider: View
    private lateinit var previewPane: LinearLayout
    private lateinit var editor: CodeEditText
    private lateinit var console: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var btnFullscreen: ImageButton
    private var preview: WebView? = null
    private lateinit var history: EditHistory

    // Project, Import, Export
    private val files = ProjectFiles(
        activity = this,
        currentCode = { code.toList() },
        onImport = { t -> loadTemplate(t) },
        onNameChanged = { name -> supportActionBar?.title = name }
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setFullscreen(false)
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = contentResolver.getType(uri) ?: "image/png"
                    val imgTag = "<img src=\"data:$mime;base64,$base64\" alt=\"image\" />"
                    insertText(imgTag)
                    Toast.makeText(this, "Inserted Image Asset", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        tabs = findViewById(R.id.tabs)
        problemsBar = findViewById(R.id.problemsBar)
        split = findViewById(R.id.split)
        editorPane = findViewById(R.id.editorPane)
        divider = findViewById(R.id.divider)
        previewPane = findViewById(R.id.previewPane)
        editor = findViewById(R.id.editor)
        history = EditHistory(editor, { currentTab }, { switching }) { invalidateOptionsMenu() }

        console = findViewById(R.id.console)
        console.movementMethod = ScrollingMovementMethod()
        previewContainer = findViewById(R.id.previewContainer)
        setSupportActionBar(toolbar)
        supportActionBar?.title = files.projectName
        supportActionBar?.subtitle = null

        problemsBar.setOnClickListener { showProblems() }

        val d = Templates.default
        val defaults = listOf(d.html, d.css, d.js)
        handleIntent(intent, defaults)
        autoRun = prefs.getBoolean("auto_run", true)
        sideBySidePref = prefs.getBoolean("side_by_side", false)
        splitRatio = prefs.getFloat("split_ratio", 0.5f)
        invalidateOptionsMenu()

        // SECURITY: শুধু debug build-এ WebView inspect করা যাবে
        WebView.setWebContentsDebuggingEnabled(isDebuggable())
        createPreview()

        setupTabs()
        setupSymbolBar()
        setupDivider()
        setupAnimations()

        findViewById<Button>(R.id.btnConsole).setOnClickListener {
            val panel = findViewById<View>(R.id.consolePanel)
            TransitionManager.beginDelayedTransition(previewPane, AutoTransition().setDuration(200))
            panel.isVisible = !panel.isVisible
        }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            logBuffer.clear()
            console.text = ""
        }
        btnFullscreen = findViewById(R.id.btnFullscreen)
        btnFullscreen.setOnClickListener {
            setFullscreen(!backCallback.isEnabled)
        }

        val consoleInput = findViewById<EditText>(R.id.consoleInput)
        val btnRunJs = findViewById<Button>(R.id.btnRunJs)
        fun executeConsoleJs() {
            val js = consoleInput.text?.toString()?.trim() ?: ""
            if (js.isNotEmpty()) {
                log("› $js")
                consoleInput.setText("")
                preview?.evaluateJavascript(js) { result ->
                    if ((result != null) && (result != "null") && (result != "undefined")) {
                        log("= $result")
                    }
                }
            }
        }
        btnRunJs.setOnClickListener { executeConsoleJs() }
        consoleInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                executeConsoleJs()
                true
            } else false
        }

        findViewById<ImageButton>(R.id.btnViewport).setOnClickListener {
            viewportMode = (viewportMode + 1) % 3
            val wrapper = findViewById<ViewGroup>(R.id.previewWrapper)
            TransitionManager.beginDelayedTransition(wrapper, AutoTransition().setDuration(200))
            val container = previewContainer
            val params = container.layoutParams as FrameLayout.LayoutParams
            when (viewportMode) {
                0 -> {
                    params.width = FrameLayout.LayoutParams.MATCH_PARENT
                    Toast.makeText(this, "Viewport: Fluid Desktop (100%)", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    params.width = dp(375)
                    Toast.makeText(this, "Viewport: Mobile (375px)", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    params.width = dp(600)
                    Toast.makeText(this, "Viewport: Tablet (600px)", Toast.LENGTH_SHORT).show()
                }
            }
            container.layoutParams = params
        }

        switching = true
        editor.setText(code[0])
        switching = false
        highlightNow()

        editor.doAfterTextChanged { text ->
            if (switching) return@doAfterTextChanged
            code[currentTab] = text?.toString() ?: ""
            handler.removeCallbacks(highlightRunnable)
            handler.postDelayed(highlightRunnable, HIGHLIGHT_DELAY_MS)
            handler.removeCallbacks(renderRunnable)
            handler.postDelayed(renderRunnable, RENDER_DELAY_MS)
        }

        onBackPressedDispatcher.addCallback(this, backCallback)
        applyLayout()
        render()
    }

    // ---------- Setup ----------

    private fun setupAnimations() {
        // Pulsing Live Dot Animation
        findViewById<View>(R.id.liveDot)?.let { liveDot ->
            ObjectAnimator.ofFloat(liveDot, "alpha", 0.3f, 1.0f).apply {
                duration = 900
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
        }
    }

    private fun setupTabs() {
        listOf("HTML", "CSS", "JS").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                switching = true
                editor.alpha = 0.85f
                editor.animate().alpha(1.0f).setDuration(120).start()
                editor.setText(code[currentTab])
                switching = false
                highlightNow()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupSymbolBar() {
        val bar = findViewById<LinearLayout>(R.id.symbolBar)

        SYMBOLS.forEach { symbol ->
            val key = TextView(this).apply {
                text = symbol
                textSize = 15f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                minWidth = dp(38)
                height = dp(32)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                layoutParams = params
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setBackgroundResource(R.drawable.bg_symbol_key)
                setOnClickListener { v ->
                    v.animate().scaleX(0.86f).scaleY(0.86f).setDuration(50).withEndAction {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
                    }.start()
                    insertText(if (symbol == TAB) "  " else symbol)
                }
            }
            bar.addView(key)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDivider() {
        val handle = findViewById<View>(R.id.dividerHandle)
        divider.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handle.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val loc = IntArray(2)
                    split.getLocationOnScreen(loc)
                    val horizontal = split.orientation == LinearLayout.HORIZONTAL
                    val size = if (horizontal) split.width else split.height
                    if (size > 0) {
                        val pos = if (horizontal) e.rawX - loc[0] else e.rawY - loc[1]
                        splitRatio = (pos / size).coerceIn(0.15f, 0.85f)
                        updateWeights()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handle.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    prefs.edit { putFloat("split_ratio", splitRatio) }
                    if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Layout ----------

    private fun applyLayout() {
        val horizontal = sideBySidePref ||
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        TransitionManager.beginDelayedTransition(split, AutoTransition().setDuration(250))
        split.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        editorPane.layoutParams = paneParams(horizontal, splitRatio)
        previewPane.layoutParams = paneParams(horizontal, 1f - splitRatio)
        divider.layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(dp(14), ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14))
        }

        findViewById<View>(R.id.dividerHandle)?.let { handle ->
            handle.layoutParams = if (horizontal) {
                FrameLayout.LayoutParams(dp(4), dp(36), Gravity.CENTER)
            } else {
                FrameLayout.LayoutParams(dp(36), dp(4), Gravity.CENTER)
            }
        }
    }

    private fun paneParams(horizontal: Boolean, weight: Float) =
        if (horizontal) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight)

    private fun updateWeights() {
        (editorPane.layoutParams as LinearLayout.LayoutParams).weight = splitRatio
        (previewPane.layoutParams as LinearLayout.LayoutParams).weight = 1f - splitRatio
        split.requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    private fun setFullscreen(on: Boolean) {
        TransitionManager.beginDelayedTransition(findViewById(R.id.main), AutoTransition().setDuration(200))
        toolbar.isVisible = !on
        tabs.isVisible = !on
        editorPane.isVisible = !on
        divider.isVisible = !on
        if (on) problemsBar.isVisible = false else updateProblemsUi()
        backCallback.isEnabled = on

        btnFullscreen.setImageResource(if (on) R.drawable.ic_collapse else R.drawable.ic_expand)
        btnFullscreen.setColorFilter(
            ContextCompat.getColor(this, if (on) R.color.accent else R.color.text_secondary)
        )

        if (on) {
            hideKeyboard()
            Toast.makeText(this, "Press Back or tap icon to exit full screen", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Preview (sandboxed WebView) ----------

    private fun isDebuggable() =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    @SuppressLint("SetJavaScriptEnabled") // JS চালানোই app-এর কাজ, তবে sandbox-এর ভেতরে
    private fun createPreview() {
        val wv = WebView(this)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // SECURITY: phone-এর file আর content provider-এ ঢোকা বন্ধ
            allowFileAccess = false
            allowContentAccess = false
            // SECURITY: https page-এ http content load হবে না
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // SECURITY: location আর popup window বন্ধ
            setGeolocationEnabled(false)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        // SECURITY: addJavascriptInterface কখনো ব্যবহার করা হয়নি

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val isError = msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR
                val icon = when (msg.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> "✖"
                    ConsoleMessage.MessageLevel.WARNING -> "⚠"
                    else -> "›"
                }
                val (tab, line) = mapLine(msg.lineNumber())
                val where = if (line > 0) " (${CodeLinter.TAB_NAMES[tab]} line $line)" else ""
                log("$icon ${msg.message()}$where")
                // SECURITY: runtime error-এর সংখ্যা সীমিত
                if (isError && runtimeProblems.size < CodeLinter.MAX_PROBLEMS) {
                    runtimeProblems += Problem(tab, line, msg.message())
                    updateProblemsUi()
                }
                return true
            }
        }

        wv.webViewClient = object : WebViewClient() {
            // SECURITY: বাইরের URL বা intent:// link-এ যাওয়া বন্ধ
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                log("⚠ Blocked navigation: ${request.url}")
                return true
            }

            // STABILITY: preview crash করলে নতুন preview আসবে
            override fun onRenderProcessGone(
                view: WebView, detail: RenderProcessGoneDetail
            ): Boolean {
                previewContainer.removeView(view)
                view.destroy()
                preview = null
                createPreview()
                log("✖ Preview crashed and was restarted.")
                render()
                return true
            }
        }

        previewContainer.addView(
            wv,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        preview = wv
    }

    private fun buildHtml(): String {
        val beforeCss = "<!DOCTYPE html>\n<html>\n<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "<style>\n"
        cssLineOffset = beforeCss.count { it == '\n' }
        val beforeHtml = beforeCss + code[1] + "\n</style>\n</head>\n<body>\n"
        htmlLineOffset = beforeHtml.count { it == '\n' }
        val beforeJs = beforeHtml + code[0] + "\n<script>\n"
        jsLineOffset = beforeJs.count { it == '\n' }
        return beforeJs + code[2] + "\n</script>\n</body>\n</html>"
    }

    private fun mapLine(docLine: Int): Pair<Int, Int> = when {
        docLine > jsLineOffset -> 2 to docLine - jsLineOffset
        docLine > htmlLineOffset -> 0 to docLine - htmlLineOffset
        docLine > cssLineOffset -> 1 to docLine - cssLineOffset
        else -> 2 to 0
    }

    private fun render() {
        logBuffer.clear()
        console.text = ""
        runtimeProblems.clear()
        runLint()
        preview?.loadDataWithBaseURL(SANDBOX_URL, buildHtml(), "text/html", "UTF-8", null)

        // Pulse Live Dot indicator to visually show active rendering
        findViewById<View>(R.id.liveDot)?.let { dot ->
            dot.animate().scaleX(1.8f).scaleY(1.8f).setDuration(150).withEndAction {
                dot.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }.start()
        }
    }

    // SECURITY: console log-এর size সীমিত
    private fun log(line: String) {
        logBuffer.append(line).append('\n')
        if (logBuffer.length > MAX_LOG_CHARS) {
            logBuffer.delete(0, logBuffer.length - MAX_LOG_CHARS)
        }
        console.text = logBuffer
    }

    // ---------- Problems ----------

    private fun runLint() {
        staticProblems.clear()
        staticProblems += CodeLinter.lintAll(code[0], code[1], code[2])
        updateProblemsUi()
    }

    private fun allProblems() = (staticProblems + runtimeProblems)
        .distinctBy { Triple(it.tab, it.line, it.message) }
        .sortedWith(compareBy({ !it.isError }, { it.tab }, { it.line }))

    private fun updateProblemsUi() {
        val all = allProblems()
        val errors = all.count { it.isError }
        val warnings = all.size - errors
        val shouldShow = all.isNotEmpty() && !backCallback.isEnabled
        if (problemsBar.isVisible != shouldShow) {
            TransitionManager.beginDelayedTransition(findViewById(R.id.main), AutoTransition().setDuration(180))
            problemsBar.isVisible = shouldShow
        }
        if (all.isNotEmpty()) {
            val summary = buildList {
                if (errors > 0) add("✖ $errors")
                if (warnings > 0) add("⚠ $warnings")
            }.joinToString("  ")
            val first = all.first()
            problemsBar.text = getString(R.string.problem_summary_format, summary, first.label(), first.message)
            problemsBar.setTextColor(
                ContextCompat.getColor(this, if (errors > 0) R.color.error else R.color.warn)
            )
        }
        for (i in 0..2) {
            val tab = tabs.getTabAt(i) ?: continue
            val count = all.count { it.tab == i }
            if (count > 0) {
                tab.orCreateBadge.apply {
                    number = count
                    backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.error)
                }
            } else {
                tab.removeBadge()
            }
        }
        updateErrorLines()
    }

    private fun updateErrorLines() {
        editor.errorLines = (staticProblems + runtimeProblems)
            .filter { it.tab == currentTab && it.line > 0 }
            .map { it.line }
            .toSet()
    }

    private fun showProblems() {
        val all = allProblems()
        if (all.isEmpty()) return
        val items = all.map {
            (if (it.isError) "✖ " else "⚠ ") + it.label() + "\n" + it.message
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Problems (${all.size})")
            .setItems(items) { _, which -> jumpTo(all[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun jumpTo(p: Problem) {
        if (backCallback.isEnabled) setFullscreen(false)
        tabs.getTabAt(p.tab)?.select()
        if (p.line > 0) editor.post { editor.goToLine(p.line) }
    }

    // ---------- Editor ----------

    private fun highlightNow() {
        editor.text?.let { SyntaxHighlighter.highlight(it, currentTab) }
        updateErrorLines()
    }

    private fun insertText(s: String) {
        val e = editor.text ?: return
        val a = editor.selectionStart.coerceAtLeast(0)
        val b = editor.selectionEnd.coerceAtLeast(0)
        e.replace(minOf(a, b), maxOf(a, b), s)
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(editor.windowToken, 0)
    }

    private fun loadTemplate(t: Template) {
        code[0] = t.html
        code[1] = t.css
        code[2] = t.js
        history.clear()
        switching = true
        editor.setText(code[currentTab])
        switching = false
        highlightNow()
        render()
        saveCode()
    }

    private fun confirmReplace(action: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Replace current code?")
            .setMessage("Your current HTML, CSS and JS will be replaced.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Replace") { _, _ -> action() }
            .show()
    }

    private fun showTemplates() {
        val customTemplates = CustomTemplatesManager.getCustomTemplates(this)
        val allTemplates = (Templates.all + customTemplates).distinctBy { it.name }

        val items = mutableListOf<CustomListItem>()
        items.add(
            CustomListItem(
                title = "Save Current Code as Custom Template",
                subtitle = "Save HTML, CSS, JS as reusable template",
                iconRes = R.drawable.ic_add,
                action = { saveCustomTemplateDialog() }
            )
        )
        allTemplates.forEach { tmpl ->
            val isCustom = tmpl in customTemplates
            items.add(
                CustomListItem(
                    title = tmpl.name,
                    subtitle = if (isCustom) "Custom User Template" else "Built-in Starter Kit",
                    iconRes = if (isCustom) R.drawable.ic_star else R.drawable.ic_template,
                    action = { confirmReplace { loadTemplate(tmpl) } }
                )
            )
        }
        DialogUiHelper.showCustomListDialog(this, "Templates", items)
    }

    private fun saveCustomTemplateDialog() {
        val (container, input) = DialogUiHelper.createStyledInput(this, "e.g. My Navbar / Portfolio Layout")
        MaterialAlertDialogBuilder(this)
            .setTitle("Save Custom Template")
            .setMessage("Save current HTML, CSS, and JS as a reusable template:")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val custom = Template(
                        name = name,
                        html = code[0],
                        css = code[1],
                        js = code[2],
                    )
                    CustomTemplatesManager.saveCustomTemplate(this, custom)
                    Toast.makeText(this, "Saved template: '$name'", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Template name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCdnLibraries() {
        val items = CdnLibraries.items.map { item ->
            CustomListItem(
                title = item.name,
                subtitle = item.description,
                iconRes = R.drawable.ic_code,
                action = {
                    val (updatedHtml, injected) = CdnLibraries.injectIntoHtml(code[0], item.htmlCode)
                    if (injected) {
                        code[0] = updatedHtml
                        if (currentTab == 0) {
                            switching = true
                            editor.setText(code[0])
                            switching = false
                            highlightNow()
                        }
                        render()
                        saveCode()
                        Toast.makeText(this, "Added ${item.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "${item.name} is already in HTML", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        DialogUiHelper.showCustomListDialog(this, "Add Library (CDN)", items)
    }

    private fun showColorPicker() {
        ColorPickerDialog.show(this) { color ->
            insertText(color)
        }
    }

    private fun formatCurrentCode() {
        code[currentTab] = CodeFormatter.format(code[currentTab], currentTab)
        switching = true
        editor.setText(code[currentTab])
        switching = false
        highlightNow()
        saveCode()
        Toast.makeText(this, "Code Formatted", Toast.LENGTH_SHORT).show()
    }

    private fun shareHtml() {
        val html = buildHtml()
        if (html.length > MAX_SHARE_CHARS) {
            Toast.makeText(this, "Code is too large to share", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "TwinPane project.html")
            putExtra(Intent.EXTRA_TEXT, html)
        }
        startActivity(Intent.createChooser(intent, "Share HTML"))
    }

    private fun toggleDomInspector() {
        val js = """
            (function() {
              if (window.__inspectorActive) {
                window.__inspectorActive = false;
                if (window.__inspectorOutline) window.__inspectorOutline.remove();
                return 'Inspector OFF';
              }
              window.__inspectorActive = true;
              var outline = document.createElement('div');
              outline.style.position = 'fixed';
              outline.style.pointerEvents = 'none';
              outline.style.border = '2px solid #8B5CF6';
              outline.style.backgroundColor = 'rgba(139, 92, 246, 0.2)';
              outline.style.zIndex = '999999';
              document.body.appendChild(outline);
              window.__inspectorOutline = outline;

              document.addEventListener('mouseover', function(e) {
                if (!window.__inspectorActive) return;
                var r = e.target.getBoundingClientRect();
                outline.style.top = r.top + 'px';
                outline.style.left = r.left + 'px';
                outline.style.width = r.width + 'px';
                outline.style.height = r.height + 'px';
              }, true);

              document.addEventListener('click', function(e) {
                if (!window.__inspectorActive) return;
                e.preventDefault();
                e.stopPropagation();
                var el = e.target;
                var tag = el.tagName.toLowerCase();
                var id = el.id ? '#' + el.id : '';
                var cls = el.className ? '.' + el.className.toString().trim().replace(/\s+/g, '.') : '';
                var text = (el.innerText || el.textContent || '').trim().substring(0, 30);
                console.log('INSPECT: <' + tag + id + cls + '> ' + text);
              }, true);
              return 'Inspector ON - Tap element in Preview';
            })()
        """.trimIndent()
        preview?.evaluateJavascript(js) { res ->
            Toast.makeText(this, res?.trim('"') ?: "Inspector Toggled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmmetDialog() {
        val (container, input) = DialogUiHelper.createStyledInput(this, "e.g. div.card>h2.title+p.desc or ul>li*3")
        MaterialAlertDialogBuilder(this)
            .setTitle("Emmet Expand")
            .setView(container)
            .setPositiveButton("Expand") { _, _ ->
                val abbr = input.text.toString().trim()
                val expanded = EmmetEngine.expand(abbr)
                if (expanded != null) {
                    insertText(expanded)
                } else {
                    Toast.makeText(this, "Could not expand abbreviation", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleLocalWebServer() {
        if (LocalWebServer.isRunning) {
            LocalWebServer.stop()
            Toast.makeText(this, "Local Web Server Stopped", Toast.LENGTH_SHORT).show()
        } else {
            val ip = LocalWebServer.getLocalIpAddress() ?: "127.0.0.1"
            val url = "http://$ip:8080"
            val success = LocalWebServer.start(8080) { buildHtml() }
            if (success) {
                val bitmap = QrCodeGenerator.generateMatrixBitmap(url, 400)
                val iv = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    setPadding(40, 20, 40, 20)
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle("Live Wi-Fi Server")
                    .setMessage("Server running at:\n$url\n\nScan QR Code on other devices on the same Wi-Fi:")
                    .setView(iv)
                    .setPositiveButton("Copy URL") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Server URL", url))
                        Toast.makeText(this, "Copied URL to Clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Stop Server") { _, _ -> LocalWebServer.stop() }
                    .show()
            } else {
                Toast.makeText(this, "Could not start server on port 8080", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWebPermissionsDialog() {
        val permissions = arrayOf("Geolocation API", "Camera & Microphone", "JavaScript Popups")
        val checked = booleanArrayOf(true, true, false)
        MaterialAlertDialogBuilder(this)
            .setTitle("Web API Permissions")
            .setMultiChoiceItems(permissions, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                preview?.settings?.apply {
                    setGeolocationEnabled(checked[0])
                    javaScriptCanOpenWindowsAutomatically = checked[2]
                }
                Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val textColor = ContextCompat.getColor(this, R.color.text)

        menu.findItem(R.id.action_home)?.icon?.mutate()?.setTint(textColor)
        menu.findItem(R.id.action_main_menu)?.icon?.mutate()?.setTint(textColor)

        menu.findItem(R.id.action_undo)?.let {
            it.isEnabled = history.canUndo
            it.icon?.mutate()?.setTint(textColor)
            it.icon?.mutate()?.alpha = if (it.isEnabled) 255 else 80
        }
        menu.findItem(R.id.action_redo)?.let {
            it.isEnabled = history.canRedo
            it.icon?.mutate()?.setTint(textColor)
            it.icon?.mutate()?.alpha = if (it.isEnabled) 255 else 80
        }
        menu.findItem(R.id.action_run)?.let { item ->
            if (autoRun) {
                item.setIcon(R.drawable.ic_pause)
                item.icon?.mutate()?.setTint(ContextCompat.getColor(this, R.color.accent))
                item.title = "Pause Auto-Run"
            } else {
                item.setIcon(R.drawable.ic_play)
                item.icon?.mutate()?.setTint(textColor)
                item.title = "Play / Enable Auto-Run"
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_home) {
            finish()
            return true
        }
        if (item.itemId == R.id.action_main_menu) {
            openMainMenu()
            return true
        }
        if (files.handleMenu(item.itemId)) return true
        if (item.itemId == R.id.action_undo) { history.undo(); return true }
        if (item.itemId == R.id.action_redo) { history.redo(); return true }
        if (item.itemId == R.id.action_run) {
            if (autoRun) {
                autoRun = false
                prefs.edit { putBoolean("auto_run", false) }
                invalidateOptionsMenu()
                Toast.makeText(this, "Auto-Run Paused (Tap ▶ to run manually)", Toast.LENGTH_SHORT).show()
            } else {
                autoRun = true
                prefs.edit { putBoolean("auto_run", true) }
                invalidateOptionsMenu()
                render()
                saveCode()
                Toast.makeText(this, "Live Preview Running (Auto-Run Enabled)", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun openMainMenu() {
        // --- 1. Project Sub-menu ---
        val importSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_file, title = "Import Single HTML File", subtitle = "Load a standalone .html file into project", action = { files.handleMenu(R.id.action_import_html) }),
            MenuItemData(iconRes = R.drawable.ic_folder, title = "Import HTML + CSS + JS", subtitle = "Load separate web source files", action = { files.handleMenu(R.id.action_import_files) }),
            MenuItemData(iconRes = R.drawable.ic_zip, title = "Import ZIP Project Archive", subtitle = "Extract and load a .zip project archive", action = { files.handleMenu(R.id.action_import_zip) }),
        )

        val projectSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_add, title = "New Project", subtitle = "Create a new web application workspace", action = { files.handleMenu(R.id.action_new_project) }),
            MenuItemData(iconRes = R.drawable.ic_edit, title = "Rename Project", subtitle = "Change current project title", action = { files.handleMenu(R.id.action_rename_project) }),
            MenuItemData(iconRes = R.drawable.ic_folder, title = "Import Code / Project", subtitle = "Load HTML, CSS, JS, or ZIP archive", isSubMenu = true, subItems = importSubItems),
        )

        // --- 2. Export Sub-menu ---
        val exportSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_zip, title = "Export as ZIP", subtitle = "Bundle code into a downloadable .zip archive", action = { files.handleMenu(R.id.action_export_zip) }),
            MenuItemData(iconRes = R.drawable.ic_folder, title = "Export as Separate Files", subtitle = "Save HTML, CSS, JS files to folder", action = { files.handleMenu(R.id.action_export_folder) }),
            MenuItemData(iconRes = R.drawable.ic_file, title = "Export as Single HTML", subtitle = "Bundle code into a single .html file", action = { files.handleMenu(R.id.action_export_html) }),
        )

        // --- 3. Templates Sub-menu ---
        val customTemplates = CustomTemplatesManager.getCustomTemplates(this)
        val templateSubItems = mutableListOf<MenuItemData>()
        templateSubItems.add(
            MenuItemData(iconRes = R.drawable.ic_add, title = "Save Current Code as Custom Template", subtitle = "Save current HTML, CSS, JS as reusable template", action = { saveCustomTemplateDialog() })
        )
        (Templates.all + customTemplates).distinctBy { it.name }.forEach { tmpl ->
            val isCustom = tmpl in customTemplates
            templateSubItems.add(
                MenuItemData(
                    iconRes = if (isCustom) R.drawable.ic_star else R.drawable.ic_template,
                    title = tmpl.name,
                    subtitle = if (isCustom) "Saved Custom Template" else "Built-in Starter Template",
                    action = { confirmReplace { loadTemplate(tmpl) } }
                )
            )
        }

        // --- 4. Code Tools Sub-menu ---
        val codeToolsSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_brush, title = "Format Code", subtitle = "Beautify & auto-indent HTML, CSS, JS", action = { formatCurrentCode() }),
            MenuItemData(iconRes = R.drawable.ic_search, title = "Find & Replace", subtitle = "Search keywords or regex and replace text", action = {
                FindReplaceDialog.show(this, code[currentTab]) { updated ->
                    code[currentTab] = updated
                    switching = true
                    editor.setText(updated)
                    switching = false
                    highlightNow()
                    saveCode()
                }
            }),
            MenuItemData(iconRes = R.drawable.ic_code, title = "Emmet Expand", subtitle = "Expand HTML abbreviations into full markup", action = { showEmmetDialog() }),
            MenuItemData(iconRes = R.drawable.ic_palette, title = "Color Picker", subtitle = "Select Hex colors from visual Material palette", action = { showColorPicker() }),
        )

        // --- 5. CSS Generator Nested Sub-menus ---
        val flexboxSubItems = CssGenerator.flexboxPresets.map { (title, css) ->
            MenuItemData(iconRes = R.drawable.ic_palette, title = title, subtitle = css.replace("\n", " "), action = { insertText(css) })
        }
        val shadowSubItems = CssGenerator.shadowPresets.map { (title, css) ->
            MenuItemData(iconRes = R.drawable.ic_palette, title = title, subtitle = css.replace("\n", " "), action = { insertText(css) })
        }
        val gradientSubItems = CssGenerator.gradientPresets.map { (title, css) ->
            MenuItemData(iconRes = R.drawable.ic_palette, title = title, subtitle = css.replace("\n", " "), action = { insertText(css) })
        }

        val cssGenSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_palette, title = "Flexbox & Grid Layouts", subtitle = "Center alignment, Space between, Grid 2 cols", isSubMenu = true, subItems = flexboxSubItems),
            MenuItemData(iconRes = R.drawable.ic_palette, title = "Box Shadows & Glassmorphism", subtitle = "Soft elevation, Glow purple, Glass blur", isSubMenu = true, subItems = shadowSubItems),
            MenuItemData(iconRes = R.drawable.ic_palette, title = "Gradients", subtitle = "Cyberpunk sunset, Neon emerald, Midnight blue", isSubMenu = true, subItems = gradientSubItems),
        )

        val cdnSubItems = CdnLibraries.items.map { item ->
            MenuItemData(
                iconRes = R.drawable.ic_code,
                title = item.name,
                subtitle = item.description,
                action = {
                    val (updatedHtml, injected) = CdnLibraries.injectIntoHtml(code[0], item.htmlCode)
                    if (injected) {
                        code[0] = updatedHtml
                        if (currentTab == 0) {
                            switching = true
                            editor.setText(code[0])
                            switching = false
                            highlightNow()
                        }
                        render()
                        saveCode()
                        Toast.makeText(this, "Added ${item.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "${item.name} is already in HTML", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        val designSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_palette, title = "CSS Generator", subtitle = "Visual builders for Flexbox, Shadow, Gradients", isSubMenu = true, subItems = cssGenSubItems),
            MenuItemData(iconRes = R.drawable.ic_code, title = "Add Library (CDN)", subtitle = "Inject Bootstrap, Tailwind, Vue, jQuery, FontAwesome", isSubMenu = true, subItems = cdnSubItems),
            MenuItemData(iconRes = R.drawable.ic_image, title = "Insert Image / Asset", subtitle = "Convert gallery image to Base64 img tag", action = { imagePickerLauncher.launch("image/*") }),
        )

        // --- 6. Web Permissions Sub-menu ---
        val webPermissionsSubItems = listOf(
            MenuItemData(
                iconRes = R.drawable.ic_security,
                title = "Geolocation API",
                subtitle = "Allow HTML5 location requests in preview",
                isChecked = webGeoEnabled,
                dismissOnClick = false,
                action = {
                    webGeoEnabled = !webGeoEnabled
                    preview?.settings?.setGeolocationEnabled(webGeoEnabled)
                    Toast.makeText(this, if (webGeoEnabled) "Geolocation Enabled" else "Geolocation Disabled", Toast.LENGTH_SHORT).show()
                }
            ),
            MenuItemData(
                iconRes = R.drawable.ic_security,
                title = "Camera & Microphone",
                subtitle = "Allow WebRTC audio & video streams",
                isChecked = webCamEnabled,
                dismissOnClick = false,
                action = {
                    webCamEnabled = !webCamEnabled
                    Toast.makeText(this, if (webCamEnabled) "Camera/Mic Allowed" else "Camera/Mic Blocked", Toast.LENGTH_SHORT).show()
                }
            ),
            MenuItemData(
                iconRes = R.drawable.ic_security,
                title = "JavaScript Popups",
                subtitle = "Allow window.open popup windows",
                isChecked = webPopupEnabled,
                dismissOnClick = false,
                action = {
                    webPopupEnabled = !webPopupEnabled
                    preview?.settings?.javaScriptCanOpenWindowsAutomatically = webPopupEnabled
                    Toast.makeText(this, if (webPopupEnabled) "Popups Allowed" else "Popups Blocked", Toast.LENGTH_SHORT).show()
                }
            )
        )

        val devToolsSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_search, title = "Inspect Element", subtitle = "Click elements in live preview to inspect markup", action = { toggleDomInspector() }),
            MenuItemData(iconRes = R.drawable.ic_storage, title = "Web Storage & Cookies", subtitle = "Inspect and clear localStorage, sessionStorage", action = { StorageInspectorDialog.show(this, preview) { render() } }),
            MenuItemData(iconRes = R.drawable.ic_security, title = "Web API Permissions", subtitle = "Configure Geolocation, Camera/Mic, JS popups", isSubMenu = true, subItems = webPermissionsSubItems),
        )

        // --- 7. Share & Server Sub-menu ---
        val shareServerSubItems = listOf(
            MenuItemData(iconRes = R.drawable.ic_wifi, title = "Live Wi-Fi Server", subtitle = "Host website on local Wi-Fi with QR Code", action = { toggleLocalWebServer() }),
            MenuItemData(iconRes = R.drawable.ic_export, title = "Share as HTML", subtitle = "Share single bundled HTML file via intent", action = { shareHtml() }),
        )

        // --- 8. Settings & View Sub-menu ---
        val settingsViewSubItems = listOf(
            MenuItemData(
                iconRes = R.drawable.ic_settings,
                title = "App Theme Mode",
                subtitle = "Switch between Royal Dark, Clean Light, or System",
                action = { ThemeManager.showThemeSelectionDialog(this) },
            ),
            MenuItemData(
                iconRes = R.drawable.ic_viewport,
                title = "Side-by-side Layout",
                subtitle = "Toggle split layout between vertical & horizontal",
                isChecked = sideBySidePref,
                action = {
                    sideBySidePref = !sideBySidePref
                    prefs.edit { putBoolean("side_by_side", sideBySidePref) }
                    applyLayout()
                    invalidateOptionsMenu()
                },
            ),
            MenuItemData(
                iconRes = R.drawable.ic_play,
                title = "Auto Run",
                subtitle = "Automatically update live preview on typing",
                isChecked = autoRun,
                action = {
                    autoRun = !autoRun
                    prefs.edit { putBoolean("auto_run", autoRun) }
                    invalidateOptionsMenu()
                    val msg = if (autoRun) "Auto run ON" else "Auto run OFF (tap ▶ to run)"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                },
            ),
            MenuItemData(iconRes = R.drawable.ic_expand, title = "Fullscreen Preview", subtitle = "Expand preview to fill entire screen", action = { setFullscreen(true) }),
        )

        val mainMenuItems = listOf(
            // 1. Project
            MenuItemData(iconRes = R.drawable.ic_folder, title = "Project", subtitle = "Create, rename, import projects", isSubMenu = true, subItems = projectSubItems),
            // 2. Export
            MenuItemData(iconRes = R.drawable.ic_export, title = "Export", subtitle = "Export project as ZIP, HTML, or files", isSubMenu = true, subItems = exportSubItems),
            // 3. Templates
            MenuItemData(iconRes = R.drawable.ic_template, title = "Templates", subtitle = "Starter templates & saved custom templates", isSubMenu = true, subItems = templateSubItems),
            // 4. Reset Code
            MenuItemData(iconRes = R.drawable.ic_reset, title = "Reset Code", subtitle = "Reset HTML, CSS, JS to default code", action = { confirmReplace { loadTemplate(Templates.default) } }),
            // 5. Code Tools
            MenuItemData(iconRes = R.drawable.ic_code, title = "Code Tools", subtitle = "Format, Find & Replace, Emmet, Color Picker", isSubMenu = true, subItems = codeToolsSubItems),
            // 6. Design & Assets
            MenuItemData(iconRes = R.drawable.ic_palette, title = "Design & Assets", subtitle = "CSS Generator, CDN Libraries, Images", isSubMenu = true, subItems = designSubItems),
            // 7. DevTools & Debug
            MenuItemData(iconRes = R.drawable.ic_bug, title = "DevTools & Debug", subtitle = "DOM Inspector, Storage, Permissions", isSubMenu = true, subItems = devToolsSubItems),
            // 8. Share & Server
            MenuItemData(iconRes = R.drawable.ic_wifi, title = "Share & Server", subtitle = "Wi-Fi Server, QR Code, HTML sharing", isSubMenu = true, subItems = shareServerSubItems),
            // 9. Settings & View
            MenuItemData(iconRes = R.drawable.ic_settings, title = "Settings & View", subtitle = "Side-by-side, Auto run, Fullscreen", isSubMenu = true, subItems = settingsViewSubItems),
        )

        MainMenuDialog.show(this, mainMenuItems)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val d = Templates.default
        val defaults = listOf(d.html, d.css, d.js)
        handleIntent(intent, defaults)
        switching = true
        editor.setText(code[currentTab])
        switching = false
        highlightNow()
        render()
    }

    private fun handleIntent(intent: Intent, defaults: List<String>) {
        val action = intent.getStringExtra("action")
        val projName = intent.getStringExtra("project_name")
        val templateName = intent.getStringExtra("template_name")

        if (projName != null) {
            files.setName(projName)
        }

        when (action) {
            "NEW_PROJECT" -> {
                code.clear()
                code.addAll(defaults)
                saveCode()
            }
            "LOAD_TEMPLATE" -> {
                val customTemplates = CustomTemplatesManager.getCustomTemplates(this)
                val allTemplates = Templates.all + customTemplates
                val tmpl = allTemplates.find { it.name.equals(templateName, ignoreCase = true) } ?: Templates.default
                if (projName == null) files.setName(tmpl.name)
                code.clear()
                code.addAll(listOf(tmpl.html, tmpl.css, tmpl.js))
                saveCode()
            }
            "IMPORT_SINGLE" -> {
                loadSavedCodeForCurrentProject(defaults)
                handler.postDelayed({ files.handleMenu(R.id.action_import_html) }, 300)
            }
            "IMPORT_FILES", "IMPORT" -> {
                loadSavedCodeForCurrentProject(defaults)
                handler.postDelayed({ files.handleMenu(R.id.action_import_files) }, 300)
            }
            "IMPORT_ZIP" -> {
                loadSavedCodeForCurrentProject(defaults)
                handler.postDelayed({ files.handleMenu(R.id.action_import_zip) }, 300)
            }
            "STORAGE" -> {
                loadSavedCodeForCurrentProject(defaults)
                handler.postDelayed({ StorageInspectorDialog.show(this, preview) { render() } }, 400)
            }
            else -> {
                loadSavedCodeForCurrentProject(defaults)
            }
        }
    }

    private fun loadSavedCodeForCurrentProject(defaults: List<String>) {
        val curName = files.projectName
        code.clear()
        KEYS.forEachIndexed { i, key ->
            val saved = prefs.getString("${curName}_$key", null) ?: prefs.getString(key, null) ?: defaults[i]
            code.add(saved)
        }
    }

    // ---------- Save & cleanup ----------

    private fun saveCode() {
        val curName = files.projectName
        prefs.edit {
            KEYS.forEachIndexed { i, key ->
                putString(key, code[i])
                putString("${curName}_$key", code[i])
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        saveCode()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        preview?.let {
            previewContainer.removeView(it)
            it.destroy()
        }
        preview = null
        super.onDestroy()
    }
}
