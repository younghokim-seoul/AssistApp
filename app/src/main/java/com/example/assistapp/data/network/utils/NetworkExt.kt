@file:Suppress("NOTHING_TO_INLINE")

package com.example.assistapp.data.network.utils

import com.example.assistapp.data.network.NetworkResult
import com.example.assistapp.data.network.model.Result
import org.json.JSONObject


internal val networkFailedException: Exception
    get() = IllegalStateException("Network Not Success")

internal inline fun NetworkResult.mapJson(): Result<JSONObject> =
    safeAPi(this) { response ->
        JSONObject(response)
    }

internal fun <T : Any> safeAPi(result: NetworkResult, convert: (String) -> T): Result<T> {
    return when (result) {
        is NetworkResult.Success -> {
            runCatching {
                Result.Success(convert(result.response))
            }.getOrElse { e ->
                Result.Error(e)
            }
        }

        else -> Result.Error(networkFailedException)
    }
}
