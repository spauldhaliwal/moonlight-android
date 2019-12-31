package com.limelight.data.base

sealed class Result<out T : Any?> {
    data class Success<out T : Any>(val data: T) : Result<T>()
    data class Unauthorized(val exception: Exception) : Result<Nothing>()
    data class Timeout(val exception: Exception) : Result<Nothing>()
    data class NetworkUnavailable(val exception: Exception) : Result<Nothing>()
    data class Error(val exception: Exception) : Result<Nothing>()
    data class Loading(val isLoading: Boolean = true) : Result<Nothing>()
    data class Complete(val isLoading: Boolean = false) : Result<Nothing>()
}