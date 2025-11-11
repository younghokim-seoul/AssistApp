package com.example.assistapp.data.network.api

import com.example.assistapp.data.network.INetworkUseCase
import javax.inject.Inject

//internal class NaverApi @Inject constructor(
//    private val networkUseCase: INetworkUseCase
//) : INetworkUseCase by networkUseCase {
//
//    suspend fun invoke(param: EpisodeApi.Param){
//
//    }
//
//}
//
//
//
//internal interface NaverApi {
//    suspend operator fun invoke(param: Param): Result<EpisodeResult>
//
//    class Param(
//        val version: String,
//        val requestId: String,
//        val page: Int,
//        val images: List<Image>,
//        val enableTableDetection : Boolean = true
//    )
//    data class Image(
//        val format: String,
//        val name: String,
//        val data: String,
//    )
//}