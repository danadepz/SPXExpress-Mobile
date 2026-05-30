package com.spx.express.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SessionManager(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        "spx_secure_session",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_display_name"
    }

    fun createLoginSession(userId: Int, email: String, role: String, displayName: String) {
        preferences.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putInt(KEY_USER_ID, userId)
            putString(KEY_USER_ROLE, role)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, displayName)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = preferences.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserId(): Int = preferences.getInt(KEY_USER_ID, -1)

    fun getUserRole(): String? = preferences.getString(KEY_USER_ROLE, null)

    fun getUserName(): String? = preferences.getString(KEY_USER_NAME, null)

    fun getUserEmail(): String? = preferences.getString(KEY_USER_EMAIL, null)

    fun clearSession() {
        preferences.edit().clear().apply()
    }
}
