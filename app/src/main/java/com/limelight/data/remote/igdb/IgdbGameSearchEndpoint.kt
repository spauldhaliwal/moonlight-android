package com.limelight.data.remote.igdb

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface IgdbGameSearchEndpoint {

    // TODO notifications for specific business needs to be added.
    //  Waiting for api endpoint to be finished.
    @POST("games")
    suspend fun getGames(
        @Body body: String
    ): Response<List<IgdbGameModel>>
}