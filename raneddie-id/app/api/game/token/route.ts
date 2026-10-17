import { clerkClient } from "@clerk/nextjs/server";
import { challengeOf, PKCE_RE, sign, verify, type GameToken, type LinkCode } from "@/lib/game-token";
import { json, type PrivateMeta } from "@/lib/game-auth";

/** The game trades the link code and its secret verifier for a long-lived game token. */
export async function POST(req: Request) {
  const body = (await req.json().catch(() => null)) as { code?: string; verifier?: string } | null;
  const code = verify<LinkCode>(body?.code);
  if (!code || code.t !== "code" || code.exp < Date.now()) return json({ error: "bad_code" }, 400);
  if (!body?.verifier || !PKCE_RE.test(body.verifier) || challengeOf(body.verifier) !== code.ch)
    return json({ error: "bad_verifier" }, 400);
  const client = await clerkClient();
  const user = await client.users.getUser(code.sub).catch(() => null);
  if (!user || user.banned || user.locked) return json({ error: "no_user" }, 403);
  const v = ((user.privateMetadata ?? {}) as PrivateMeta).gameTokenVersion ?? 0;
  const token: GameToken = { t: "game", sub: user.id, v, iat: Date.now() };
  return json({ token: sign(token), username: user.username ?? "" });
}
