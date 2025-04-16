package com.drdisagree.iconify.utils

import com.drdisagree.iconify.Iconify.Companion.appContextLocale
import com.drdisagree.iconify.R
import com.drdisagree.iconify.data.models.InfoModel
import com.drdisagree.iconify.utils.FileUtils.readJsonFileFromAssets
import org.json.JSONArray
import org.json.JSONException

@Throws(JSONException::class)
fun parseContributors(): ArrayList<InfoModel> {
    val excludedContributors = ArrayList<String>().apply {
        add("Mahmud0808")
        add("crowdin-bot")
    }

    val contributorsList = ArrayList<InfoModel>()
    val jsonStr = readJsonFileFromAssets("Misc/contributors.json")
    val jsonArray = JSONArray(jsonStr)

    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        val name = jsonObject.getString("login")

        if (excludedContributors.contains(name)) continue // Skip the excluded contributors

        val picture = jsonObject.getString("avatar_url")
        val commitsUrl = "https://github.com/Mahmud0808/Iconify/commits?author=$name"
        val contributions = jsonObject.getInt("contributions")

        contributorsList.add(
            InfoModel(
                name,
                appContextLocale.resources.getString(R.string.total_contributions, contributions),
                commitsUrl,
                picture
            )
        )
    }

    return contributorsList
}