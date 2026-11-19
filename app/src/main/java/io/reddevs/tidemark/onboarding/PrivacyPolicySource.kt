// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.content.res.AssetManager
import io.reddevs.tidemark.onboarding.markdown.MarkdownBlock
import io.reddevs.tidemark.onboarding.markdown.MarkdownParser
import java.io.IOException
import java.util.Locale

/**
 * The bundled privacy policy. app/build.gradle.kts copies PRIVACY.md and PRIVACY.fr.md from the
 * repo root into assets/privacy/, so the app shows the same text as https://tidemark.reddevs.io/privacy/.
 */
object PrivacyPolicySource {
    const val ENGLISH_ASSET = "privacy/PRIVACY.md"
    const val FRENCH_ASSET = "privacy/PRIVACY.fr.md"

    /** French for any French locale (fr-FR, fr-CA, fr-CH ...), English otherwise. */
    fun assetFor(locale: Locale): String = if (locale.language == Locale.FRENCH.language) FRENCH_ASSET else ENGLISH_ASSET

    /** Reads and parses the policy for [locale], falling back to English if the translation is missing. */
    @Throws(IOException::class)
    fun load(assets: AssetManager, locale: Locale): List<MarkdownBlock> {
        val path = assetFor(locale)
        val text = try {
            read(assets, path)
        } catch (e: IOException) {
            if (path == ENGLISH_ASSET) throw e
            read(assets, ENGLISH_ASSET)
        }
        return MarkdownParser.parse(text)
    }

    private fun read(assets: AssetManager, path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
