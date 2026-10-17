import { clerkClient } from "@clerk/nextjs/server";
import { isEmail } from "@/lib/auth-helpers";

/** Sign-up step 1: tells the form right away if an email already has a RanEddie ID. */
export async function POST(req: Request) {
  const body = (await req.json().catch(() => null)) as { email?: unknown } | null;
  const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
  if (!isEmail(email) || email.length > 254) return Response.json({ error: "bad_email" }, { status: 400 });
  const client = await clerkClient();
  const { totalCount } = await client.users.getUserList({ emailAddress: [email], limit: 1 });
  return Response.json({ taken: totalCount > 0 }, { headers: { "Cache-Control": "no-store" } });
}
