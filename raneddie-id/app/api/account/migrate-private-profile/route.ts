import { auth, clerkClient } from "@clerk/nextjs/server";
import { json } from "@/lib/game-auth";

type ProfileMetadata = { birthDate?: string; country?: string };

export async function POST() {
  const { userId } = await auth();
  if (!userId) return json({ error: "unauthorized" }, 401);

  const client = await clerkClient();
  const user = await client.users.getUser(userId).catch(() => null);
  if (!user) return json({ error: "user_not_found" }, 404);

  const unsafe = (user.unsafeMetadata ?? {}) as ProfileMetadata;
  const privateMeta = (user.privateMetadata ?? {}) as ProfileMetadata;
  const privateUpdate: ProfileMetadata = {};

  if (typeof unsafe.birthDate === "string" && !privateMeta.birthDate) privateUpdate.birthDate = unsafe.birthDate;
  if (typeof unsafe.country === "string" && !privateMeta.country) privateUpdate.country = unsafe.country;

  const removeUnsafe: Record<string, null> = {};
  if (typeof unsafe.birthDate === "string") removeUnsafe.birthDate = null;
  if (typeof unsafe.country === "string") removeUnsafe.country = null;

  if (Object.keys(privateUpdate).length || Object.keys(removeUnsafe).length) {
    await client.users.updateUserMetadata(userId, {
      ...(Object.keys(privateUpdate).length ? { privateMetadata: privateUpdate } : {}),
      ...(Object.keys(removeUnsafe).length ? { unsafeMetadata: removeUnsafe } : {}),
    });
  }

  return json({ success: true, migrated: Object.keys(privateUpdate) });
}
