package com.cbkii.ts18launcher.oem;

/**
 * Binary interface of the exported command service in the exact TS18 com.tw.service.xt
 * APK identified below. Only identification, status-callback registration and the source query
 * are represented. Nothing here can change Topway source, radio, volume, light, screen or MCU
 * state, and no such transaction may be added without caller authority, single-writer
 * arbitration, a reversible exercise, physical observation and owner approval.
 */
public final class TopwayContract {
    private TopwayContract() {}

    public static final String PACKAGE = "com.tw.service.xt";
    public static final String SERVICE = "com.tw.service.xt.CommandService";
    public static final String ACTION_BIND = "com.tw.service.xt.CommandService.Bind";

    /** SHA-256 of the exact XTService APK (versionName 2022.02.17) the contract was read from. */
    public static final String VERIFIED_APK_SHA256 =
            "341af03ccbaeb6a7debe1929153eaadf9ced421d64a4933016010e0e7aa77267";
    /** Topway "TW" signer certificate SHA-256. Identification only; not a credential. */
    public static final String TOPWAY_SIGNER_SHA256 =
            "aa6f9fb3070512ac962425797cd65aa585cf6202937ee3ceefb14b5802eabdf3";

    public static final String COMMAND_DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandAidl";
    public static final String STATUS_CALLBACK_DESCRIPTOR = "com.tw.service.xt.aidl.ITWCommandCallbackAidl";

    /** ITWCommandAidl transactions used here. The music callback (5/6) is deliberately absent:
     *  registering it routes steering-wheel music keys to the registrant. */
    static final int REGISTER_STATUS_CALLBACK = 1;
    static final int UNREGISTER_STATUS_CALLBACK = 2;
    static final int EXTENDED_INTERFACE = 67;

    /** ITWCommandCallbackAidl callbacks (one-way from the service). */
    static final int CB_SYSTEM_VOLUME = 1;
    static final int CB_VOLUME_STATUS = 2;
    static final int CB_BT_PHONE_STATUS = 3;
    static final int CB_BT_CALL_STATUS = 4;
    static final int CB_BT_CONNECTED_STATUS = 5;
    static final int CB_REVERSE_STATUS = 6;
    static final int CB_SLEEP_STATUS = 7;
    static final int CB_EXTENDED = 8;

    static final String EXTRA_PROJECT = "project";
    static final String EXTRA_ACTION = "action";
    static final String PROJECT_SYSTEM = "system";
    static final String ACTION_SOURCE_REQUEST = "source_request";
    /** Exact vendor spelling. */
    static final String ACTION_SOURCE_RECEIVE = "Source_recieve";
    static final String EXTRA_SOURCE_VALUE = "SourceValue";

    /** Topway source values routed by the service. */
    public enum Source {
        RADIO(1), LOCAL_MUSIC(3), BLUETOOTH(8), VIDEO(9), OTHER(-1);

        public final int value;
        Source(int value) { this.value = value; }

        public static Source of(int value) {
            for (Source source : values()) if (source.value == value) return source;
            return OTHER;
        }
    }
}
