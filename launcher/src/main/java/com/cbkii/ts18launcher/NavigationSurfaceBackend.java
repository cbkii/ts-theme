package com.cbkii.ts18launcher;

interface NavigationSurfaceBackend {
    interface Callback { void onResult(NavigationHelperResult result); }
    String label();
    void present(String packageName, NavigationWindowBounds bounds, int taskId, Callback callback);
    void verify(String packageName, NavigationWindowBounds bounds, int taskId, Callback callback);
    void status(String packageName, int taskId, Callback callback);
    void fullscreen(String packageName, int taskId, Callback callback);
    void destroy();
}
