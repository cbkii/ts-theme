# Standalone TS18 launcher. Runtime classes are referenced directly by the manifest
# or by ordinary Java calls; no reflection-based framework keep rules are required.
-keepattributes SourceFile,LineNumberTable

# Loaded by root app_process from the installed APK, outside normal Activity entrypoints.
-keep class com.cbkii.ts18launcher.NavTaskBridge { *; }
-keep class com.cbkii.ts18launcher.NavTaskBridge$* { *; }
