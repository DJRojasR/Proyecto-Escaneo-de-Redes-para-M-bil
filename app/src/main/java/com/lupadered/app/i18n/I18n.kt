package com.lupadered.app.i18n

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import org.json.JSONObject
import java.util.Locale
import kotlin.system.exitProcess

/** Un idioma disponible en la app, con su nombre en ese mismo idioma. */
data class Language(val code: String, val nativeName: String)

/**
 * Traducciones de la app.
 *
 * Los textos se escriben en español dentro del código y se pasan por [tr]. Las
 * traducciones viven en `assets/i18n/<idioma>.json` (texto en español → traducción).
 * Por defecto la app usa el idioma del teléfono si está disponible y, si no, inglés.
 */
object I18n {

    const val AUTO = "auto"
    private const val PREFS = "settings"
    private const val KEY = "language"

    val languages = listOf(
        Language("en", "English"),
        Language("es", "Español"),
        Language("pt", "Português (Brasil)"),
        Language("fr", "Français"),
        Language("de", "Deutsch"),
        Language("it", "Italiano"),
        Language("hi", "हिन्दी"),
        Language("id", "Bahasa Indonesia"),
        Language("zh", "中文（简体）"),
        Language("ja", "日本語"),
        Language("ru", "Русский"),
        Language("ar", "العربية"),
    )

    /** Idioma que se está mostrando ahora (código de 2 letras). */
    @Volatile
    var current: String = "en"
        private set

    @Volatile
    private var strings: Map<String, String> = emptyMap()

    @Volatile
    private var english: Map<String, String> = emptyMap()

    val isRtl: Boolean get() = current == "ar"

    /** Se llama una vez al iniciar la app, antes de mostrar cualquier texto. */
    fun init(context: Context) {
        val choice = selected(context)
        current = if (choice == AUTO) detect() else choice
        if (current != "es") {
            english = load(context, "en")
            strings = if (current == "en") english else load(context, current)
        }
        if (choice != AUTO) Locale.setDefault(localeFor(current))
    }

    /** Idioma elegido en Ajustes, o [AUTO] para seguir al teléfono. */
    fun selected(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, AUTO) ?: AUTO

    /** Idioma del teléfono si la app lo tiene; si no, inglés. */
    fun detect(): String {
        val sys = Resources.getSystem().configuration.locales.get(0)?.language ?: "en"
        val code = if (sys == "in") "id" else sys // Indonesio usa "in" en Android antiguos
        return if (languages.any { it.code == code }) code else "en"
    }

    /** Guarda el idioma y reinicia la app para aplicarlo en todas las pantallas. */
    fun selectAndRestart(activity: Activity, code: String) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, code).commit()
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.finishAffinity()
        if (intent != null) activity.startActivity(intent)
        exitProcess(0)
    }

    fun nameOf(code: String): String = languages.firstOrNull { it.code == code }?.nativeName ?: code

    fun translate(key: String, args: Array<out Any?>): String {
        val pattern = if (current == "es") key else strings[key] ?: english[key] ?: key
        if (args.isEmpty()) return pattern
        return try {
            String.format(localeFor(current), pattern, *args)
        } catch (_: Exception) {
            // Si una traducción tuviera un marcador mal escrito, mostramos el texto original.
            try { String.format(key, *args) } catch (_: Exception) { key }
        }
    }

    private fun localeFor(code: String): Locale = when (code) {
        "pt" -> Locale.forLanguageTag("pt-BR")
        "zh" -> Locale.forLanguageTag("zh-CN")
        else -> Locale.forLanguageTag(code)
    }

    private fun load(context: Context, code: String): Map<String, String> = try {
        val text = context.assets.open("i18n/$code.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(text)
        val map = HashMap<String, String>(json.length() * 2)
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = json.getString(k)
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}

/**
 * Traduce un texto escrito en español al idioma actual. Las variables van como
 * `%1$s`, `%2$s`…: `tr("Hay %1$s equipos", n)`.
 */
fun tr(key: String, vararg args: Any?): String = I18n.translate(key, args)
