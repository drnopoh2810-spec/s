package com.sms.paymentgateway.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sms.paymentgateway.data.AppDatabase
import com.sms.paymentgateway.data.dao.PendingTransactionDao
import com.sms.paymentgateway.data.dao.SmsLogDao
import com.sms.paymentgateway.services.ConnectionMonitor
import com.sms.paymentgateway.services.DirectConnectionManager
import com.sms.paymentgateway.services.WebSocketHandler
import com.sms.paymentgateway.utils.matcher.TransactionMatcher
import com.sms.paymentgateway.utils.parser.SmsParser
import com.sms.paymentgateway.utils.security.RateLimiter
import com.sms.paymentgateway.utils.security.SecurityManager
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

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "payment_gateway_db").build()

    @Provides @Singleton
    fun provideSmsLogDao(db: AppDatabase): SmsLogDao = db.smsLogDao()

    @Provides @Singleton
    fun providePendingTransactionDao(db: AppDatabase): PendingTransactionDao = db.pendingTransactionDao()

    @Provides @Singleton
    fun provideSmsParser(): SmsParser = SmsParser()

    @Provides @Singleton
    fun provideTransactionMatcher(): TransactionMatcher = TransactionMatcher()

    @Provides @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun provideSecurityManager(@ApplicationContext ctx: Context): SecurityManager =
        SecurityManager(ctx)

    @Provides @Singleton
    fun provideRateLimiter(): RateLimiter = RateLimiter()

    @Provides @Singleton
    fun provideNetworkDetector(@ApplicationContext ctx: Context): NetworkDetector = 
        NetworkDetector(ctx)

    @Provides @Singleton
    fun provideExternalAccessManager(
        @ApplicationContext ctx: Context,
        securityManager: SecurityManager,
        networkDetector: NetworkDetector
    ): ExternalAccessManager = ExternalAccessManager(ctx, securityManager, networkDetector)

    @Provides @Singleton
    fun provideWebSocketHandler(gson: Gson): WebSocketHandler = 
        WebSocketHandler(gson)

    @Provides @Singleton
    fun provideDirectConnectionManager(
        @ApplicationContext ctx: Context,
        securityManager: SecurityManager,
        webSocketHandler: WebSocketHandler,
        networkDetector: NetworkDetector
    ): DirectConnectionManager = DirectConnectionManager(ctx, securityManager, webSocketHandler, networkDetector)

    @Provides @Singleton
    fun provideConnectionMonitor(
        @ApplicationContext ctx: Context,
        directConnectionManager: DirectConnectionManager
    ): ConnectionMonitor = ConnectionMonitor(ctx, directConnectionManager)
}
