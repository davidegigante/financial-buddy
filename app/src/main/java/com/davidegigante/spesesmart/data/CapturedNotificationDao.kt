package com.davidegigante.spesesmart.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedNotificationDao {

    /** Restituisce -1 se la notifica era già stata salvata (duplicato esatto). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: CapturedNotification): Long

    @Query("SELECT * FROM captured_notifications ORDER BY receivedAt DESC LIMIT 500")
    fun observeLatest(): Flow<List<CapturedNotification>>

    @Query("DELETE FROM captured_notifications WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM captured_notifications")
    suspend fun deleteAll()

    /** Cancella le notifiche di app non monitorate più vecchie di [before]. */
    @Query("DELETE FROM captured_notifications WHERE receivedAt < :before AND packageName NOT IN (:keepPackages)")
    suspend fun purgeUnwatched(before: Long, keepPackages: List<String>)
}
