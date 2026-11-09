import { MIN_AGE, ageOn } from "./auth-helpers.ts";

/** The details collected on the sign-up "About you" step. Stored in the player's private Clerk metadata. */
export type ProfileDetails = { birthDate: string; country: string };

export type ProfileCheck = { ok: true; value: ProfileDetails } | { ok: false; error: "invalid" | "underage" };

/** "YYYY-MM-DD" from the month (0–11), day and year chosen in the sign-up form. */
export function toBirthDate(year: number, monthIndex: number, day: number): string {
  const pad = (n: number, len = 2) => String(n).padStart(len, "0");
  return `${pad(year, 4)}-${pad(monthIndex + 1)}-${pad(day)}`;
}

/** Validates profile details received from the browser; the server never trusts the sign-up form's own checks. */
export function validateProfile(input: unknown, now: Date = new Date()): ProfileCheck {
  if (typeof input !== "object" || input === null) return { ok: false, error: "invalid" };
  const { birthDate, country } = input as Record<string, unknown>;
  if (typeof birthDate !== "string" || typeof country !== "string") return { ok: false, error: "invalid" };

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate);
  if (!match || !/^[A-Z]{2}$/.test(country)) return { ok: false, error: "invalid" };

  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const birth = new Date(year, month - 1, day);
  const isRealDate = birth.getFullYear() === year && birth.getMonth() === month - 1 && birth.getDate() === day;
  if (!isRealDate || year < 1900 || birth > now) return { ok: false, error: "invalid" };
  if (ageOn(birth, now) < MIN_AGE) return { ok: false, error: "underage" };

  return { ok: true, value: { birthDate, country } };
}
