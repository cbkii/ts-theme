package com.cbkii.ts18launcher.oem;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

/**
 * Local implementation of the service's status-callback interface. The peer is outside this app's
 * trust boundary: wrong descriptors, truncated fields and unparcelable bundles are rejected and
 * never escape the Binder thread. Every accepted value is handed to deliver, which must
 * move it to the probe's worker thread.
 */
final class TopwayStatusCallback extends Binder {
    interface Deliver {
        void source(int value);
        void reverse(int value);
        void sleep(int value);
    }

    private final Deliver deliver;

    TopwayStatusCallback(Deliver deliver) {
        this.deliver = deliver;
        attachInterface(null, TopwayContract.STATUS_CALLBACK_DESCRIPTOR);
    }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == IBinder.INTERFACE_TRANSACTION) {
            if (reply != null) reply.writeString(TopwayContract.STATUS_CALLBACK_DESCRIPTOR);
            return true;
        }
        if (code < TopwayContract.CB_SYSTEM_VOLUME || code > TopwayContract.CB_EXTENDED) return false;
        try {
            data.enforceInterface(TopwayContract.STATUS_CALLBACK_DESCRIPTOR);
            switch (code) {
                case TopwayContract.CB_REVERSE_STATUS: deliver.reverse(requiredInt(data)); break;
                case TopwayContract.CB_SLEEP_STATUS: deliver.sleep(requiredInt(data)); break;
                case TopwayContract.CB_EXTENDED: {
                    Integer source = parseSource(requiredInt(data) == 0 ? null : Bundle.CREATOR.createFromParcel(data));
                    if (source != null) deliver.source(source);
                    break;
                }
                default: break; // volume, Bluetooth and call status are not used.
            }
            if (reply != null) reply.writeNoException();
            return true;
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static int requiredInt(Parcel data) {
        if (data.dataAvail() < 4) throw new IllegalArgumentException("missing int");
        return data.readInt();
    }

    /** The source reply, or null for any other or malformed extended callback. */
    static Integer parseSource(Bundle bundle) {
        if (bundle == null) return null;
        try {
            bundle.setClassLoader(TopwayStatusCallback.class.getClassLoader());
            Object action = bundle.get(TopwayContract.EXTRA_ACTION);
            Object value = bundle.get(TopwayContract.EXTRA_SOURCE_VALUE);
            if (!TopwayContract.ACTION_SOURCE_RECEIVE.equals(action) || !(value instanceof Integer)) return null;
            return (Integer) value;
        } catch (RuntimeException unparcelable) {
            return null;
        }
    }
}
