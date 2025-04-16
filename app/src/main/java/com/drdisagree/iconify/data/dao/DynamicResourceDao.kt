package com.drdisagree.iconify.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.drdisagree.iconify.data.common.Resources.DYNAMIC_RESOURCE_TABLE
import com.drdisagree.iconify.data.entity.DynamicResourceEntity

@Dao
interface DynamicResourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<DynamicResourceEntity>)

    @Query(
        """
        DELETE FROM $DYNAMIC_RESOURCE_TABLE WHERE
        (packageName, startEndTag, resourceName, resourceValue, isPortrait, isLandscape, isNightMode) IN
        (SELECT packageName, startEndTag, resourceName, resourceValue, isPortrait, isLandscape, isNightMode FROM $DYNAMIC_RESOURCE_TABLE WHERE
        packageName IN (:packageNames) AND
        startEndTag IN (:startEndTags) AND
        resourceName IN (:resourceNames) AND
        isPortrait IN (:isPortraits) AND
        isLandscape IN (:isLandscapes) AND
        isNightMode IN (:isNightModes))
    """
    )
    suspend fun deleteResources(
        packageNames: List<String>,
        startEndTags: List<String>,
        resourceNames: List<String>,
        isPortraits: List<Boolean>,
        isLandscapes: List<Boolean>,
        isNightModes: List<Boolean>
    )

    @Query("SELECT * FROM $DYNAMIC_RESOURCE_TABLE")
    suspend fun getAllResources(): List<DynamicResourceEntity>

    @Transaction
    @Query("SELECT * FROM $DYNAMIC_RESOURCE_TABLE WHERE packageName = :packageName")
    suspend fun getResourcesForPackage(packageName: String): List<DynamicResourceEntity>

    @Query("DELETE FROM $DYNAMIC_RESOURCE_TABLE WHERE packageName = :packageName")
    suspend fun deleteResourcesForPackage(packageName: String)

    @Query("SELECT * FROM $DYNAMIC_RESOURCE_TABLE WHERE isPortrait = 1")
    suspend fun getPortraitResources(): List<DynamicResourceEntity>

    @Query("SELECT * FROM $DYNAMIC_RESOURCE_TABLE WHERE isLandscape = 1")
    suspend fun getLandscapeResources(): List<DynamicResourceEntity>

    @Query("SELECT * FROM $DYNAMIC_RESOURCE_TABLE WHERE isNightMode = 1")
    suspend fun getNightModeResources(): List<DynamicResourceEntity>
}