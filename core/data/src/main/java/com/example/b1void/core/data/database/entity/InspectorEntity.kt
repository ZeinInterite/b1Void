package com.example.b1void.core.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.b1void.core.model.Inspector // Import from core:model

@Entity(tableName = "inspectors")
data class InspectorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val photoPath: String?,
    val localPhotoPath: String?
) {
    fun toDomain(): Inspector {
        return Inspector(id, name, code, photoPath, localPhotoPath)
    }

    companion object {
        fun fromDomain(inspector: Inspector): InspectorEntity {
            return InspectorEntity(inspector.id, inspector.name, inspector.code, inspector.photoPath, inspector.localPhotoPath)
        }
    }
}
