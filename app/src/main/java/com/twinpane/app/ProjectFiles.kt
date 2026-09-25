package com.twinpane.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.InputFilter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectFiles(
    private val activity: AppCompatActivity,
    private val currentCode: () -> List<String>,      // [html, css, js]
    private val onImport: (Template) -> Unit,
    private val onNameChanged: (String) -> Unit
) {
    companion object {
        // SECURITY: প্রতিটা file সর্বোচ্চ 1 MB, একবারে সর্বোচ্চ 3টা
        private const val MAX_FILE_BYTES = 1_000_000
        private const val MAX_FILES = 3
        // SECURITY: zip bomb সুরক্ষা
        private const val MAX_ZIP_BYTES = 5_000_000L        // ZIP file-এর size
        private const val MAX_ZIP_UNPACKED = 10_000_000L    // খোলার পর মোট size
        private const val MAX_ZIP_ENTRIES = 200
        private const val DEFAULT_NAME = "my-project"
        private const val RAW_MIME = "application/octet-stream"
        private val HTML_EXTS = setOf("html", "htm")
        private val TEXT_EXTS = setOf("html", "htm", "css", "js", "mjs")
        private val SCRIPT_END = Regex("</script", RegexOption.IGNORE_CASE)
        private val STYLE_END = Regex("</style", RegexOption.IGNORE_CASE)

        // SECURITY: শুধু নিরাপদ অক্ষর, তাই "../" দিয়ে অন্য জায়গায় লেখা যাবে না
        fun safeName(raw: String): String =
            raw.trim()
                .replace(Regex("[^A-Za-z0-9 _-]"), "")
                .replace(Regex("\\s+"), "-")
                .take(40)
                .trim('-', '_')
                .ifEmpty { DEFAULT_NAME }
    }

    private data class Snapshot(val name: String, val html: String, val css: String, val js: String)

    // SECURITY: MODE_PRIVATE
    private val prefs by lazy {
        activity.getSharedPreferences("twinpane_project", Context.MODE_PRIVATE)
    }

    val projectName: String get() = prefs.getString("name", null) ?: DEFAULT_NAME

    fun setName(name: String) {
        val safe = safeName(name)
        prefs.edit { putString("name", safe) }
        onNameChanged(safe)
    }

    // ---------- File pickers (SECURITY: Storage Access Framework, কোনো permission লাগে না) ----------

    private val pickOne = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUris(listOf(uri)) }

    private val pickFiles = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) importUris(uris) }

    private val saveZip = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            val s = snapshot()
            runIo("Exported ${s.name}.zip") { writeZip(uri, s) }
        }
    }

    private val saveHtml = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val s = snapshot()
            runIo("Exported ${s.name}.html") { writeSingleHtml(uri, s) }
        }
    }

    private val pickFolder = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val s = snapshot()
            runIo("Exported folder \"${s.name}\"") { writeFolder(uri, s) }
        }
    }

    // ---------- Menu ----------

    fun handleMenu(id: Int): Boolean {
        try {
            when (id) {
                R.id.action_new_project -> newProject()
                R.id.action_rename_project ->
                    askName("Rename project", null, projectName) { setName(it) }
                R.id.action_import_html -> pickOne.launch(arrayOf("*/*"))
                R.id.action_import_files -> pickFiles.launch(arrayOf("*/*"))
                R.id.action_import_zip -> pickOne.launch(arrayOf("*/*"))
                R.id.action_export_zip -> saveZip.launch("$projectName.zip")
                R.id.action_export_folder -> pickFolder.launch(null)
                R.id.action_export_html -> saveHtml.launch("$projectName.html")
                else -> return false
            }
        } catch (e: ActivityNotFoundException) {
            toast("No file manager found on this device")
        }
        return true
    }

    private fun newProject() {
        askName(
            "New project",
            "Your current code will be replaced. Export it first if you need it.",
            DEFAULT_NAME
        ) { name ->
            val names = Templates.all.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle("Start from")
                .setItems(names) { _, which ->
                    setName(name)
                    onImport(Templates.all[which].copy(name = name))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun askName(title: String, message: String?, initial: String, onOk: (String) -> Unit) {
        val input = EditText(activity).apply {
            setText(initial)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(40))
            setSelection(text.length)
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = FrameLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .apply { if (message != null) setMessage(message) }
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ -> onOk(safeName(input.text.toString())) }
            .show()
    }

    // ---------- Import ----------

    private fun importUris(uris: List<Uri>) {
        Thread {
            val result = runCatching { readProject(uris) }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result.onSuccess { confirmOpen(it) }
                    .onFailure { toast("✖ " + (it.message ?: "Import failed")) }
            }
        }.start()
    }

    private fun ext(name: String) =
        name.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    private fun readProject(uris: List<Uri>): Template {
        val names = uris.map { fileName(it) }

        if (names.any { ext(it) == "zip" }) {
            if (uris.size != 1) throw IOException("Import a ZIP file on its own")
            return readZip(uris[0], names[0])
        }

        if (uris.size > MAX_FILES) throw IOException("Select up to 3 files: one HTML, one CSS, one JS")
        var html: String? = null
        var css: String? = null
        var js: String? = null
        var baseName: String? = null

        uris.forEachIndexed { i, uri ->
            val name = names[i]
            when (ext(name)) {
                "html", "htm" -> {
                    if (html != null) throw IOException("Select only one HTML file")
                    html = readText(uri, name)
                    baseName = name.substringBeforeLast('.')
                }
                "css" -> {
                    if (css != null) throw IOException("Select only one CSS file")
                    css = readText(uri, name)
                }
                "js", "mjs" -> {
                    if (js != null) throw IOException("Select only one JS file")
                    js = readText(uri, name)
                }
                else -> throw IOException("Unsupported file: ${name.take(60)} (use .html, .css, .js or .zip)")
            }
        }

        val parts = HtmlImport.split(html ?: "")
        val raw = baseName?.takeUnless { it.equals("index", ignoreCase = true) }
            ?: parts.title ?: projectName
        return Template(safeName(raw), parts.html, join(css, parts.css), join(js, parts.js))
    }

    // ---------- ZIP import ----------

    private fun readZip(uri: Uri, zipName: String): Template {
        val raw = activity.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open ZIP file")
        val files = linkedMapOf<String, String>()   // path -> text
        var total = 0L
        var count = 0

        try {
            val limited = LimitedInputStream(raw, MAX_ZIP_BYTES, "ZIP file is too large (max 5 MB)")
            ZipInputStream(limited).use { zip ->
                val buf = ByteArray(8192)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > MAX_ZIP_ENTRIES) {
                        throw IOException("ZIP has too many files (max $MAX_ZIP_ENTRIES)")
                    }
                    // শুধু code file রাখা হয়, image/font ইত্যাদি বাদ
                    val path = cleanPath(entry.name)
                        ?.takeIf { !entry.isDirectory && ext(it) in TEXT_EXTS }
                    val out = ByteArrayOutputStream()
                    var size = 0

                    // SECURITY: বাদ দেওয়া file-ও গোনা হয়, তাই zip bomb থামে
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ZIP_UNPACKED) {
                            throw IOException("ZIP content is too large (max 10 MB)")
                        }
                        if (path != null) {
                            size += n
                            if (size > MAX_FILE_BYTES) {
                                throw IOException("${path.take(60)} is too large (max 1 MB)")
                            }
                            out.write(buf, 0, n)
                        }
                    }
                    if (path != null) files[path] = decodeText(out.toByteArray(), path)
                }
            }
        } catch (e: ZipException) {
            throw IOException("Invalid, encrypted or unsafe ZIP file")
        }

        // মূল HTML: index.html আগে, তারপর যেটা সবচেয়ে উপরের folder-এ
        val htmlPath = files.keys
            .filter { ext(it) in HTML_EXTS }
            .minWithOrNull(
                compareBy<String>(
                    { !it.substringAfterLast('/').equals("index.html", ignoreCase = true) },
                    { p -> p.count { it == '/' } },
                    { it }
                )
            ) ?: throw IOException("No HTML file found in this ZIP")

        val parts = HtmlImport.split(files.getValue(htmlPath))
        val dir = htmlPath.substringBeforeLast('/', "")

        // HTML-এ যে CSS/JS file-এর link আছে সেগুলোই নেওয়া (connected files)
        fun linked(refs: List<String>, exts: Set<String>): String {
            val all = files.keys.filter { ext(it) in exts }
            val matched = refs.mapNotNull { resolve(dir, it) }.filter { it in files }.distinct()
            return matched.ifEmpty { all }.joinToString("\n\n") { files.getValue(it).trim() }
        }

        val css = linked(parts.localCss, setOf("css"))
        val js = linked(parts.localJs, setOf("js", "mjs"))
        val name = zipName.substringBeforeLast('.').ifBlank { parts.title ?: projectName }
        return Template(safeName(name), parts.html, join(css, parts.css), join(js, parts.js))
    }

    // SECURITY: ZIP-এর path শুধু file মেলাতে ব্যবহার হয়, কখনো disk-এ লেখা হয় না।
    // তবুও "../", absolute path, hidden আর __MACOSX file বাদ দেওয়া হয়।
    private fun cleanPath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        if (parts.first() == "__MACOSX" || parts.last().startsWith(".")) return null
        return parts.joinToString("/")
    }

    // HTML folder থেকে relative link (যেমন ../css/style.css) এর আসল path
    private fun resolve(baseDir: String, ref: String): String? {
        val clean = Uri.decode(ref.substringBefore('?').substringBefore('#'))
        if (clean.isBlank()) return null
        val stack = if (clean.startsWith("/")) mutableListOf()
        else baseDir.split('/').filter { it.isNotEmpty() }.toMutableList()
        for (part in clean.split('/')) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/").ifEmpty { null }
    }

    private fun join(vararg parts: String?) =
        parts.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

    private fun confirmOpen(t: Template) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Open \"${t.name}\"?")
            .setMessage("Your current HTML, CSS and JS will be replaced.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open") { _, _ ->
                setName(t.name)
                onImport(t)
            }
            .show()
    }

    private fun fileName(uri: Uri): String =
        runCatching {
            activity.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"

    // SECURITY: size limit, আর binary file বাতিল
    private fun readText(uri: Uri, name: String): String {
        val input = activity.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open file")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw IOException("File is too large (max 1 MB)")
                out.write(buf, 0, n)
            }
            return decodeText(out.toByteArray(), name)
        }
    }

    private fun decodeText(bytes: ByteArray, name: String): String {
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        if (text.contains('\u0000')) throw IOException("${name.take(60)} is not a text file")
        return text
    }

    // ---------- Export ----------

    private fun snapshot(): Snapshot {
        val c = currentCode()
        return Snapshot(projectName, c[0], c[1], c[2])
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    // index.html-এ style.css আর script.js-এর নাম হুবহু মেলানো (file name sync)
    private fun indexHtml(s: Snapshot) =
        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "  <title>${escape(s.name)}</title>\n" +
                "  <link rel=\"stylesheet\" href=\"style.css\">\n" +
                "</head>\n<body>\n" + s.html + "\n\n" +
                "  <script src=\"script.js\"></script>\n</body>\n</html>\n"

    // SECURITY: code-এর ভেতরের </script> বা </style> escape, যাতে page না ভাঙে
    private fun singleHtml(s: Snapshot) =
        "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "  <title>${escape(s.name)}</title>\n" +
                "<style>\n" + STYLE_END.replace(s.css) { "<\\/style" } + "\n</style>\n" +
                "</head>\n<body>\n" + s.html + "\n\n" +
                "<script>\n" + SCRIPT_END.replace(s.js) { "<\\/script" } + "\n</script>\n" +
                "</body>\n</html>\n"

    private fun projectFiles(s: Snapshot) = listOf(
        "index.html" to indexHtml(s),
        "style.css" to s.css,
        "script.js" to s.js
    )

    private fun writeZip(uri: Uri, s: Snapshot) {
        openOut(uri).use { out ->
            ZipOutputStream(out).use { zip ->
                // SECURITY: নির্দিষ্ট, নিরাপদ file name
                projectFiles(s).forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeSingleHtml(uri: Uri, s: Snapshot) {
        openOut(uri).use { it.write(singleHtml(s).toByteArray(Charsets.UTF_8)) }
    }

    private fun writeFolder(tree: Uri, s: Snapshot) {
        val resolver = activity.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree)
        )
        val dir = DocumentsContract.createDocument(
            resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, s.name
        ) ?: throw IOException("Cannot create folder")

        projectFiles(s).forEach { (name, text) ->
            val file = DocumentsContract.createDocument(resolver, dir, RAW_MIME, name)
                ?: throw IOException("Cannot create $name")
            openOut(file).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun openOut(uri: Uri): OutputStream {
        val r = activity.contentResolver
        return runCatching { r.openOutputStream(uri, "wt") }.getOrNull()
            ?: r.openOutputStream(uri)
            ?: throw IOException("Cannot write file")
    }

    // ---------- Helpers ----------

    private fun runIo(success: String, block: () -> Unit) {
        Thread {
            val error = runCatching(block).exceptionOrNull()
            activity.runOnUiThread {
                toast(if (error == null) "✓ $success" else "✖ " + (error.message ?: "Export failed"))
            }
        }.start()
    }

    private fun toast(msg: String) =
        Toast.makeText(activity.applicationContext, msg, Toast.LENGTH_LONG).show()
}

/** একটা HTML file ভেঙে HTML, CSS আর JS আলাদা করে */
private object HtmlImport {

    data class Parts(
        val html: String,
        val css: String,
        val js: String,
        val title: String?,
        val localCss: List<String>,   // <link href="style.css">
        val localJs: List<String>     // <script src="script.js">
    )

    private val OPT = setOf(RegexOption.IGNORE_CASE)
    private val TITLE = Regex("""<title\b[^>]*>([\s\S]*?)</title\s*>""", OPT)
    private val STYLE = Regex("""<style\b[^>]*>([\s\S]*?)</style\s*>""", OPT)
    private val SCRIPT = Regex("""<script\b([^>]*)>([\s\S]*?)</script\s*>""", OPT)
    private val LINK = Regex("""<link\b[^>]*>""", OPT)
    private val SRC = Regex("""\bsrc\s*=\s*["']?([^"'\s>]+)""", OPT)
    private val HREF = Regex("""\bhref\s*=\s*["']?([^"'\s>]+)""", OPT)
    private val BODY = Regex("""<body\b[^>]*>([\s\S]*?)(?:</body\s*>|$)""", OPT)
    private val HEAD = Regex("""<head\b[^>]*>[\s\S]*?</head\s*>""", OPT)
    private val WRAPPERS =
        Regex("""<!DOCTYPE[^>]*>|</?html\b[^>]*>|</?body\b[^>]*>|</?head\b[^>]*>""", OPT)

    fun split(src: String): Parts {
        val title = TITLE.find(src)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val css = mutableListOf<String>()
        val js = mutableListOf<String>()
        val external = mutableListOf<String>() // CDN library রেখে দেওয়া হয়
        val localCss = mutableListOf<String>()
        val localJs = mutableListOf<String>()

        var s = TITLE.replace(src, "")
        s = STYLE.replace(s) { m ->
            css.add(m.groupValues[1].trimIndent().trim())
            ""
        }
        s = SCRIPT.replace(s) { m ->
            val url = SRC.find(m.groupValues[1])?.groupValues?.get(1)
            when {
                url == null -> js.add(m.groupValues[2].trimIndent().trim())
                isRemote(url) -> external.add(m.value.trim())
                else -> localJs.add(url)
            }
            ""
        }
        s = LINK.replace(s) { m ->
            val href = HREF.find(m.value)?.groupValues?.get(1)
            if (href != null && m.value.contains("stylesheet", ignoreCase = true)) {
                if (isRemote(href)) external.add(m.value.trim()) else localCss.add(href)
            }
            ""
        }

        val body = BODY.find(s)?.groupValues?.get(1) ?: HEAD.replace(s, "")
        val html = WRAPPERS.replace(body, "").trimIndent().trim()

        return Parts(
            html = (external + html).filter { it.isNotBlank() }.joinToString("\n"),
            css = css.filter { it.isNotBlank() }.joinToString("\n\n"),
            js = js.filter { it.isNotBlank() }.joinToString("\n\n"),
            title = title,
            localCss = localCss,
            localJs = localJs
        )
    }

    private fun isRemote(url: String) =
        url.startsWith("https://", true) || url.startsWith("http://", true) || url.startsWith("//")
}

/** SECURITY: নির্দিষ্ট byte-এর বেশি পড়লে থামিয়ে দেয় (বড় বা ক্ষতিকর ZIP আটকাতে) */
private class LimitedInputStream(
    input: InputStream,
    private val limit: Long,
    private val message: String
) : FilterInputStream(input) {

    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0 && ++count > limit) throw IOException(message)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) {
            count += n
            if (count > limit) throw IOException(message)
        }
        return n
    }

    // skip-ও গোনা হয়, যাতে limit এড়ানো না যায়
    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        val buf = ByteArray(minOf(n, 8192L).toInt())
        val r = read(buf, 0, buf.size)
        return if (r < 0) 0 else r.toLong()
    }
}
