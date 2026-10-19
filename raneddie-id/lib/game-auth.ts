import "server-only";
import { clerkClient } from "@clerk/nextjs/server";
import { verify, type GameToken } from "./game-token";

export type PrivateMeta = { gameTokenVersion?: number; snakeBrawlSave?: string; snakeBrawlSaveAt?: number;
  fizzappSave?: string; fizzappSaveAt?: number };

/** The player behind a game request (Authorization: Bearer <game token>), or null. */
export async function gameUser(req: Request) {
  const auth = req.headers.get("authorization") ?? "";
  const tok = verify<GameToken>(auth.startsWith("Bearer ") ? auth.slice(7) : "");
  if (!tok || tok.t !== "game") return null;
  const client = await clerkClient();
  try {
    const user = await client.users.getUser(tok.sub);
    const meta = (user.privateMetadata ?? {}) as PrivateMeta;
    if ((meta.gameTokenVersion ?? 0) !== tok.v || user.banned || user.locked) return null;
    return { client, user, meta };
  } catch {
    return null;
  }
}

export function json(data: unknown, status = 200) {
  return Response.json(data, { status, headers: { "Cache-Control": "no-store" } });
}
