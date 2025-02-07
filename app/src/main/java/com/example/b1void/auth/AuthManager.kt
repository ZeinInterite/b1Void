package com.example.b1void.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import com.dropbox.core.android.Auth
import com.example.b1void.R

class AuthManager(private val activity: Activity) {
    private val sharedPreferences: SharedPreferences =
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    val isAuthenticated: Boolean
        get() = accessToken != null

    fun startAuthentication() {
        Auth.startOAuth2Authentication(activity, activity.getString(R.string.APP_KEY))
    }

    fun handleAuthenticationResult(data: Intent?) {
        val accessToken = Auth.getOAuth2Token()
        if (accessToken != null) {
            saveAccessToken(accessToken)
            Toast.makeText(activity, "Authentication successful!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Authentication failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAccessToken(accessToken: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_ACCESS_TOKEN, accessToken)
        editor.apply()
    }

    val accessToken: String?
        get() = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)

    companion object {
        private const val PREF_NAME = "app_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        const val REQUEST_CODE_AUTH = 123
    }
}
