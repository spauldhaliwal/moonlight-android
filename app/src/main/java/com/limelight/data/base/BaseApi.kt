package com.limelight.data.base

import com.limelight.data.base.Result.Error
import com.limelight.data.base.Result.NetworkUnavailable
import com.limelight.data.base.Result.Success
import com.limelight.data.base.Result.Timeout
import com.limelight.data.base.Result.Unauthorized
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.io.IOException

/**
 * BaseApi class. Implements safeApi call which handles errors for its children.
 */

abstract class BaseApi {

    //TODO setup auto timeout retry system
    private val timeout = 10_000L

    suspend fun <T : Any> safeApiCall(call: suspend () -> Response<T>, errorMessage: String): Result<T>? {
        return safeApiResult(call, errorMessage)
    }

    /**
     * @throws NoConnectivityException if network is unavailable
     */
    @Throws(NoConnectivityException::class)
    private suspend fun <T : Any> safeApiResult(
        call: suspend () -> Response<T>,
        errorMessage: String
    ): Result<T>? {
        try {
            val result = withTimeoutOrNull(timeout) {
                val response = call.invoke()
                if (response.isSuccessful) {
                    return@withTimeoutOrNull Success(response.body()!!)
                } else {
                    return@withTimeoutOrNull when (response.code()) {
                        401 -> Unauthorized(IOException(errorMessage + response.code()))
                        else -> Error(IOException(errorMessage + response.code()))
                    }
                }
            }
            /**
             * @return timeout exception
             */
            return result ?: Timeout(IOException("network timeout @$timeout ms"))
        }
        /**
         * @return NetworkUnavailable exception
         */
        catch (e: NoConnectivityException) {
            return NetworkUnavailable(e)
        }
    }
}