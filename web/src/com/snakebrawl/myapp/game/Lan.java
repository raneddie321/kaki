package com.snakebrawl.myapp.game;

/** Browser build: no Wi-Fi play (browsers cannot open sockets). Online play works instead. */
final class Lan {
    private Lan() {}

    static boolean available() {
        return false;
    }

    static NetSession host(String name) {
        throw new IllegalStateException("no Wi-Fi play in the browser");
    }

    static NetSession search(String name) {
        throw new IllegalStateException("no Wi-Fi play in the browser");
    }

    static int port() {
        return 0;
    }
}
