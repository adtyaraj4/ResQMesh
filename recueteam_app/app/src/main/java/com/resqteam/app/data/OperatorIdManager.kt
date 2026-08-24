package com.resqteam.app.data

import android.content.Context

/**
 * Local rescue-operator identity used when acknowledging incidents.
 * Generated once, editable by the operator afterwards, never derived from
 * phone number/IMEI/etc.
 */
class OperatorIdManager(context: Context) {
    private val prefs = context.getSharedPreferences("resqteam_operator", Context.MODE_PRIVATE)

    fun getOperatorId(): String {
        val existing = prefs.getString(KEY_OPERATOR_ID, null)
        if (existing != null) return existing
        val generated = "RESQ-OP-${(1..99).random().toString().padStart(2, '0')}"
        prefs.edit().putString(KEY_OPERATOR_ID, generated).apply()
        return generated
    }

    fun setOperatorId(id: String) {
        prefs.edit().putString(KEY_OPERATOR_ID, id).apply()
    }

    companion object {
        private const val KEY_OPERATOR_ID = "operator_id"
    }
}
