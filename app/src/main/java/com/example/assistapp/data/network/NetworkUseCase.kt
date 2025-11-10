package com.example.assistapp.data.network


import com.example.assistapp.data.network.model.IRequest
import com.example.assistapp.data.network.model.buildRequestApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import javax.inject.Inject


internal class NetworkUseCase @Inject constructor(
    private val okHttpClient: OkHttpClient
) : INetworkUseCase {


    override suspend fun requestApi(request: IRequest): NetworkResult {
        return requestApi(request.buildRequestApi())
    }

    private suspend fun requestApi(request: Request): NetworkResult {
        return runCatching {
            createTask().requestApi(request)
        }.getOrElse { e ->
            NetworkResult.Fail(e)
        }
    }

    private fun createTask(): NetworkTask =
        NetworkTask(okHttpClient)
}

internal interface INetworkUseCase {
    suspend fun requestApi(request: IRequest): NetworkResult
}
