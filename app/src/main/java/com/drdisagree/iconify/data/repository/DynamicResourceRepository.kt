package com.drdisagree.iconify.data.repository

import com.drdisagree.iconify.data.dao.DynamicResourceDao
import com.drdisagree.iconify.data.database.DynamicResourceDatabase
import com.drdisagree.iconify.data.entity.DynamicResourceEntity

class DynamicResourceRepository(
    private val dynamicResourceDao: DynamicResourceDao = DynamicResourceDatabase
        .getInstance()
        .dynamicResourceDao()
) {

    suspend fun insertResources(resources: List<DynamicResourceEntity>) {
        dynamicResourceDao.insertResources(resources)
    }

    suspend fun deleteResources(resources: List<DynamicResourceEntity>) {
        dynamicResourceDao.deleteResources(
            resources.map { it.packageName },
            resources.map { it.startEndTag },
            resources.map { it.resourceName },
            resources.map { it.isPortrait },
            resources.map { it.isLandscape },
            resources.map { it.isNightMode }
        )
    }

    suspend fun getAllResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getAllResources()
    }

    suspend fun getResourcesForPackage(packageName: String): List<DynamicResourceEntity> {
        return dynamicResourceDao.getResourcesForPackage(packageName)
    }

    suspend fun deleteResourcesForPackage(packageName: String) {
        dynamicResourceDao.deleteResourcesForPackage(packageName)
    }

    suspend fun getPortraitResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getPortraitResources()
    }

    suspend fun getLandscapeResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getLandscapeResources()
    }

    suspend fun getNightModeResources(): List<DynamicResourceEntity> {
        return dynamicResourceDao.getNightModeResources()
    }
}