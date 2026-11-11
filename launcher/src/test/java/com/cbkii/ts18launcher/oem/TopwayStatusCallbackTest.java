package com.cbkii.ts18launcher.oem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class TopwayStatusCallbackTest {
    private final List<String> seen = new ArrayList<>();
    private final TopwayStatusCallback callback = new TopwayStatusCallback(new TopwayStatusCallback.Deliver() {
        @Override public void source(int value) { seen.add("source=" + value); }
        @Override public void reverse(int value) { seen.add("reverse=" + value); }
        @Override public void sleep(int value) { seen.add("sleep=" + value); }
    });

    private boolean send(int code, String descriptor, java.util.function.Consumer<Parcel> body) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            body.accept(data);
            data.setDataPosition(0);
            return callback.transact(code, data, reply, 0);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static Bundle sourceReply(Object value) {
        Bundle b = new Bundle();
        b.putString("action", "Source_recieve");
        if (value instanceof Integer) b.putInt("SourceValue", (Integer) value);
        else b.putString("SourceValue", String.valueOf(value));
        return b;
    }

    @Test public void interfaceTransactionAnswersTheCallbackDescriptor() throws Exception {
        assertEquals(TopwayContract.STATUS_CALLBACK_DESCRIPTOR, callback.getInterfaceDescriptor());
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            assertTrue(callback.transact(IBinder.INTERFACE_TRANSACTION, data, reply, 0));
            reply.setDataPosition(0);
            assertEquals(TopwayContract.STATUS_CALLBACK_DESCRIPTOR, reply.readString());
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    @Test public void sourceReplyIsParsed() throws Exception {
        assertTrue(send(TopwayContract.CB_EXTENDED, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> {
            p.writeInt(1);
            sourceReply(3).writeToParcel(p, 0);
        }));
        assertEquals(java.util.Arrays.asList("source=3"), seen);
    }

    @Test public void reverseAndSleepAreParsed() throws Exception {
        send(TopwayContract.CB_REVERSE_STATUS, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> p.writeInt(1));
        send(TopwayContract.CB_SLEEP_STATUS, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> p.writeInt(0));
        assertEquals(java.util.Arrays.asList("reverse=1", "sleep=0"), seen);
    }

    @Test public void wrongDescriptorTruncatedAndUnknownCodesAreRejected() throws Exception {
        assertFalse(send(TopwayContract.CB_REVERSE_STATUS, "evil.Descriptor", p -> p.writeInt(1)));
        assertFalse(send(TopwayContract.CB_SLEEP_STATUS, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> { }));
        assertFalse(send(99, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> p.writeInt(1)));
        assertTrue(seen.isEmpty());
    }

    @Test public void otherExtendedPayloadsAndWrongTypesAreIgnored() throws Exception {
        assertTrue(send(TopwayContract.CB_EXTENDED, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> p.writeInt(0)));
        assertTrue(send(TopwayContract.CB_EXTENDED, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> {
            p.writeInt(1);
            sourceReply("3").writeToParcel(p, 0);
        }));
        Bundle other = new Bundle();
        other.putString("action", "something_else");
        other.putInt("SourceValue", 1);
        assertNull(TopwayStatusCallback.parseSource(other));
        assertTrue(seen.isEmpty());
    }

    @Test public void unusedStatusCodesAreAcceptedButNotDelivered() throws Exception {
        assertTrue(send(TopwayContract.CB_SYSTEM_VOLUME, TopwayContract.STATUS_CALLBACK_DESCRIPTOR, p -> p.writeInt(10)));
        assertTrue(seen.isEmpty());
    }
}
