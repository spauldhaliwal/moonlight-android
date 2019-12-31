package com.limelight.data.remote.igdb

import com.limelight.data.base.BaseApi
import com.limelight.data.base.Result
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody




class IgdbGameSearchApi(
    private val endpoint: IgdbGameSearchEndpoint
) : BaseApi() {

    suspend fun getGames(gameTitle: String): Result<List<IgdbGameModel>>? {
        val bodyPlain = "fields name," +
                " summary," +
                " total_rating," +
                " age_ratings," +
                " popularity," +
                " platforms," +
                " first_release_date," +
                " genres," +
                " involved_companies," +
                " age_ratings, artworks," +
                " time_to_beat," +
                " total_rating_count," +
                " cover," +
                " artworks," +
                " screenshots; " +

                " search \"$gameTitle\"; " +
                " where popularity > 1;"
        return safeApiCall(
            call = {
                endpoint.getGames(
                    body = bodyPlain
                )
            },
            errorMessage = "Error fetching games list"
        )
    }
}