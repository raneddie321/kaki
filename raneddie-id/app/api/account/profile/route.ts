import { auth, clerkClient } from "@clerk/nextjs/server";
import { json } from "@/lib/game-auth";
import { validateProfile } from "@/lib/profile";

type ProfileMetadata = { birthDate?: string; country?: string };

/**
 * Stores the date of birth and country collected at sign-up in the player's private metadata
 * (never public, never readable from the browser). The first values saved are kept, so the birth
 * date can't be edited afterwards to get around the age rule.
 */
export async function POST(req: Request) {
  const { userId } = await auth();
  if (!userId) return json({ error: "unauthorized" }, 401);

  const body = await req.json().catch(() => null);
  const check = validateProfile(body);
  if (!check.ok) return json({ error: check.error }, 400);

  const client = await clerkClient();
  const user = await client.users.getUser(userId).catch(() => null);
  if (!user) return json({ error: "user_not_found" }, 404);

  const existing = (user.privateMetadata ?? {}) as ProfileMetadata;
  const update: ProfileMetadata = {};
  if (!existing.birthDate) update.birthDate = check.value.birthDate;
  if (!existing.country) update.country = check.value.country;

  if (Object.keys(update).length) {
    await client.users.updateUserMetadata(userId, { privateMetadata: update });
  }
  return json({ success: true });
}
