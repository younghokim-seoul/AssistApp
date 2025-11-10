package com.example.assistapp.data.network

import com.example.assistapp.data.network.model.IRequest
import javax.inject.Inject

internal class OpenApi @Inject constructor(
    private val networkUseCase: INetworkUseCase
) : INetworkUseCase by networkUseCase {

}