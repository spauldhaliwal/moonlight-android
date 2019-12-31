package com.limelight.data.remote.igdb

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.limelight.MoonlightApplication
import com.limelight.nvstream.http.NvApp

class GameMetadataCacheRepository {

    val context = MoonlightApplication.applicationContext()

    private val authPreferencesName = "com.limelight.gamemetadata"
    private val authPreferencesTokenTypeKey = "TOKEN_TYPE"
    private val authPreferencesExpiresInKey = "EXPIRES_IN"
    private val authPreferencesAccessTokenKey = "accessToken"
    private val authPreferencesRefreshToken = "REFRESH_TOKEN"
    private val authPreferencesDefaultStoreId = "DEFAULT_STORE"

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(authPreferencesName, Context.MODE_PRIVATE)

    fun getGameMetadata(nvApp: NvApp): IgdbGameModel? {
        val json = sharedPreferences.getString(nvApp.appId.toString(), "null")!!
        if (json.equals("null")) {
            return null
        }
        val gson = Gson()
        return gson.fromJson(json, IgdbGameModel::class.java)
    }

    fun saveGameMetaData(nvApp: NvApp, gameMetaData: IgdbGameModel) {
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(gameMetaData) //

        editor.putString(nvApp.appId.toString(), json)
        editor.apply()
    }

    fun clearAllMetaData() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
}