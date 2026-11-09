# Making RanEddie ID payments real

The **Payments & plans** page (`components/billing-center.tsx`) is a UI preview: plans, payment
methods, history and billing details are display-only and no card data is collected.

## Recommended: Stripe with your own UI (no third-party branding)
1. Create a Stripe account, finish business verification, and add your bank account.
2. In Stripe, create Products + Prices for each plan (monthly and yearly).
3. Add Vercel env vars (project `kaki`): `STRIPE_SECRET_KEY`, `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`.
4. Server routes to add:
   - `POST /api/billing/checkout` — creates a Checkout Session or a Subscription for the signed-in Clerk user (store `stripeCustomerId` in the user's private metadata).
   - `POST /api/billing/setup-intent` — for adding a card.
   - `POST /api/stripe/webhook` — on `customer.subscription.*` and `invoice.*`, save plan/status to Clerk private metadata.
   - `GET /api/billing/summary` — plan, payment methods and invoices for the page.
5. Collect cards with **Stripe Elements** (`@stripe/react-stripe-js`) styled with your fonts/colors —
   card fields stay inside your page and card numbers never touch your servers (PCI SAQ-A).
6. Turn on Apple Pay / Google Pay in Stripe (verify the domain `id.raneddie.com`).
7. Taxes: enable Stripe Tax. Receipts: Stripe emails them; brand them in Stripe settings.
8. Test with Stripe test keys and test cards, then switch to live keys.

Branding: Elements renders without a visible Stripe logo; hosted Checkout and the Customer Portal can
use your logo, colors and the custom domain feature (e.g. `pay.raneddie.com`).

Alternative: Clerk Billing (built on Stripe) — less code, uses Clerk's components.
