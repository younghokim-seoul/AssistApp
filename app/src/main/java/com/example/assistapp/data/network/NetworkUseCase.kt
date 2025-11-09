package com.example.assistapp.data.network


import com.example.assistapp.data.network.model.IRequest
import com.example.assistapp.data.network.model.buildRequestApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber


internal class NetworkUseCase : INetworkUseCase {

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor { message ->
                Timber.tag("OkHttp").d(message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }).build()
    }

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
