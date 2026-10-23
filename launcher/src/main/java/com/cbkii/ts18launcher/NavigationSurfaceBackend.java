package com.cbkii.ts18launcher;

interface NavigationSurfaceBackend {
    interface Callback { void onResult(NavigationHelperResult result); }
    String label();
    default void setDeadline(long elapsedDeadlineMs) { }
    default void setColdLaunchPolicy(String policy) { }
    default void setPresentationEpoch(long epoch) { }
    void present(String packageName, String launchComponent, NavigationWindowBounds bounds,
            int taskId, int transactionId, Callback callback);
    void verify(String packageName, NavigationWindowBounds bounds, int taskId, Callback callback);
    void resume(String packageName, NavigationWindowBounds bounds, int taskId,
            String homePackage, int homeTaskId, Callback callback);
    void status(String packageName, int taskId, Callback callback);
    default void returnHome(String packageName, int taskId, String homePackage, int homeTaskId, Callback callback) {
        callback.onResult(NavigationHelperResult.failure("HOME_RETURN_UNSUPPORTED", ""));
    }
    void fullscreen(String packageName, String launchComponent, int taskId, Callback callback);
    void backgroundFullscreen(String packageName, int taskId, String homePackage,
            int homeTaskId, boolean externalOnly, Callback callback);
    void suspend(String packageName, int taskId, String homePackage, int homeTaskId, Callback callback);
    void destroy();
}
