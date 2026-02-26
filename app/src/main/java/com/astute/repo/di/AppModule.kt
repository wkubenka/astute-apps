package com.astute.repo.di

import android.content.Context
import androidx.room.Room
import com.astute.repo.BuildConfig
import com.astute.repo.data.local.AppDao
import com.astute.repo.data.local.AppDatabase
import com.astute.repo.data.remote.GitHubApi
import com.astute.repo.data.remote.ManifestApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ManifestRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    @Provides
    @Singleton
    @ManifestRetrofit
    fun provideManifestRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json; charset=utf-8".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @GitHubRetrofit
    fun provideGitHubRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json; charset=utf-8".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideManifestApi(@ManifestRetrofit retrofit: Retrofit): ManifestApi {
        return retrofit.create(ManifestApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGitHubApi(@GitHubRetrofit retrofit: Retrofit): GitHubApi {
        return retrofit.create(GitHubApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "astute_repo.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideAppDao(database: AppDatabase): AppDao {
        return database.appDao()
    }
}
