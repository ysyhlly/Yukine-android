package app.yukine.streaming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LuoxueSourceStoreTest {
    @Test
    fun migratesLegacyPreferenceScriptToPrivateFileAndRetainsItAcrossStoreInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("luoxue_sources", Context.MODE_PRIVATE)
        val original = preferences.getString("sources_json", null)
        val id = "migration-${UUID.randomUUID()}"
        val script = "globalThis.lx = globalThis.lx || {}"
        val legacy = JSONArray()
            .put(
                JSONObject()
                    .put("id", id)
                    .put("name", "Legacy source")
                    .put("script", script)
                    .put("sourceKinds", JSONArray().put("kw"))
            )
            .toString()
        val store = LuoxueSourceStore(context)

        try {
            assertTrue(preferences.edit().putString("sources_json", legacy).commit())

            val migrated = store.load().single()

            assertEquals(script, migrated.script)
            assertFalse(preferences.getString("sources_json", "").orEmpty().contains(script))
            assertEquals(script, LuoxueSourceStore(context).load().single().script)
        } finally {
            store.remove(id)
            val editor = preferences.edit()
            if (original == null) {
                editor.remove("sources_json")
            } else {
                editor.putString("sources_json", original)
            }
            editor.commit()
        }
    }

    @Test
    fun persistsEnablementAndManualOrderWithoutKeepingScriptInPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LuoxueSourceStore(context)
        val first = LuoxueImportedSource(
            id = "first-${UUID.randomUUID()}",
            name = "First",
            script = "first script"
        )
        val second = LuoxueImportedSource(
            id = "second-${UUID.randomUUID()}",
            name = "Second",
            script = "second script"
        )

        try {
            assertEquals(2, store.saveAll(listOf(first, second)))
            assertTrue(store.setEnabled(first.id, false))
            assertTrue(store.move(second.id, -1))

            val restored = LuoxueSourceStore(context).load()
            val firstIndex = restored.indexOfFirst { it.id == first.id }
            val secondIndex = restored.indexOfFirst { it.id == second.id }

            assertTrue(secondIndex >= 0 && secondIndex < firstIndex)
            assertFalse(restored.first { it.id == first.id }.enabled)
            val preferences = context.getSharedPreferences("luoxue_sources", Context.MODE_PRIVATE)
            assertFalse(preferences.getString("sources_json", "").orEmpty().contains("first script"))
            assertFalse(preferences.getString("sources_json", "").orEmpty().contains("second script"))
        } finally {
            store.remove(first.id)
            store.remove(second.id)
        }
    }
}
