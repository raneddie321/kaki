import { gameUser, json } from "@/lib/game-auth";

const MAX_SAVE = 6000; // Clerk metadata is limited to 8 KB
const MAX_FIZZ_SAVE = 1500; // shares the 8 KB with the Snake Brawl save

/** Other apps (?app=fizzapp) keep their own save next to Snake Brawl's. */
const isFizz = (req: Request) => new URL(req.url).searchParams.get("app") === "fizzapp";

/** The player's Snake Brawl cloud save. */
export async function GET(req: Request) {
  const g = await gameUser(req);
  if (!g) return json({ error: "unauthorized" }, 401);
  if (isFizz(req))
    return json({ username: g.user.username ?? "", save: g.meta.fizzappSave ?? null, updatedAt: g.meta.fizzappSaveAt ?? 0 });
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
  const body = (await req.json().catch(() => null)) as { save?: string; baseAt?: number; summary?: Summary } | null;
  if (isFizz(req)) {
    if (typeof body?.save !== "string" || body.save.length === 0 || body.save.length > MAX_FIZZ_SAVE)
      return json({ error: "bad_save" }, 400);
    const at = g.meta.fizzappSaveAt ?? 0;
    if (typeof body.baseAt === "number" && at > body.baseAt && g.meta.fizzappSave)
      return json({ error: "conflict", save: g.meta.fizzappSave, updatedAt: at }, 409);
    const updatedAt = Math.max(Date.now(), at + 1);
    await g.client.users.updateUserMetadata(g.user.id, { privateMetadata: { fizzappSave: body.save, fizzappSaveAt: updatedAt } });
    return json({ updatedAt });
  }
  if (typeof body?.save !== "string" || body.save.length === 0 || body.save.length > MAX_SAVE)
    return json({ error: "bad_save" }, 400);
  // The game says which cloud save its progress builds on. If another device saved since then,
  // refuse and hand back the newer save so the game can merge both instead of overwriting.
  // (Games before 3.16 send no baseAt and keep the old behaviour.)
  const cloudAt = g.meta.snakeBrawlSaveAt ?? 0;
  if (typeof body.baseAt === "number" && cloudAt > body.baseAt && g.meta.snakeBrawlSave)
    return json({ error: "conflict", save: g.meta.snakeBrawlSave, updatedAt: cloudAt }, 409);
  const s = body.summary ?? {};
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? Math.max(0, Math.floor(v)) : 0);
  const updatedAt = Math.max(Date.now(), cloudAt + 1);
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
