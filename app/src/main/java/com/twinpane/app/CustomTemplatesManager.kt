package com.twinpane.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object CustomTemplatesManager {

    private const val PREFS_NAME = "twinpane_custom_templates"
    private const val KEY_TEMPLATES = "custom_templates_json"

    fun getCustomTemplates(context: Context): List<Template> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_TEMPLATES, null) ?: return emptyList()
        val list = mutableListOf<Template>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Template(
                        name = obj.optString("name", "Custom Template"),
                        html = obj.optString("html", ""),
                        css = obj.optString("css", ""),
                        js = obj.optString("js", ""),
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomTemplate(context: Context, template: Template) {
        val current = getCustomTemplates(context).toMutableList()
        current.removeAll { it.name.equals(template.name, ignoreCase = true) }
        current.add(0, template)

        saveAll(context, current)
    }

    fun deleteCustomTemplate(context: Context, name: String) {
        val current = getCustomTemplates(context).toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        saveAll(context, current)
    }

    private fun saveAll(context: Context, list: List<Template>) {
        val array = JSONArray()
        for (t in list) {
            val obj = JSONObject().apply {
                put("name", t.name)
                put("html", t.html)
                put("css", t.css)
                put("js", t.js)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TEMPLATES, array.toString())
        }
    }
}
