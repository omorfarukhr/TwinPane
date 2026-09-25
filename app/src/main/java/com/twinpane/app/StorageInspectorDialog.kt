package com.twinpane.app

import android.content.Context
import android.webkit.WebView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object StorageInspectorDialog {

    fun show(context: Context, webView: WebView?, onStorageCleared: () -> Unit) {
        if (webView == null) return

        val jsScript = """
            (function() {
                return JSON.stringify({
                    localStorage: Object.assign({}, localStorage),
                    sessionStorage: Object.assign({}, sessionStorage),
                    cookies: document.cookie || 'None'
                });
            })()
        """.trimIndent()

        webView.evaluateJavascript(jsScript) { json ->
            val cleanJson = json?.replace("\\\"", "\"")?.trim('"') ?: "{}"
            val message = if (cleanJson.length > 2) {
                cleanJson.replace("{", "")
                    .replace("}", "")
                    .replace("\",\"", "\n")
                    .replace(":", ": ")
            } else {
                "No Web Storage items found."
            }

            val tv = TextView(context).apply {
                text = message
                setPadding(40, 20, 40, 20)
                textSize = 13f
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("Web Storage & Cookies")
                .setView(tv)
                .setPositiveButton("Clear Storage") { _, _ ->
                    webView.evaluateJavascript("localStorage.clear(); sessionStorage.clear();", null)
                    onStorageCleared()
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }
}
