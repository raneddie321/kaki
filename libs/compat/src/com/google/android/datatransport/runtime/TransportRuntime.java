package com.google.android.datatransport.runtime;

import android.content.Context;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;

/** Drops every event: no usage logs leave the device. */
public final class TransportRuntime {
    private static final TransportRuntime INSTANCE = new TransportRuntime();

    public static void initialize(Context context) {}

    public static TransportRuntime getInstance() {
        return INSTANCE;
    }

    public TransportFactory newFactory(Destination destination) {
        return new TransportFactory() {
            @Override
            public <T> Transport<T> getTransport(String name, Class<T> type, com.google.android.datatransport.Encoding e,
                    com.google.android.datatransport.Transformer<T, byte[]> t) {
                return new Transport<T>() {
                    @Override
                    public void send(Event<T> event) {}
                };
            }
        };
    }
}
