package com.drdisagree.iconify.utils.overlay.manager.resource

import android.util.Log
import android.widget.Toast
import com.drdisagree.iconify.Iconify.Companion.appContext
import com.drdisagree.iconify.Iconify.Companion.appContextLocale
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.common.Const.DYNAMIC_OVERLAYABLE_PACKAGES
import com.drdisagree.iconify.data.database.DynamicResourceDatabase
import com.drdisagree.iconify.data.entity.DynamicResourceEntity
import com.drdisagree.iconify.data.repository.DynamicResourceRepository
import com.drdisagree.iconify.utils.SystemUtils.hasStoragePermission
import com.drdisagree.iconify.utils.SystemUtils.requestStoragePermission
import com.drdisagree.iconify.utils.overlay.compiler.DynamicCompiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object ResourceManager {

    enum class ResourceType {
        PORTRAIT, LANDSCAPE, NIGHT
    }

    private val TAG = ResourceManager::class.java.simpleName
    private val repository: DynamicResourceRepository
        get() = DynamicResourceRepository(
            DynamicResourceDatabase.getInstance().dynamicResourceDao()
        )

    suspend fun buildOverlayWithResource(vararg resourceEntries: ResourceEntry?): Boolean {
        if (!hasStoragePermission()) {
            requestStoragePermission(appContext)
        } else {
            val hasErroredOut = AtomicBoolean(false)

            saveResources(*resourceEntries.filterNotNull().toTypedArray())

            try {
                val distinctPackageNames = resourceEntries.filterNotNull()
                    .map { it.packageName }
                    .distinct()

                hasErroredOut.set(DynamicCompiler.buildDynamicOverlay(overlaysToUpdate = distinctPackageNames))
            } catch (e: Exception) {
                Log.i(TAG, "buildOverlayWithResource: ", e)
                hasErroredOut.set(true)
            }

            showToast(hasErroredOut)

            return hasErroredOut.get()
        }

        return true
    }

    suspend fun removeResourceFromOverlay(vararg resourceEntries: ResourceEntry?): Boolean {
        if (!hasStoragePermission()) {
            requestStoragePermission(appContext)
        } else {
            val hasErroredOut = AtomicBoolean(false)

            removeResources(*resourceEntries.filterNotNull().toTypedArray())

            try {
                val distinctPackageNames = resourceEntries.filterNotNull()
                    .map { it.packageName }
                    .distinct()

                hasErroredOut.set(DynamicCompiler.buildDynamicOverlay(overlaysToUpdate = distinctPackageNames))
            } catch (e: Exception) {
                Log.i(TAG, "removeResourceFromOverlay: ", e)
                hasErroredOut.set(true)
            }

            showToast(hasErroredOut)

            return hasErroredOut.get()
        }

        return true
    }

    private suspend fun saveResources(vararg resourceEntries: ResourceEntry) {
        repository.insertResources(
            resourceEntries.map { entry ->
                DynamicResourceEntity(
                    packageName = entry.packageName,
                    startEndTag = entry.startEndTag,
                    resourceName = entry.resourceName,
                    resourceValue = entry.resourceValue,
                    isPortrait = entry.isPortrait,
                    isLandscape = entry.isLandscape,
                    isNightMode = entry.isNightMode,
                )
            }
        )
    }

    suspend fun removeResources(vararg resourceEntries: ResourceEntry) {
        repository.deleteResources(
            resourceEntries.map { entry ->
                DynamicResourceEntity(
                    packageName = entry.packageName,
                    startEndTag = entry.startEndTag,
                    resourceName = entry.resourceName,
                    resourceValue = entry.resourceValue,
                    isPortrait = entry.isPortrait,
                    isLandscape = entry.isLandscape,
                    isNightMode = entry.isNightMode,
                )
            }
        )
    }

    suspend fun generateXmlStructureForAllResources(overlaysToUpdate: List<String>?): MutableMap<String, MutableMap<ResourceType, ArrayList<String>>> {
        val resourceEntries = repository.getAllResources()
        val resourcesGrouped = resourceEntries.groupBy { it.packageName }
        val result: MutableMap<String, MutableMap<ResourceType, ArrayList<String>>> = mutableMapOf()
        val emptyResources = getEmptyResources()

        resourcesGrouped.forEach { (packageName, resources) ->
            if (!DYNAMIC_OVERLAYABLE_PACKAGES.contains(packageName)) {
                throw Exception("Package $packageName is not dynamically overlayable.")
            }

            if (overlaysToUpdate != null && packageName !in overlaysToUpdate) {
                return@forEach
            }

            val xmlPartsMap: MutableMap<ResourceType, ArrayList<String>> = mutableMapOf()

            val groupedByType = resources.groupBy {
                when {
                    it.isPortrait -> ResourceType.PORTRAIT
                    it.isLandscape -> ResourceType.LANDSCAPE
                    it.isNightMode -> ResourceType.NIGHT
                    else -> throw Exception("Invalid resource type")
                }
            }

            val xmlParts = ArrayList<String>()

            groupedByType.forEach { (type, group) ->
                xmlParts.clear()
                xmlParts.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                xmlParts.add("<resources>")
                group.forEach { entry ->
                    xmlParts.add("<${entry.startEndTag} name=\"${entry.resourceName}\">${entry.resourceValue}</${entry.startEndTag}>")
                }
                xmlParts.add("</resources>")

                xmlPartsMap[type] = ArrayList(xmlParts)

                if (type == ResourceType.PORTRAIT && group.isEmpty()) {
                    xmlPartsMap[type] = ArrayList(emptyResources[packageName]!!)
                }
            }

            result[packageName] = xmlPartsMap
        }

        overlaysToUpdate?.forEach { packageName ->
            if (packageName !in result) {
                result[packageName] = mutableMapOf(
                    ResourceType.PORTRAIT to ArrayList(
                        emptyResources[packageName]!!
                    )
                )
            }
        }

        return result
    }

    private fun getEmptyResources(): MutableMap<String, ArrayList<String>> {
        val result: MutableMap<String, ArrayList<String>> = mutableMapOf()

        for (i in DYNAMIC_OVERLAYABLE_PACKAGES.indices) {
            result[DYNAMIC_OVERLAYABLE_PACKAGES[i]] = ArrayList<String>().apply {
                add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                add("<resources>")
                add("<color name=\"dummy${i + 1}\">#00000000</color>")
                add("</resources>")
            }
        }

        return result
    }

    private suspend fun showToast(hasErroredOut: AtomicBoolean) {
        withContext(Dispatchers.Main) {
            delay(500)
            if (!hasErroredOut.get()) {
                Toast.makeText(
                    appContext,
                    appContextLocale.resources.getString(R.string.toast_applied),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    appContext,
                    appContextLocale.resources.getString(R.string.toast_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
