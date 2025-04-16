package com.drdisagree.iconify.utils.helper

import com.drdisagree.iconify.data.common.Const.DYNAMIC_OVERLAYABLE_PACKAGES
import com.drdisagree.iconify.data.common.Preferences.DYNAMIC_OVERLAY_RESOURCES
import com.drdisagree.iconify.data.common.Preferences.DYNAMIC_OVERLAY_RESOURCES_LAND
import com.drdisagree.iconify.data.common.Preferences.DYNAMIC_OVERLAY_RESOURCES_NIGHT
import com.drdisagree.iconify.data.common.Resources.BACKUP_DIR
import com.drdisagree.iconify.data.common.Resources.MODULE_DIR
import com.drdisagree.iconify.data.common.Resources.OVERLAY_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_MODULE_DIR
import com.drdisagree.iconify.data.common.Resources.TEMP_MODULE_OVERLAY_DIR
import com.drdisagree.iconify.data.config.RPrefs
import com.drdisagree.iconify.data.database.DynamicResourceDatabase
import com.drdisagree.iconify.data.entity.DynamicResourceEntity
import com.drdisagree.iconify.data.repository.DynamicResourceRepository
import com.drdisagree.iconify.utils.RootUtils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

object BackupRestore {

    fun backupFiles() {
        // Create backup directory
        Shell.cmd("rm -rf $BACKUP_DIR", "mkdir -p $BACKUP_DIR").exec()

        backupFile("$MODULE_DIR/system.prop")
        backupFile("$MODULE_DIR/post-exec.sh")
        backupFile("$OVERLAY_DIR/IconifyComponentCR1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentCR2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIS.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIP1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIP2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSIP3.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentPGB.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSWITCH1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentSWITCH2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic1.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic2.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic3.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic4.apk")
        backupFile("$OVERLAY_DIR/IconifyComponentDynamic5.apk")
    }

    fun restoreFiles() {
        restoreFile("system.prop", TEMP_MODULE_DIR)
        restoreFile("post-exec.sh", TEMP_MODULE_DIR)
        restoreFile("IconifyComponentCR1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentCR2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIS.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIP1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIP2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSIP3.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentPGB.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSWITCH1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentSWITCH2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic1.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic2.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic3.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic4.apk", TEMP_MODULE_OVERLAY_DIR)
        restoreFile("IconifyComponentDynamic5.apk", TEMP_MODULE_OVERLAY_DIR)

        restoreBlurSettings()

        // Remove backup directory
        Shell.cmd("rm -rf $BACKUP_DIR").exec()
    }

    private fun backupExists(fileName: String): Boolean {
        return RootUtils.fileExists("$BACKUP_DIR/$fileName")
    }

    private fun backupFile(source: String) {
        if (RootUtils.fileExists(source)) Shell.cmd("cp -rf $source $BACKUP_DIR/")
            .exec()
    }

    private fun restoreFile(fileName: String, dest: String) {
        if (backupExists(fileName)) {
            Shell.cmd("rm -rf $dest/$fileName").exec()
            Shell.cmd("cp -rf $BACKUP_DIR/$fileName $dest/").exec()
        }
    }

    private fun restoreBlurSettings() {
        if (isBlurEnabled) {
            enableBlur()
        }
    }

    private val isBlurEnabled: Boolean
        get() {
            val outs =
                Shell.cmd("if grep -q \"ro.surface_flinger.supports_background_blur=1\" $TEMP_MODULE_DIR/system.prop; then echo yes; else echo no; fi")
                    .exec().out
            return outs[0] == "yes"
        }

    private fun disableBlur() {
        Shell.cmd("mv $TEMP_MODULE_DIR/system.prop $TEMP_MODULE_DIR/system.txt; grep -v \"ro.surface_flinger.supports_background_blur\" $TEMP_MODULE_DIR/system.txt > $TEMP_MODULE_DIR/system.txt.tmp; rm -rf $TEMP_MODULE_DIR/system.prop; mv $TEMP_MODULE_DIR/system.txt.tmp $TEMP_MODULE_DIR/system.prop; rm -rf $TEMP_MODULE_DIR/system.txt; rm -rf $TEMP_MODULE_DIR/system.txt.tmp")
            .exec()
        Shell.cmd("grep -v \"ro.surface_flinger.supports_background_blur\" $TEMP_MODULE_DIR/service.sh > $TEMP_MODULE_DIR/service.sh.tmp && mv $TEMP_MODULE_DIR/service.sh.tmp $TEMP_MODULE_DIR/service.sh")
            .exec()
    }

    private fun enableBlur() {
        disableBlur()

        val blurCmd1 = "ro.surface_flinger.supports_background_blur=1"
        val blurCmd2 =
            "resetprop ro.surface_flinger.supports_background_blur 1 && killall surfaceflinger"

        Shell.cmd("echo \"$blurCmd1\" >> $TEMP_MODULE_DIR/system.prop")
            .exec()
        Shell.cmd("sed '/*}/a $blurCmd2' $TEMP_MODULE_DIR/service.sh > $TEMP_MODULE_DIR/service.sh.tmp && mv $TEMP_MODULE_DIR/service.sh.tmp $TEMP_MODULE_DIR/service.sh")
            .exec()
    }

    fun migrateToRoomDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            val resources = RPrefs.getString(DYNAMIC_OVERLAY_RESOURCES, "{}") ?: "{}"
            val resourcesLand = RPrefs.getString(DYNAMIC_OVERLAY_RESOURCES_LAND, "{}") ?: "{}"
            val resourcesNight = RPrefs.getString(DYNAMIC_OVERLAY_RESOURCES_NIGHT, "{}") ?: "{}"

            val resourceList = listOf(
                JSONObject(resources),
                JSONObject(resourcesLand),
                JSONObject(resourcesNight)
            )
            val resourceEntries = ArrayList<DynamicResourceEntity>()

            for (i in resourceList.indices) {
                for (packageName in resourceList[i].keys()) {
                    val valueJson = try {
                        resourceList[i].getJSONObject(packageName)
                    } catch (_: JSONException) {
                        null
                    } ?: continue

                    if (DYNAMIC_OVERLAYABLE_PACKAGES.contains(packageName)) {
                        for (startEndTag in valueJson.keys()) {
                            val innerValueJson = try {
                                valueJson.getJSONObject(startEndTag)
                            } catch (_: JSONException) {
                                null
                            } ?: continue

                            for (resourceName in innerValueJson.keys()) {
                                val resourceValue = try {
                                    innerValueJson.getString(resourceName)
                                } catch (_: JSONException) {
                                    null
                                } ?: continue

                                resourceEntries.add(
                                    DynamicResourceEntity(
                                        packageName = packageName,
                                        startEndTag = startEndTag,
                                        resourceName = resourceName,
                                        resourceValue = resourceValue,
                                        isPortrait = i == 0,
                                        isLandscape = i == 1,
                                        isNightMode = i == 2
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val repository = DynamicResourceRepository(
                DynamicResourceDatabase.getInstance().dynamicResourceDao()
            )

            if (resourceEntries.isNotEmpty()) {
                repository.insertResources(resourceEntries)

                RPrefs.clearPrefs(
                    DYNAMIC_OVERLAY_RESOURCES,
                    DYNAMIC_OVERLAY_RESOURCES_LAND,
                    DYNAMIC_OVERLAY_RESOURCES_NIGHT
                )
            }

            // Fix inverted startEndTag & resourceName
            repository.getAllResources().forEach { entry ->
                if (entry.startEndTag.contains("dummy") || entry.startEndTag.contains("_")) {
                    repository.deleteResources(listOf(entry))
                    repository.insertResources(
                        listOf(
                            entry.copy(
                                startEndTag = entry.resourceName,
                                resourceName = entry.startEndTag
                            )
                        )
                    )
                }
            }
        }
    }
}
