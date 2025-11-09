package com.example.assistapp.data.network

/**
 * Network Result 정보
 */
internal sealed class NetworkResult {
    class Success(val response: String) : NetworkResult()
    class Fail(val throwable: Throwable) : NetworkResult()
}
