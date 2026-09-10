# Legacy D8 TS18 compatibility carrier

This is an isolated physical-test build lane. It reuses the project-authored dashboard assets/resources but deliberately reproduces the older Android build envelope shared by the supplied working DoFun themes.

It does **not** replace the normal release build until physical TS18 validation proves that the legacy carrier is required.

Key properties:

- Gradle 5.1.1 + Android Gradle Plugin 3.4.0;
- D8 1.4.77 expected and verified from the built DEX;
- minSdk 16 / targetSdk 26 / compileSdk 29;
- genuine `land` product flavor and generated `BuildConfig`;
- internal versionName `<versionCode>_<YYMMDDHHMMSS>.land`;
- one DEX, no native ABI, no Android components;
- unique project application/plugin identity and independent V1+V2 signing;
- project-authored root JSON configuration with `local_radio` and `local_music|bt_music` retained;
- no `.gen/c.json`, `layout-*` mirror, `variety.theme` SDK, vendor APK bytes, vendor code, or vendor signing material.

CI builds and verifies this carrier separately and publishes it only as a short-lived Actions artifact for physical testing.
