# BetterSMS has no reflection-based serialization; defaults are sufficient.
# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
