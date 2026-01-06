package com.example.b1void.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.b1void.core.data.database.entity.InspectorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectorDao {
    @Query("SELECT * FROM inspectors")
    fun getAllInspectors(): Flow<List<InspectorEntity>>

    @Query("SELECT * FROM inspectors WHERE id = :inspectorId")
    suspend fun getInspectorById(inspectorId: String): InspectorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspector(inspector: InspectorEntity)

    @Query("DELETE FROM inspectors WHERE id = :inspectorId")
    suspend fun deleteInspectorById(inspectorId: String)
}
