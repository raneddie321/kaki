import { gameUser, json } from "@/lib/game-auth";

/**
 * Who a game token belongs to. Desktop games call this at start-up: 200 = still connected (show the username),
 * 401 = signed out or disconnected in the Account Center (forget the token, offer "Sign in with RanEddie ID" again).
 */
export async function GET(req: Request) {
  const g = await gameUser(req);
  if (!g) return json({ error: "unauthorized" }, 401);
  return json({ id: g.user.id, username: g.user.username ?? "", imageUrl: g.user.hasImage ? g.user.imageUrl : null });
}
