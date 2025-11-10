package com.example.assistapp.di

import com.example.assistapp.data.local.`object`.ObjectAnalysis
import com.example.assistapp.data.local.`object`.ObjectDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindObjectDataSource(
        objectAnalysis: ObjectAnalysis
    ): ObjectDataSource
}