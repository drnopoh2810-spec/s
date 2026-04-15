package com.sms.paymentgateway.data.dao

import androidx.room.*
import com.sms.paymentgateway.data.entities.PendingTransaction
import com.sms.paymentgateway.data.entities.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {
    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingTransactions(): Flow<List<PendingTransaction>>
    
    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): PendingTransaction?
    
    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' AND amount = :amount AND phoneNumber = :phone")
    suspend fun findMatchingTransaction(amount: Double, phone: String): List<PendingTransaction>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: PendingTransaction)
    
    @Update
    suspend fun updateTransaction(transaction: PendingTransaction)
    
    @Query("UPDATE pending_transactions SET status = 'EXPIRED' WHERE expiresAt < :currentTime AND status = 'PENDING'")
    suspend fun expireOldTransactions(currentTime: Long)
    
    @Delete
    suspend fun deleteTransaction(transaction: PendingTransaction)
}
