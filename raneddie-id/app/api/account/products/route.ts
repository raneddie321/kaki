import { auth, clerkClient } from "@clerk/nextjs/server";
import { json, type PrivateMeta } from "@/lib/game-auth";

type AppId = "snakebrawl" | "fizzapp";

function isAppId(value: unknown): value is AppId {
  return value === "snakebrawl" || value === "fizzapp";
}

export async function GET() {
  const { userId } = await auth();
  if (!userId) return json({ error: "unauthorized" }, 401);

  const client = await clerkClient();
  const user = await client.users.getUser(userId).catch(() => null);
  if (!user) return json({ error: "user_not_found" }, 404);

  const meta = (user.privateMetadata ?? {}) as PrivateMeta;
  const connectedProducts = meta.connectedProducts ?? {};
  return json({
    products: {
      snakebrawl: connectedProducts.snakebrawl?.connectedAt ?? null,
      fizzapp: connectedProducts.fizzapp?.connectedAt ?? null,
    },
  });
}

export async function DELETE(req: Request) {
  const { userId } = await auth();
  if (!userId) return json({ error: "unauthorized" }, 401);

  const body = (await req.json().catch(() => null)) as { app?: unknown } | null;
  if (!isAppId(body?.app)) return json({ error: "bad_app" }, 400);

  const client = await clerkClient();
  const user = await client.users.getUser(userId).catch(() => null);
  if (!user) return json({ error: "user_not_found" }, 404);

  const meta = (user.privateMetadata ?? {}) as PrivateMeta;
  const current = meta.connectedProducts?.[body.app];
  const version = (current?.version ?? 0) + 1;

  await client.users.updateUserMetadata(userId, {
    privateMetadata: {
      connectedProducts: {
        [body.app]: { version, connectedAt: null },
      },
    },
  });

  return json({ success: true, app: body.app });
}
