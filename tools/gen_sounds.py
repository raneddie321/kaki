#!/usr/bin/env python3
"""Synthesizes the game's sound effects into app/src/main/res/raw/*.wav (22.05 kHz mono 16-bit)."""
import os
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, attack=0.005, decay_pow=2.0):
    x = np.linspace(0, 1, n)
    a = np.clip(np.arange(n) / max(1, int(SR * attack)), 0, 1)
    return a * (1 - x) ** decay_pow


def sweep(f0, f1, dur, shape="sine"):
    t = t_axis(dur)
    f = np.geomspace(f0, f1, len(t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "square":
        return np.sign(np.sin(ph)) * 0.6
    if shape == "tri":
        return 2 / np.pi * np.arcsin(np.sin(ph))
    return np.sin(ph)


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def note(freq, dur, shape="tri", decay=1.5):
    t = t_axis(dur)
    if shape == "square":
        w = np.sign(np.sin(2 * np.pi * freq * t)) * 0.5
    elif shape == "sine":
        w = np.sin(2 * np.pi * freq * t)
    else:
        w = 2 / np.pi * np.arcsin(np.sin(2 * np.pi * freq * t))
    return w * env(len(t), 0.004, decay)


def seq(parts, gap=0.0):
    out = []
    for p in parts:
        out.append(p)
        if gap:
            out.append(np.zeros(int(SR * gap)))
    return np.concatenate(out)


def mix(*xs):
    n = max(len(x) for x in xs)
    y = np.zeros(n)
    for x in xs:
        y[: len(x)] += x
    return y


def save(name, x, gain=0.8):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * gain
    fade = min(len(x), int(SR * 0.004))
    x[-fade:] *= np.linspace(1, 0, fade)
    data = (x * 32767).astype("<i2").tobytes()
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


def main():
    os.makedirs(OUT, exist_ok=True)
    d = 0.09
    save("snd_shoot", sweep(900, 380, d, "square") * env(int(SR * d)), 0.55)

    d = 0.2
    n = int(SR * d)
    save("snd_shotgun", mix(lowpass(noise(d), 3000) * env(n, 0.001, 3), sweep(160, 50, d) * env(n, 0.001, 2) * 0.8), 0.7)

    d = 0.16
    n = int(SR * d)
    zap = sweep(2200, 260, d, "square") * env(n, 0.001, 1.6)
    save("snd_bolt", mix(zap, noise(d) * env(n, 0.001, 4) * 0.2), 0.5)

    d = 0.22
    n = int(SR * d)
    whoosh = lowpass(noise(d), 1500) * np.sin(np.linspace(0, np.pi, n)) ** 2
    save("snd_throw", mix(whoosh, sweep(300, 600, d, "tri") * np.sin(np.linspace(0, np.pi, n)) * 0.3), 0.55)

    d = 0.7
    n = int(SR * d)
    boom = lowpass(noise(d), 900) * env(n, 0.002, 2.5)
    thump = sweep(120, 35, d) * env(n, 0.001, 3)
    save("snd_explode", mix(boom, thump * 0.9), 0.9)

    d = 0.32
    n = int(SR * d)
    crackle = lowpass(noise(d), 2500) * (0.6 + 0.4 * (rng.uniform(0, 1, n) > 0.92))
    save("snd_flame", crackle * np.sin(np.linspace(0, np.pi, n)) ** 0.7, 0.5)

    d = 0.07
    n = int(SR * d)
    save("snd_hit", mix(sweep(420, 160, d, "square") * env(n, 0.001, 2) * 0.6, lowpass(noise(d), 4000) * env(n, 0.001, 4)), 0.6)

    d = 0.06
    save("snd_eat", sweep(1100, 1700, d, "sine") * env(int(SR * d), 0.002, 1.2), 0.35)

    save("snd_power", seq([note(f, 0.09, "square", 1.0) for f in (523, 659, 784, 1047)]), 0.45)

    save("snd_super_ready", seq([note(880, 0.12, "sine", 1.2), note(1319, 0.3, "sine", 1.8)]), 0.5)

    d = 0.5
    n = int(SR * d)
    rise = sweep(200, 1400, d, "square") * np.sin(np.linspace(0, np.pi, n)) * 0.4
    save("snd_super", mix(rise, lowpass(noise(d), 2000) * env(n, 0.01, 1.5) * 0.6), 0.7)

    d = 0.8
    n = int(SR * d)
    save("snd_death", mix(sweep(700, 70, d, "square") * env(n, 0.002, 1.2) * 0.5, lowpass(noise(d), 700) * env(n, 0.001, 3)), 0.7)

    save("snd_kill", seq([note(784, 0.1, "square", 1.0), note(1175, 0.32, "square", 1.6)]), 0.5)

    fanfare = seq([note(523, 0.14, "square", 0.6), note(659, 0.14, "square", 0.6), note(784, 0.14, "square", 0.6),
                   note(1047, 0.5, "square", 1.2), note(784, 0.12, "square", 0.6), note(1047, 0.7, "square", 1.5)])
    bass = seq([note(131, 0.42, "tri", 0.8), note(196, 0.62, "tri", 0.8), note(262, 0.8, "tri", 1.2)])
    save("snd_victory", mix(fanfare, bass * 0.7), 0.6)

    save("snd_defeat", seq([note(392, 0.25, "tri", 0.8), note(330, 0.25, "tri", 0.8), note(262, 0.25, "tri", 0.8),
                            note(196, 0.7, "tri", 1.5)]), 0.6)

    d = 0.035
    save("snd_click", sweep(1500, 900, d, "sine") * env(int(SR * d), 0.001, 2), 0.5)

    d = 0.35
    n = int(SR * d)
    cracks = np.zeros(n)
    for start in (0.0, 0.05, 0.12):
        s = int(SR * start)
        m = int(SR * 0.12)
        cracks[s:s + m] += lowpass(noise(0.12), 1800)[: n - s] * env(m, 0.001, 3)[: n - s]
    save("snd_box", mix(cracks, sweep(180, 60, d) * env(n, 0.001, 3) * 0.6), 0.7)


if __name__ == "__main__":
    main()
