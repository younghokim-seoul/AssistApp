package com.example.assistapp.data.network

import com.example.assistapp.data.network.model.IRequest

internal class OpenApi(val networkUseCase: INetworkUseCase) : INetworkUseCase by networkUseCase{

}