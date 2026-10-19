import { auth } from "@clerk/nextjs/server";
import { PKCE_RE, sign, type LinkCode } from "@/lib/game-token";
import { json } from "@/lib/game-auth";

/** The signed-in player approved linking a game: returns a 10-minute link code bound to the game's challenge. */
export async function POST(req: Request) {
  const { userId } = await auth();
  if (!userId) return json({ error: "signed_out" }, 401);
  const body = (await req.json().catch(() => null)) as { challenge?: string } | null;
  if (!body?.challenge || !PKCE_RE.test(body.challenge)) return json({ error: "bad_challenge" }, 400);
  const code: LinkCode = { t: "code", sub: userId, ch: body.challenge, exp: Date.now() + 10 * 60_000 };
  return json({ code: sign(code) });
}
