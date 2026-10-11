package yuku.alkitab.base.feedback

data class PrereleaseVersion(val channel: String, val label: String) {
    fun emailSubject(appName: String, fullVersionName: String, commitHash: String): String =
        "$channel feedback $appName $fullVersionName ($commitHash)"

    companion object {
        fun parse(versionName: String): PrereleaseVersion? {
            val match = Regex("-(beta|dev)\\.(\\d+)$").find(versionName) ?: return null
            return if (match.groupValues[1] == "beta") {
                PrereleaseVersion("Beta", "Beta")
            } else {
                PrereleaseVersion("Dev", "Dev ${match.groupValues[2]}")
            }
        }
    }
}
