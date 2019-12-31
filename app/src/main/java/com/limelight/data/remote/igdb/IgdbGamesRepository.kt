package com.limelight.data.remote.igdb

import com.limelight.data.base.Result
import com.limelight.data.base.Result.Success
import kotlinx.coroutines.runBlocking

class IgdbGamesRepository(
    private val igdbGameSearchApi: IgdbGameSearchApi
) {

    suspend fun searchGames(gameTitle: String): Result<List<IgdbGameModel>>? {
        val result = igdbGameSearchApi.getGames(
            gameTitle = gameTitle
        )
        return result
    }

    fun searchGamesAsync(gameTitle: String): List<IgdbGameModel> {
        val result = runBlocking {
            searchGames(gameTitle)
        }
        return when (result) {
            is Success -> {
                val gamesListExactMatch = result.data.filter { it.name.toLowerCase().trim() == gameTitle.toLowerCase().trim() }
                val gamesListByPopularity = result.data.sortedByDescending { it.popularity }

                val gameListReturned :List<IgdbGameModel>

                gameListReturned = if (gamesListExactMatch.isNotEmpty()) {
                    gamesListExactMatch
                } else {
                    gamesListByPopularity
                }

                gameListReturned
            }
            else -> {
                emptyList()
            }
        }
    }
}