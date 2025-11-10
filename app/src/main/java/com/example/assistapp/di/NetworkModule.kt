package com.example.assistapp.di


import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.example.assistapp.BuildConfig
import com.example.assistapp.data.network.INetworkUseCase
import com.example.assistapp.data.network.NetworkUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlin.apply
import kotlin.time.Duration.Companion.seconds


@InstallIn(SingletonComponent::class)
@Module
internal object NetworkModule {
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor { message ->
        Timber.tag("OkHttp").d(message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    @Singleton
    @Provides
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    @Provides
    @Singleton
    fun provideOpenAIClient(okHttpClient: OkHttpClient): OpenAI {

        val ktorEngine = io.ktor.client.engine.okhttp.OkHttp.create {
            preconfigured = okHttpClient
        }
        val apiKey = BuildConfig.ACCESS_KEY
        Timber.i("apiKey=> " + apiKey)
        val config = OpenAIConfig(
            token = apiKey,
            timeout = Timeout(socket = 60.seconds),
            logging = LoggingConfig(LogLevel.Body),
            engine = ktorEngine

        )
        return OpenAI(config)
    }

    @Provides
    @Singleton
    fun provideJsonParser(): Json {
        return Json {
            ignoreUnknownKeys = true // 알 수 없는 키는 무시
            isLenient = true // JSON 형식이 조금 느슨해도 파싱
        }
    }
}

@Suppress("unused")
@InstallIn(SingletonComponent::class)
@Module
internal abstract class NetworkModuleBinds {
    @Binds
    abstract fun bindsNetworkUseCase(
        networkUseCase: NetworkUseCase
    ): INetworkUseCase
}