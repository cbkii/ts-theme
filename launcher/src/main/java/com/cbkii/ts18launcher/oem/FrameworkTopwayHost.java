package com.cbkii.ts18launcher.oem;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;

import java.util.function.Consumer;

/** Real binding and Binder transport for TopwayStatusProbe; one worker thread, one bind. */
final class FrameworkTopwayHost implements TopwayStatusProbe.Host {
    private final Context app;
    private final Handler worker;
    private ServiceConnection connection;

    FrameworkTopwayHost(Context context, Handler worker) {
        this.app = context.getApplicationContext();
        this.worker = worker;
    }

    @Override public boolean bind(Consumer<TopwayStatusProbe.Remote> onConnected, Runnable onLost) {
        ServiceConnection next = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                worker.post(() -> onConnected.accept(new BinderRemote(service, worker)));
            }
            @Override public void onServiceDisconnected(ComponentName name) { worker.post(onLost); }
            @Override public void onBindingDied(ComponentName name) { worker.post(onLost); }
            @Override public void onNullBinding(ComponentName name) { worker.post(onLost); }
        };
        Intent intent = new Intent(TopwayContract.ACTION_BIND)
                .setComponent(new ComponentName(TopwayContract.PACKAGE, TopwayContract.SERVICE));
        boolean accepted;
        try {
            // Flag 0: never create the vendor service; only attach if it is already running.
            accepted = app.bindService(intent, next, 0);
        } catch (SecurityException refused) {
            accepted = false;
        }
        if (accepted) connection = next;
        else safeUnbind(next);
        return accepted;
    }

    @Override public void unbind() {
        ServiceConnection current = connection;
        connection = null;
        if (current != null) safeUnbind(current);
    }

    private void safeUnbind(ServiceConnection value) {
        try { app.unbindService(value); } catch (RuntimeException notBound) { }
    }

    @Override public Object schedule(Runnable task, long delayMs) {
        Runnable token = task::run;
        worker.postDelayed(token, delayMs);
        return token;
    }

    @Override public void cancel(Object token) {
        if (token instanceof Runnable) worker.removeCallbacks((Runnable) token);
    }

    /** Synchronous transactions in the order the service's generated proxy uses. */
    static final class BinderRemote implements TopwayStatusProbe.Remote {
        private final IBinder service;
        private final Handler worker;
        private TopwayStatusCallback callback;

        BinderRemote(IBinder service, Handler worker) {
            this.service = service;
            this.worker = worker;
        }

        @Override public String descriptor() throws Exception {
            return service.getInterfaceDescriptor();
        }

        @Override public boolean registerStatus(TopwayStatusProbe.StatusListener listener) {
            callback = new TopwayStatusCallback(new TopwayStatusCallback.Deliver() {
                @Override public void source(int value) { worker.post(() -> listener.onSource(value)); }
                @Override public void reverse(int value) { worker.post(() -> listener.onReverse(value)); }
                @Override public void sleep(int value) { worker.post(() -> listener.onSleep(value)); }
            });
            return callbackTransaction(TopwayContract.REGISTER_STATUS_CALLBACK, callback);
        }

        @Override public boolean requestSource() {
            Bundle request = new Bundle();
            request.putString(TopwayContract.EXTRA_PROJECT, TopwayContract.PROJECT_SYSTEM);
            request.putString(TopwayContract.EXTRA_ACTION, TopwayContract.ACTION_SOURCE_REQUEST);
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(TopwayContract.COMMAND_DESCRIPTOR);
                data.writeInt(1);
                request.writeToParcel(data, 0);
                if (!service.transact(TopwayContract.EXTENDED_INTERFACE, data, reply, 0)) return false;
                reply.readException();
                return true;
            } catch (Exception failed) {
                return false;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        @Override public void unregisterStatus() {
            TopwayStatusCallback current = callback;
            callback = null;
            if (current != null) callbackTransaction(TopwayContract.UNREGISTER_STATUS_CALLBACK, current);
        }

        private boolean callbackTransaction(int code, IBinder value) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(TopwayContract.COMMAND_DESCRIPTOR);
                data.writeStrongBinder(value);
                if (!service.transact(code, data, reply, 0)) return false;
                reply.readException();
                return true;
            } catch (Exception failed) {
                return false;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }
}
