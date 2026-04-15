package com.sms.paymentgateway.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sms.paymentgateway.data.AppDatabase
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.services.ConnectionMonitor
import com.sms.paymentgateway.services.RelayClient
import com.sms.paymentgateway.utils.matcher.TransactionMatcher
import com.sms.paymentgateway.utils.parser.SmsParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "payment_gateway_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideSmsLogDao(database: AppDatabase): SmsLogDao {
        return database.smsLogDao()
    }

    @Provides
    @Singleton
    fun providePendingTransactionDao(database: AppDatabase): PendingTransactionDao {
        return database.pendingTransactionDao()
    }

    @Provides
    @Singleton
    fun provideSmsParser(): SmsParser {
        return SmsParser()
    }

    @Provides
    @Singleton
    fun provideTransactionMatcher(): TransactionMatcher {
        return TransactionMatcher()
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRelayClient(@ApplicationContext context: Context): RelayClient {
        return RelayClient(context)
    }

    @Provides
    @Singleton
    fun provideConnectionMonitor(
        @ApplicationContext context: Context,
        relayClient: RelayClient
    ): ConnectionMonitor {
        return ConnectionMonitor(context, relayClient)
    }
}
