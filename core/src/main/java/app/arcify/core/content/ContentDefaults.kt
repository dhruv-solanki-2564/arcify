package app.arcify.core.content

/** A browser whose address bar Arcify reads, identified by the address bar's view ID. */
data class MonitoredBrowser(val packageName: String, val urlBarIds: List<String>)

/**
 * Bundled defaults. Every list is editable by the user and can be reset to these values.
 * Device-specific facts (view IDs) are marked as unverified until checked on a real phone.
 */
object ContentDefaults {

    /** Well-known lo-fi / study-music channels (spec F05). Handles are best-known values. */
    val studyChannels: List<StudyChannel> = listOf(
        StudyChannel("Lofi Girl", "@LofiGirl", isDefault = true),
        StudyChannel("Chillhop Music", "@ChillhopMusic", isDefault = true),
        StudyChannel("College Music", "@CollegeMusic", isDefault = true),
        StudyChannel("Lofi Records", null, isDefault = true),
        StudyChannel("The Jazz Hop Café", "@TheJazzHopCafe", isDefault = true),
        StudyChannel("Chillhop Raccoon", null, isDefault = true),
        StudyChannel("STEEZYASFUCK", "@STEEZYASFUCK", isDefault = true),
        StudyChannel("Homework Radio", "@HomeworkRadio", isDefault = true),
    )

    /** Chrome and Brave (Chromium-based) expose the address bar under the same ID name. Unverified. */
    val browsers: List<MonitoredBrowser> = listOf(
        MonitoredBrowser("com.android.chrome", listOf("com.android.chrome:id/url_bar")),
        MonitoredBrowser("com.brave.browser", listOf("com.brave.browser:id/url_bar")),
    )

    /** Packages whose screens are scanned for short-form players, per platform. */
    val shortFormApps: Map<String, Platform> = mapOf(
        "com.instagram.android" to Platform.INSTAGRAM,
        "com.instagram.lite" to Platform.INSTAGRAM,
        "com.google.android.youtube" to Platform.YOUTUBE,
        "com.snapchat.android" to Platform.SNAPCHAT,
        "com.facebook.katana" to Platform.FACEBOOK,
        "com.facebook.lite" to Platform.FACEBOOK,
    )

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
}
