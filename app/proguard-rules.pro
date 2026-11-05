# Arcify release shrinking rules (R8).
# Android components named in the manifest, Hilt, Room, WorkManager and Glance ship their own
# keep rules, so only project-specific cases go here.

# Keep line numbers so crash reports from release builds stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enums are stored in Room and DataStore by name.
-keepclassmembers enum app.arcify.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
