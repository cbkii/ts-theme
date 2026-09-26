package com.cbkii.ts18launcher;

interface NavigationSurfaceBackend {
    interface Callback { void onResult(NavigationHelperResult result); }
    String label();
    void present(String packageName, String launchComponent, NavigationWindowBounds bounds,
            int taskId, int transactionId, Callback callback);
    void verify(String packageName, NavigationWindowBounds bounds, int taskId, Callback callback);
    void resume(String packageName, NavigationWindowBounds bounds, int taskId,
            int homeTaskId, Callback callback);
    void status(String packageName, int taskId, Callback callback);
    void fullscreen(String packageName, int taskId, Callback callback);
    void suspend(String packageName, int taskId, String homePackage, int homeTaskId, Callback callback);
    void destroy();
}
