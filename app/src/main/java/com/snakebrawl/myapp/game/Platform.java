package com.snakebrawl.myapp.game;

/** Services the game needs from the host (Android app or desktop harness). */
public interface Platform {
    int SND_SHOOT = 0;
    int SND_SHOTGUN = 1;
    int SND_BOLT = 2;
    int SND_THROW = 3;
    int SND_EXPLODE = 4;
    int SND_FLAME = 5;
    int SND_HIT = 6;
    int SND_EAT = 7;
    int SND_POWER = 8;
    int SND_SUPER_READY = 9;
    int SND_SUPER = 10;
    int SND_DEATH = 11;
    int SND_KILL = 12;
    int SND_VICTORY = 13;
    int SND_DEFEAT = 14;
    int SND_CLICK = 15;
    int SND_BOX = 16;
    int SND_COUNT = 17;

    void playSound(int id, float volume);

    int loadInt(String key, int def);

    void saveInt(String key, int value);

    void vibrate(int millis);
}
