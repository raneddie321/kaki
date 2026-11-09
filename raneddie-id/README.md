# RanEddie ID

The central account system for RanEddie products. Sign-up, sign-in, email verification, password
reset and the Account Center run on this Next.js app, backed by [Clerk](https://clerk.com).
It is served at **https://accounts.raneddie.com** and linked from the main website
([`raneddie321/raneddie`](https://github.com/raneddie321/raneddie)).

## Pages

| Path | What it does |
| --- | --- |
| `/` | Landing page; shows a short account summary when signed in |
| `/sign-in` | Email → password → (new device: emailed code, or authenticator / backup code) |
| `/sign-up` | Email → password → date of birth & country → security check → username → emailed code |
| `/forgot-password` | Email → emailed code → new password |
| `/account` | Account Center: profile, email, password, two-step verification, sessions, connected products |
| `/connect` | Opened from a game: approves linking Snake Brawl / Fizz App to this account (PKCE) |
| `/privacy`, `/terms` | Legal pages |

Every auth page accepts `?redirect_url=`. Only local paths and the trusted RanEddie origins listed in
[`lib/auth-helpers.ts`](lib/auth-helpers.ts) (`TRUSTED_REDIRECT_ORIGINS`) are honoured.

## API routes

| Route | Auth | Purpose |
| --- | --- | --- |
| `POST /api/email-taken` | none (same-site only, rate limited) | Sign-up step 1: is this email already registered? |
| `POST /api/account/profile` | Clerk session | Saves date of birth + country (private metadata, first write wins) |
| `POST /api/account/migrate-private-profile` | Clerk session | Moves legacy birth date / country out of public-readable metadata |
| `GET/DELETE /api/account/products` | Clerk session | Lists / disconnects linked games |
| `POST /api/game/code`, `POST /api/game/token` | Clerk session / PKCE code | Game linking handshake |
| `GET/PUT /api/game/save` | game token | Cloud saves (`?app=fizzapp` for Fizz) |

## Local development

```bash
cp .env.example .env.local   # fill in the three values, see below
npm ci
npm run dev
```

| Command | What it does |
| --- | --- |
| `npm run lint` | ESLint |
| `npm run typecheck` | Generates Next route types, then `tsc --noEmit` |
| `npm test` | Unit tests (Node's built-in runner) for redirect safety, password rules and profile validation |
| `npm run build` | Production build |

Run all four before opening a pull request.

## Environment variables

| Variable | Where | Notes |
| --- | --- | --- |
| `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` | Vercel + local | `pk_live_…` for Production, `pk_test_…` for local / previews |
| `CLERK_SECRET_KEY` | Vercel + local | Server only. Never commit it |
| `RANEDDIE_GAME_SECRET` | Vercel + local | ≥ 32 random characters. Signs game link codes and tokens; changing it signs every game out |

> Vercel currently has these set for the **Production** target only. Preview deployments (any branch
> other than the production branch) build without Clerk keys, so the auth pages cannot work there.
> Add **Preview** values using the Clerk *Development* instance keys if you want working previews.

## Clerk configuration this app depends on

The code assumes these settings in **both** the Development and Production instances
(Clerk dashboard → Configure):

- **Email address**: required, verified at sign-up with an email code.
- **Password**: enabled and required. The forms check `MIN_PASSWORD_LENGTH` in
  [`lib/auth-helpers.ts`](lib/auth-helpers.ts) (**15**), which must match the dashboard's minimum.
- **Username**: enabled and **required at sign-up** (sign-up step 5 sends it; games display it).
- **Client Trust / new-device verification**: supported (emailed code on a new device).
- **Bot protection** (Attack protection): recommended. Sign-up already renders Clerk's
  `#clerk-captcha` mount point, so enabling Smart CAPTCHA works without code changes.
- **Domains** (Production): `clerk.raneddie.com` (Frontend API) must verify, plus the
  `clkmail` / `clk._domainkey` / `clk2._domainkey` CNAMEs so verification and reset emails are delivered.

## Deployment

- Vercel project **`kaki`** (this app lives in the `raneddie-id/` folder of the `kaki` repository).
- Domains: `accounts.raneddie.com`, `id.raneddie.com`.
- The repository also contains the Snake Brawl game; only `raneddie-id/` is part of this app.
- Clerk production sessions only work on `*.raneddie.com`; `*.vercel.app` URLs show a
  "We couldn't load RanEddie ID" message instead of a spinner.

## Known limitations

- The date-of-birth age check (13+) runs in the browser **and** when the profile is saved, but the
  account itself is created by Clerk before that. Enforcing it at account creation would need a
  Clerk webhook or similar.
- `/api/email-taken` uses an in-memory limiter per server instance; add a Vercel Firewall
  rate-limit rule for a hard global cap.
