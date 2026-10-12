import { gameUser, json } from "@/lib/game-auth";

const MAX_SAVE = 6000; // Clerk metadata is limited to 8 KB

/** The player's Snake Brawl cloud save. */
export async function GET(req: Request) {
  const g = await gameUser(req);
  if (!g) return json({ error: "unauthorized" }, 401);
  return json({
    username: g.user.username ?? "",
    save: g.meta.snakeBrawlSave ?? null,
    updatedAt: g.meta.snakeBrawlSaveAt ?? 0,
  });
}

type Summary = { nickname?: string; trophies?: number; bestTrophies?: number; coins?: number; wins?: number; games?: number; snakes?: number };

/** Stores the save (private) and a readable summary (public metadata, shown in the Clerk dashboard). */
export async function PUT(req: Request) {
  const g = await gameUser(req);
  if (!g) return json({ error: "unauthorized" }, 401);
  const body = (await req.json().catch(() => null)) as { save?: string; summary?: Summary } | null;
  if (typeof body?.save !== "string" || body.save.length === 0 || body.save.length > MAX_SAVE)
    return json({ error: "bad_save" }, 400);
  const s = body.summary ?? {};
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? Math.max(0, Math.floor(v)) : 0);
  const updatedAt = Date.now();
  await g.client.users.updateUserMetadata(g.user.id, {
    privateMetadata: { snakeBrawlSave: body.save, snakeBrawlSaveAt: updatedAt },
    publicMetadata: {
      snakeBrawl: {
        nickname: typeof s.nickname === "string" ? s.nickname.slice(0, 24) : "",
        trophies: num(s.trophies),
        bestTrophies: num(s.bestTrophies),
        coins: num(s.coins),
        wins: num(s.wins),
        games: num(s.games),
        snakes: num(s.snakes),
        updatedAt: new Date(updatedAt).toISOString(),
      },
    },
  });
  return json({ updatedAt });
}
