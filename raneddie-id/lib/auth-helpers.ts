/** Helpers shared by the RanEddie ID forms. */

/** Players must be at least this old to create a RanEddie ID (COPPA: no accounts for under-13s). */
export const MIN_AGE = 13;

type ClerkLikeError = {
  code?: string;
  message?: string;
  longMessage?: string;
  errors?: { code?: string; message?: string; longMessage?: string; meta?: { paramName?: string } }[];
};

/** The first error Clerk returned, in a shape that's easy to show. */
export function clerkError(error: unknown): { code: string; message: string; param?: string } {
  const e = (error ?? {}) as ClerkLikeError;
  const first = e.errors?.[0];
  const code = first?.code || e.code || "unknown";
  const message =
    FRIENDLY[code] || first?.longMessage || first?.message || e.longMessage || e.message || "Something went wrong. Please try again.";
  return { code, message, param: first?.meta?.paramName };
}

/** Clearer wording for the errors players hit most often. */
const FRIENDLY: Record<string, string> = {
  form_identifier_not_found: "We couldn't find a RanEddie ID with that email.",
  form_password_incorrect: "That password isn't right. Try again or reset it.",
  form_identifier_exists: "That email already has a RanEddie ID. Sign in instead.",
  form_username_exists: "That username is taken. Try another one.",
  form_password_pwned:
    "This password has appeared in a data leak on another site. Please choose a different one.",
  form_code_incorrect: "That code isn't right. Check the email and try again.",
  verification_expired: "That code has expired. Send a new one.",
  too_many_requests: "Too many attempts. Please wait a minute and try again.",
};

export function isEmail(s: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s.trim());
}

/** Password rules shown while typing (Clerk enforces its own on top). */
export function passwordChecks(pw: string) {
  return [
    { ok: pw.length >= 8, label: "At least 8 characters" },
    { ok: /[a-z]/.test(pw) && /[A-Z]/.test(pw), label: "Upper & lowercase letters" },
    { ok: /\d/.test(pw), label: "A number" },
  ];
}

export function passwordStrength(pw: string): { score: number; label: string } {
  if (!pw) return { score: 0, label: "" };
  let s = 0;
  if (pw.length >= 8) s++;
  if (pw.length >= 12) s++;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) s++;
  if (/\d/.test(pw)) s++;
  if (/[^A-Za-z0-9]/.test(pw)) s++;
  const score = Math.min(4, Math.max(1, s - (pw.length < 8 ? 1 : 0)));
  return { score, label: ["", "Weak", "Fair", "Good", "Strong"][score] };
}

/** Usernames: 4–20 letters, numbers, _ or -, starting with a letter. */
export function usernameError(u: string): string | null {
  if (u.length < 4) return "Use at least 4 characters.";
  if (u.length > 20) return "Use 20 characters or fewer.";
  if (!/^[A-Za-z]/.test(u)) return "Start with a letter.";
  if (!/^[A-Za-z0-9_-]+$/.test(u)) return "Use only letters, numbers, _ and -.";
  return null;
}

export function ageOn(birth: Date, today = new Date()) {
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--;
  return age;
}

export const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

export function daysInMonth(year: number, month: number) {
  return new Date(year || 2000, month + 1, 0).getDate();
}

// ISO 3166-1 alpha-2 codes; names come from the browser (Intl.DisplayNames).
const CODES =
  "AF AX AL DZ AS AD AO AI AQ AG AR AM AW AU AT AZ BS BH BD BB BY BE BZ BJ BM BT BO BQ BA BW BV BR IO BN BG BF BI CV KH CM CA KY CF TD CL CN CX CC CO KM CG CD CK CR CI HR CU CW CY CZ DK DJ DM DO EC EG SV GQ ER EE SZ ET FK FO FJ FI FR GF PF TF GA GM GE DE GH GI GR GL GD GP GU GT GG GN GW GY HT HM VA HN HK HU IS IN ID IR IQ IE IM IL IT JM JP JE JO KZ KE KI KP KR KW KG LA LV LB LS LR LY LI LT LU MO MG MW MY MV ML MT MH MQ MR MU YT MX FM MD MC MN ME MS MA MZ MM NA NR NP NL NC NZ NI NE NG NU NF MK MP NO OM PK PW PS PA PG PY PE PH PN PL PT PR QA RE RO RU RW BL SH KN LC MF PM VC WS SM ST SA SN RS SC SL SG SX SK SI SB SO ZA GS SS ES LK SD SR SJ SE CH SY TW TJ TZ TH TL TG TK TO TT TN TR TM TC TV UG UA AE GB US UM UY UZ VU VE VN VG VI WF EH YE ZM ZW".split(
    " ",
  );

function flag(code: string) {
  return String.fromCodePoint(...[...code].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}

export function countries(): { code: string; name: string; flag: string }[] {
  const names = new Intl.DisplayNames(["en"], { type: "region" });
  return CODES.map((code) => ({ code, name: names.of(code) ?? code, flag: flag(code) })).sort((a, b) =>
    a.name.localeCompare(b.name),
  );
}

/** Best guess at the player's country from the browser locale, for the default selection. */
export function guessCountry(): string {
  if (typeof navigator === "undefined") return "";
  for (const l of navigator.languages ?? [navigator.language]) {
    const region = l.split("-")[1];
    if (region && region.length === 2 && CODES.includes(region.toUpperCase())) return region.toUpperCase();
  }
  return "";
}

/** Sends the signed-in player to `to`, through Clerk's decorateUrl (needed for Safari cookie refresh). */
export function navigateAfterAuth(
  router: { push: (url: string) => void },
  to = "/",
): (args: { session?: { currentTask?: unknown } | null; decorateUrl: (url: string) => string }) => void {
  return ({ session, decorateUrl }) => {
    if (session?.currentTask) {
      // Session tasks (e.g. choose an organization) are not used by RanEddie ID yet
      console.warn("Pending session task", session.currentTask);
    }
    const url = decorateUrl(to);
    if (url.startsWith("http")) window.location.href = url;
    else router.push(url);
  };
}
