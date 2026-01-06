package com.example.b1void.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.b1void.core.data.database.dao.InspectorDao
import com.example.b1void.core.data.database.entity.InspectorEntity

@Database(entities = [InspectorEntity::class], version = 1, exportSchema = false)
abstract class B1VoidDatabase : RoomDatabase() {
    abstract fun inspectorDao(): InspectorDao
}
