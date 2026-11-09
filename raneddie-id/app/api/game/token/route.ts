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
  const meta = (user.privateMetadata ?? {}) as PrivateMeta;
  const app = code.app;
  let token: GameToken;
  if (app === "snakebrawl" || app === "fizzapp") {
    const version = (meta.connectedProducts?.[app]?.version ?? 0) + 1;
    const connectedAt = Date.now();
    await client.users.updateUserMetadata(user.id, {
      privateMetadata: { connectedProducts: { [app]: { version, connectedAt } } },
    });
    token = { t: "game", sub: user.id, v: version, app, iat: connectedAt };
  } else {
    // Backward compatibility for short-lived link codes issued before product-bound tokens.
    const version = meta.gameTokenVersion ?? 0;
    token = { t: "game", sub: user.id, v: version, iat: Date.now() };
  }
  return json({ token: sign(token), username: user.username ?? "" });
}
