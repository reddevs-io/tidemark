// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reddevs.tidemark.R
import io.reddevs.tidemark.onboarding.markdown.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Health Connect only shows Tidemark's permission dialog when Tidemark answers both rationale
 * intents. These tests pin the manifest entries T-001 declared to what that contract needs.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyRationaleManifestTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager
    private val activityName = PrivacyPolicyActivity::class.java.name
    private val aliasName = "${context.packageName}.ViewPermissionUsageActivity"

    private val rationaleIntent = Intent(ACTION_SHOW_PERMISSIONS_RATIONALE).setPackage(context.packageName)
    private val usageIntent = Intent(ACTION_VIEW_PERMISSION_USAGE)
        .addCategory(CATEGORY_HEALTH_PERMISSIONS)
        .setPackage(context.packageName)

    @Suppress("DEPRECATION")
    private fun resolve(intent: Intent) = packageManager.queryIntentActivities(intent, 0)

    @Suppress("DEPRECATION")
    private fun activityInfo(name: String): ActivityInfo =
        packageManager.getActivityInfo(ComponentName(context.packageName, name), PackageManager.GET_META_DATA)

    @Test
    fun rationaleIntentResolvesToPrivacyPolicyActivityOnly() {
        val matches = resolve(rationaleIntent)
        assertEquals(1, matches.size)
        assertEquals(activityName, matches.single().activityInfo.name)
    }

    @Test
    fun permissionUsageIntentResolvesToTheAliasOnly() {
        val matches = resolve(usageIntent)
        assertEquals(1, matches.size)
        val info = matches.single().activityInfo
        assertEquals(aliasName, info.name)
        assertEquals(activityName, info.targetActivity)
    }

    @Test
    fun rationaleActivityIsOpenToHealthConnectWithoutAPermission() {
        val info = activityInfo(activityName)
        assertTrue(info.exported)
        // Health Connect on Android 9 to 13 starts it without holding any special permission.
        assertNull(info.permission)
    }

    @Test
    fun aliasIsExportedAndOnlyTheSystemMayStartIt() {
        val info = activityInfo(aliasName)
        assertTrue(info.exported)
        assertEquals(START_VIEW_PERMISSION_USAGE, info.permission)
        assertEquals(activityName, info.targetActivity)
    }

    @Test
    fun bothPolicyFilesAreBundled() {
        listOf(PrivacyPolicySource.ENGLISH_ASSET, PrivacyPolicySource.FRENCH_ASSET).forEach { path ->
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            assertTrue("$path is empty", text.isNotBlank())
        }
    }

    @Test
    fun coldLaunchFromTheRationaleIntentShowsThePolicy() {
        val intent = Intent(rationaleIntent).setClassName(context.packageName, activityName)
        ActivityScenario.launch<PrivacyPolicyActivity>(intent).use {
            compose.onNodeWithText(context.getString(R.string.privacy_policy_title)).assertExists()
            val firstLine = when (val block = PrivacyPolicySource.load(context.assets, Locale.US).first()) {
                is MarkdownBlock.Heading -> block.content
                is MarkdownBlock.Paragraph -> block.content
                is MarkdownBlock.BulletList -> block.items.first()
            }.joinToString("") { it.text }
            // The policy loads on Dispatchers.IO; wait for it to reach the screen.
            compose.waitUntil(5_000) {
                compose.waitForIdle()
                compose.onAllNodes(hasText(firstLine)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun coldLaunchThroughTheAliasShowsThePolicy() {
        val intent = Intent(usageIntent).setClassName(context.packageName, aliasName)
        ActivityScenario.launch<PrivacyPolicyActivity>(intent).use {
            compose.onNodeWithText(context.getString(R.string.privacy_policy_title)).assertExists()
        }
    }

    private companion object {
        const val ACTION_SHOW_PERMISSIONS_RATIONALE = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
        const val ACTION_VIEW_PERMISSION_USAGE = "android.intent.action.VIEW_PERMISSION_USAGE"
        const val CATEGORY_HEALTH_PERMISSIONS = "android.intent.category.HEALTH_PERMISSIONS"
        const val START_VIEW_PERMISSION_USAGE = "android.permission.START_VIEW_PERMISSION_USAGE"
    }
}
