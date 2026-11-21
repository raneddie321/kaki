# Connecting a product to RanEddie ID

RanEddie ID is the single account for every RanEddie product. It runs on the **Clerk production
instance "RanEddie ID"** (Frontend API `clerk.raneddie.com`), and its pages live at
**https://id.raneddie.com**. There is one user database: a user who signs up anywhere can sign in
everywhere.

Pick the path that matches the product:

| Product type | Path | Effort |
|---|---|---|
| Website on a `*.raneddie.com` subdomain (recommended) | A. Same Clerk keys, shared session | ~10 minutes |
| Website on another domain (e.g. `*.vercel.app`) | B. Move it to a subdomain, or a Clerk satellite domain | 10 min / paid plan |
| Mobile or desktop app, game | C. The `/connect` flow (already used by Snake Brawl) | ~1 hour |

---

## A. Website on a raneddie.com subdomain (the professional default)

Because every product shares the same Clerk production instance and the same root domain, a user
who signs in once is **already signed in** on every `*.raneddie.com` site. No extra servers, no
token exchange.

1. **Domain:** in Vercel, add the product's domain, for example `fizz.raneddie.com`.
2. **Install:** `npm install @clerk/nextjs`
3. **Environment variables** (Vercel → the product's project → Settings → Environment Variables,
   Production). Copy them from the `kaki` project, never paste them into code:
   - `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`: the `pk_live_…` key (the same one for every product)
   - `CLERK_SECRET_KEY`: the `sk_live_…` key (mark it Sensitive)
   - `NEXT_PUBLIC_CLERK_SIGN_IN_URL=https://id.raneddie.com/sign-in`
   - `NEXT_PUBLIC_CLERK_SIGN_UP_URL=https://id.raneddie.com/sign-up`
4. **Provider** (`app/layout.tsx`):

   ```tsx
   import { ClerkProvider } from "@clerk/nextjs";

   export default function RootLayout({ children }: { children: React.ReactNode }) {
     return (
       <html lang="en">
         <body>
           <ClerkProvider>{children}</ClerkProvider>
         </body>
       </html>
     );
   }
   ```

5. **Middleware** (`middleware.ts`, or `proxy.ts` on Next 16):

   ```ts
   import { clerkMiddleware } from "@clerk/nextjs/server";
   export default clerkMiddleware();
   export const config = { matcher: ["/((?!_next|.*\\..*).*)", "/(api|trpc)(.*)"] };
   ```

6. **Sign-in button:** send people to RanEddie ID and back:

   ```tsx
   const back = encodeURIComponent("https://fizz.raneddie.com" + pathname);
   <a href={`https://id.raneddie.com/sign-in?redirect_url=${back}`}>Sign in with RanEddie ID</a>
   ```

   RanEddie ID accepts any `https://*.raneddie.com` address as `redirect_url`, so new products
   need no change here.

7. **Who is signed in:**
   - Client: `const { user, isSignedIn } = useUser();`
   - Server: `const { userId } = await auth();`, then `(await clerkClient()).users.getUser(userId)`
   - Account management: link to `https://id.raneddie.com/account`
   - Sign out: `useClerk().signOut()` signs the user out of every RanEddie site at once

8. **Per-product data:** store it in your own database keyed by `userId` (`user_…`). Never
   create a second user table with emails or passwords.

`raneddie.com` itself already works this way and is a working example.

## B. Website on another domain

Clerk shares a session only across one root domain. For a product on `something.vercel.app` or a
different domain:

- **Recommended:** give it a `*.raneddie.com` subdomain and follow path A. It's free and the
  simplest option.
- **Alternative:** Clerk **satellite domains** (Clerk dashboard → Domains → Satellites, which
  requires a paid Clerk plan). The satellite app uses the same keys plus `isSatellite` and
  `domain` settings, and `signInUrl=https://id.raneddie.com/sign-in`. Also add the origin to
  `TRUSTED_REDIRECT_ORIGINS` in `lib/auth-helpers.ts`.

## C. Mobile apps, desktop apps and games (`/connect`)

Apps can't share browser cookies, so they use a short PKCE flow. Snake Brawl already uses it.

1. The app creates a random `verifier` (43 to 128 chars) and `challenge = base64url(sha256(verifier))`,
   plus a random `state`.
2. The app opens the browser at
   `https://id.raneddie.com/connect?game=<app-id>&state=<state>&ch=<challenge>`.
3. The user signs in (or signs up) and taps Approve. RanEddie ID returns the user to
   `<app-scheme>://raneddie?state=<state>&code=<code>`.
4. The app checks `state`, then calls
   `POST https://id.raneddie.com/api/game/token` with `{ "code": "...", "verifier": "..." }` and gets
   `{ token, username }`.
5. The app keeps the token in secure storage and sends it as `Authorization: Bearer <token>` to
   `/api/game/save` (cloud save) or to its own server.

**Adding a new app** (code change in this repo):

- `app/connect/page.tsx` → `GAMES`: add `{ name, returnUrl: "<scheme>://raneddie" }`
- `lib/game-auth.ts` → `connectedProducts` type: add the app id
- `app/api/game/token/route.ts`: include the app id in the product-bound branch
- The app's **Connected products** entry then appears in the user's Account Center.

## Rules for every product

- One Clerk instance (RanEddie ID production) and no other auth system.
- Keys only in environment variables. `sk_live` must never reach the browser or a repository.
- Sign-up always happens on id.raneddie.com, which enforces the age check (13+), the 15-character
  password minimum and the security check.
- Users manage their profile, security, privacy and connected products at
  https://id.raneddie.com/account.
