import "server-only";
import { createHash, createHmac, timingSafeEqual } from "node:crypto";

/**
 * Signed tokens for the games (no database needed). Two kinds:
 * - a short-lived link code, handed to the game through the snakebrawl:// link after the player
 *   approves on /connect. It only works together with the secret "verifier" the game kept (PKCE),
 *   so another app that grabs the link can't use it;
 * - the game token, which the game keeps and sends with every cloud-save request. It stays valid
 *   until the player's gameTokenVersion (private metadata) is raised, which signs out every device.
 */

function secret(): string {
  const s = process.env.RANEDDIE_GAME_SECRET;
  if (!s || s.length < 32) throw new Error("RANEDDIE_GAME_SECRET must be set (at least 32 characters)");
  return s;
}

const b64 = (b: Buffer | string) => Buffer.from(b).toString("base64url");

function mac(body: string) {
  return createHmac("sha256", secret()).update(body).digest("base64url");
}

export function sign(payload: object): string {
  const body = b64(JSON.stringify(payload));
  return `${body}.${mac(body)}`;
}

export function verify<T>(token: unknown): T | null {
  if (typeof token !== "string" || token.length > 2000) return null;
  const [body, sig] = token.split(".");
  if (!body || !sig) return null;
  const want = Buffer.from(mac(body));
  const got = Buffer.from(sig);
  if (want.length !== got.length || !timingSafeEqual(want, got)) return null;
  try {
    return JSON.parse(Buffer.from(body, "base64url").toString("utf8")) as T;
  } catch {
    return null;
  }
}

/** PKCE: base64url(SHA-256(verifier)). */
export function challengeOf(verifier: string) {
  return createHash("sha256").update(verifier).digest("base64url");
}

export type LinkCode = { t: "code"; sub: string; ch: string; exp: number };
export type GameToken = { t: "game"; sub: string; v: number; iat: number };

export const PKCE_RE = /^[A-Za-z0-9_-]{43,128}$/;
