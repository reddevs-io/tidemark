// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PrivacyPolicySourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun frenchLocalesGetTheFrenchPolicy() {
        listOf(Locale.FRANCE, Locale.CANADA_FRENCH, Locale.forLanguageTag("fr-CH"), Locale.FRENCH).forEach {
            assertEquals(it.toLanguageTag(), PrivacyPolicySource.FRENCH_ASSET, PrivacyPolicySource.assetFor(it))
        }
    }

    @Test
    fun everyOtherLocaleGetsTheEnglishPolicy() {
        listOf(Locale.US, Locale.UK, Locale.GERMANY, Locale.forLanguageTag("es-419")).forEach {
            assertEquals(it.toLanguageTag(), PrivacyPolicySource.ENGLISH_ASSET, PrivacyPolicySource.assetFor(it))
        }
    }

    @Test
    fun loadsAndParsesTheBundledPolicyInBothLanguages() {
        assertTrue(PrivacyPolicySource.load(context.assets, Locale.US).isNotEmpty())
        val french = PrivacyPolicySource.load(context.assets, Locale.FRANCE)
        assertTrue(french.isNotEmpty())
        val english = PrivacyPolicySource.load(context.assets, Locale.US)
        // The two files are distinct translations, not the same file twice.
        assertTrue(french != english)
    }
}
