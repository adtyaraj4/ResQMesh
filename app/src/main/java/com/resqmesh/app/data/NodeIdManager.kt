package com.resqmesh.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

/**
 * Generates and persists a random, non-identifying Node ID for this device.
 *
 * The ID is generated ONCE on first launch and stored in SharedPreferences.
 * It intentionally does NOT use the phone number, IMEI, or any other
 * personally identifying hardware value — only a random identifier,
 * per the ResQMesh node model spec (Section 5).
 *
 * Format: NODE-XXXXXX where X is an uppercase hex character.
 * Example: NODE-A7F291
 */
class NodeIdManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the persistent node ID for this device, generating and
     * saving a new one on first call if none exists yet.
     */
    fun getOrCreateNodeId(): String {
        val existing = prefs.getString(KEY_NODE_ID, null)
        if (existing != null) return existing

        val newId = generateNodeId()
        prefs.edit().putString(KEY_NODE_ID, newId).apply()
        return newId
    }

    private fun generateNodeId(): String {
        val chars = "0123456789ABCDEF"
        val suffix = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "NODE-$suffix"
    }

    companion object {
        private const val PREFS_NAME = "resqmesh_node_prefs"
        private const val KEY_NODE_ID = "node_id"
    }
}
