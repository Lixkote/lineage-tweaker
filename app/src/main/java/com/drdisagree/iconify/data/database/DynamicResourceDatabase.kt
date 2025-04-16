package com.drdisagree.iconify.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.data.common.Resources.DYNAMIC_RESOURCE_DATABASE_NAME
import com.drdisagree.iconify.data.dao.DynamicResourceDao
import com.drdisagree.iconify.data.entity.DynamicResourceEntity

@Database(entities = [DynamicResourceEntity::class], version = 2)
abstract class DynamicResourceDatabase : RoomDatabase() {
    abstract fun dynamicResourceDao(): DynamicResourceDao

    companion object {
        @Volatile
        private var INSTANCE: DynamicResourceDatabase? = null

        fun getInstance(): DynamicResourceDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    appContext,
                    DynamicResourceDatabase::class.java,
                    DYNAMIC_RESOURCE_DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }

        fun reloadInstance() {
            synchronized(this) {
                INSTANCE = Room.databaseBuilder(
                    appContext,
                    DynamicResourceDatabase::class.java,
                    DYNAMIC_RESOURCE_DATABASE_NAME
                ).build()
            }
        }
    }
}