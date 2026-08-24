package com.resqteam.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(incident: IncidentEntity)

    @Update
    suspend fun update(incident: IncidentEntity)

    @Query("SELECT * FROM incidents WHERE messageId = :messageId LIMIT 1")
    suspend fun findByMessageId(messageId: String): IncidentEntity?

    /**
     * Active queue: priority first (5 = CRITICAL at top), then most recent.
     * Spec section 39: never let timestamp-only sorting bury a critical incident.
     */
    @Query(
        """
        SELECT * FROM incidents
        WHERE status != 'RESCUED' AND status != 'RESOLVED'
        ORDER BY priority DESC, receivedAt DESC
        """
    )
    fun observeActiveIncidents(): Flow<List<IncidentEntity>>

    @Query(
        """
        SELECT * FROM incidents
        WHERE status = 'RESCUED' OR status = 'RESOLVED'
        ORDER BY resolvedAt DESC
        """
    )
    fun observeHistory(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE messageId = :messageId LIMIT 1")
    fun observeById(messageId: String): Flow<IncidentEntity?>

    @Query("SELECT COUNT(*) FROM incidents WHERE status != 'RESCUED' AND status != 'RESOLVED'")
    fun observeActiveCount(): Flow<Int>
}
