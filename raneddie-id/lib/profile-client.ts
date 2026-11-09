import type { ProfileDetails } from "./profile.ts";

const PENDING_KEY = "raneddie-id.pending-profile";

async function post(details: ProfileDetails): Promise<"saved" | "rejected" | "failed"> {
  try {
    const res = await fetch("/api/account/profile", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(details),
    });
    if (res.ok) return "saved";
    // 400 = the server refused these details for good; retrying later won't help
    return res.status === 400 ? "rejected" : "failed";
  } catch {
    return "failed";
  }
}

/**
 * Saves the birth date and country from sign-up to the new account. If that can't happen right now
 * (offline, Clerk session still settling), the details are kept in this browser and sent the next
 * time the player opens RanEddie ID, see {@link flushPendingProfile}.
 */
export async function saveProfileDetails(details: ProfileDetails): Promise<void> {
  const result = await post(details);
  if (result === "failed") {
    try {
      window.localStorage.setItem(PENDING_KEY, JSON.stringify(details));
    } catch {
      // Storage is blocked: nothing more we can do
    }
  }
}

/** Sends profile details that could not be saved at sign-up. Safe to call on every page load. */
export async function flushPendingProfile(): Promise<void> {
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(PENDING_KEY);
  } catch {
    return;
  }
  if (!raw) return;
  let details: ProfileDetails;
  try {
    details = JSON.parse(raw) as ProfileDetails;
  } catch {
    window.localStorage.removeItem(PENDING_KEY);
    return;
  }
  const result = await post(details);
  if (result !== "failed") window.localStorage.removeItem(PENDING_KEY);
}
