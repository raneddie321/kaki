package com.google.android.datatransport;

public final class Event<T> {
    private final T payload;

    private Event(T payload) {
        this.payload = payload;
    }

    public static <T> Event<T> ofData(T payload) {
        return new Event<T>(payload);
    }

    public T getPayload() {
        return payload;
    }
}
