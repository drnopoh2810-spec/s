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
    
    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = 'EXPIRED'")
    suspend fun getExpiredTransactionsCount(): Int
    
    @Query("SELECT * FROM pending_transactions WHERE status = 'EXPIRED' ORDER BY expiresAt DESC LIMIT :limit")
    suspend fun getExpiredTransactions(limit: Int = 100): List<PendingTransaction>

    @Query("""
        SELECT * FROM pending_transactions 
        WHERE (:status IS NULL OR status = :status)
        AND (:walletType IS NULL OR walletType = :walletType)
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilteredTransactions(
        status: String? = null,
        walletType: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<PendingTransaction>

    @Query("""
        SELECT COUNT(*) FROM pending_transactions
        WHERE (:status IS NULL OR status = :status)
        AND (:walletType IS NULL OR walletType = :walletType)
    """)
    suspend fun countFilteredTransactions(
        status: String? = null,
        walletType: String? = null
    ): Int

    @Delete
    suspend fun deleteTransaction(transaction: PendingTransaction)
}
