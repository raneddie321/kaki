package com.google.android.datatransport;

/** Stand-in for Google's datatransport library. The billing library uses it only for usage logs; this one sends nothing. */
public final class Encoding {
    private final String name;

    private Encoding(String name) {
        this.name = name;
    }

    public static Encoding of(String name) {
        return new Encoding(name);
    }

    public String getName() {
        return name;
    }
}
