package com.drdisagree.iconify.data.entity

import androidx.room.Entity
import androidx.room.Index
import com.drdisagree.iconify.data.common.Resources.DYNAMIC_RESOURCE_TABLE

@Entity(
    tableName = DYNAMIC_RESOURCE_TABLE,
    indices = [Index("packageName")],
    primaryKeys = ["packageName", "resourceName", "startEndTag", "isPortrait", "isLandscape", "isNightMode"]
)
data class DynamicResourceEntity(
    val packageName: String,
    var startEndTag: String,
    var resourceName: String,
    var resourceValue: String,
    var isPortrait: Boolean,
    var isLandscape: Boolean,
    var isNightMode: Boolean
)