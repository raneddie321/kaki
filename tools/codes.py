#!/usr/bin/env python3
"""Makes Snake Brawl redeem codes (the game checks them with the same key, see Codes.java).

  python3 tools/codes.py coins 500 5          # 5 codes worth 500 coins, each usable once per phone
  python3 tools/codes.py trophies 100 1 ABC234 # 1 code for player ID ABC234 only
Types: coins, trophies, box, megabox, skin <id>, brawler <id>, passxp <amount>, passplus
"""
import os, re, secrets, sys

M = (1 << 64) - 1
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
TYPES = {"coins": 0, "trophies": 1, "box": 2, "megabox": 3, "skin": 4, "brawler": 5, "passxp": 6, "passplus": 7}


def key():
    src = open(os.path.join(os.path.dirname(__file__), "..", "app/src/main/java/com/snakebrawl/myapp/game/Codes.java")).read()
    return re.search(r'KEY = "([^"]+)"', src).group(1)


def mix(z):
    z &= M
    z = ((z ^ (z >> 30)) * 0xbf58476d1ce4e5b9) & M
    z = ((z ^ (z >> 27)) * 0x94d049bb133111eb) & M
    return z ^ (z >> 31)


def key_hash(salt, k):
    h = salt & M
    for i, ch in enumerate(k):
        h = mix(h ^ ((ord(ch) * 0x100000001b3 + i) & M))
    return h


def make(t, amount, player, serial, k):
    p = ((t & 15) << 60) | ((amount & 0xfff) << 48) | ((player & 0x3fffffff) << 18) | (serial & 0x3ffff)
    serial &= 0x3ffff
    upper = (p >> 18) ^ (mix(key_hash(0xface, k) ^ serial) & ((1 << 46) - 1))
    hi = (upper << 18) | ((serial ^ key_hash(0xc0de, k)) & 0x3ffff)
    mac = mix(key_hash(0x5eed, k) ^ hi) & 0xfffffffff
    n = (hi << 36) | mac
    chars = "".join(ALPHABET[(n >> (95 - 5 * i)) & 31] for i in range(20))
    return "-".join(chars[i:i + 5] for i in range(0, 20, 5))


def player_id(text):
    v = 0
    for c in text.upper().replace("0", "O").replace("1", "I"):
        v = v << 5 | ALPHABET.index(c)
    return v


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] not in TYPES:
        sys.exit(__doc__)
    t = TYPES[a[0]]
    amount = int(a[1]) if len(a) > 1 else 1
    count = int(a[2]) if len(a) > 2 else 1
    player = player_id(a[3]) if len(a) > 3 else 0
    k = key()
    for _ in range(count):
        print(make(t, amount, player, secrets.randbelow(1 << 18), k))
