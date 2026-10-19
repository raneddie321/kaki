package com.snakebrawl.myapp.game;

/** Wi-Fi play entry points. The browser build replaces this file with one that has no Wi-Fi play. */
final class Lan {
    private Lan() {}

    static boolean available() {
        return true;
    }

    static NetSession host(String name) {
        return LanSession.host(name);
    }

    static NetSession search(String name) {
        return LanSession.search(name);
    }

    static int port() {
        return LanSession.TCP_PORT;
    }
}
