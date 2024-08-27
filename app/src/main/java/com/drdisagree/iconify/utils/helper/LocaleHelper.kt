package com.drdisagree.iconify.utils.helper

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import com.drdisagree.iconify.R
import com.drdisagree.iconify.common.Preferences.APP_LANGUAGE
import com.drdisagree.iconify.common.Resources.SHARED_XPREFERENCES
import java.util.Locale

object LocaleHelper {

    fun setLocale(context: Context): Context {
        val prefs: SharedPreferences =
            context.createDeviceProtectedStorageContext().getSharedPreferences(
                SHARED_XPREFERENCES, MODE_PRIVATE
            )
        var localeCode = prefs.getString(APP_LANGUAGE, null)

        if (localeCode == null) {
            val supportedLocales = context.resources.getStringArray(R.array.locale_code).toSet()
            val systemLocale = context.resources.configuration.locales[0]
            localeCode = findBestMatchingLocale(systemLocale, supportedLocales) ?: "en-US"

            // Store the detected locale for future use
            prefs.edit().putString(APP_LANGUAGE, localeCode).apply()
        }

        val locale = Locale.forLanguageTag(localeCode)
        Locale.setDefault(locale) // Set default locale for the entire processreturn updateContextConfiguration(context, locale)

        return updateContextConfiguration(context, locale)
    }

    private fun findBestMatchingLocale(systemLocale: Locale, supportedLocales: Set<String>): String? {
        // Exact match
        if (supportedLocales.contains(systemLocale.toLanguageTag())) {
            return systemLocale.toLanguageTag()
        }

        // Language-only match
        val languageCode = systemLocale.language
        val matchingLocale = supportedLocales.find { it.startsWith(languageCode) }
        if (matchingLocale != null) {
            return matchingLocale
        }

        // No suitable match found
        return null
    }

    private fun updateContextConfiguration(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

}