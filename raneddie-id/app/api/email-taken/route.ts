import { clerkClient } from "@clerk/nextjs/server";
import { isEmail } from "@/lib/auth-helpers";
import { clientIp, isCrossSite, rateLimit } from "@/lib/rate-limit";

const NO_STORE = { "Cache-Control": "no-store" };

/**
 * Sign-up step 1: tells the form right away if an email already has a RanEddie ID.
 *
 * This reveals whether an address is registered, so it only answers requests from this site and
 * limits how fast one visitor can ask (it also protects the Clerk API quota from being used up).
 */
export async function POST(req: Request) {
  if (isCrossSite(req)) return Response.json({ error: "forbidden" }, { status: 403, headers: NO_STORE });

  const limit = rateLimit(`email-taken:${clientIp(req)}`, 10, 60_000);
  if (!limit.ok) {
    return Response.json(
      { error: "too_many_requests" },
      { status: 429, headers: { ...NO_STORE, "Retry-After": String(limit.retryAfter) } },
    );
  }

  const body = (await req.json().catch(() => null)) as { email?: unknown } | null;
  const email = typeof body?.email === "string" ? body.email.trim().toLowerCase() : "";
  if (!isEmail(email) || email.length > 254) return Response.json({ error: "bad_email" }, { status: 400, headers: NO_STORE });
  const client = await clerkClient();
  const { totalCount } = await client.users.getUserList({ emailAddress: [email], limit: 1 });
  return Response.json({ taken: totalCount > 0 }, { headers: NO_STORE });
}
