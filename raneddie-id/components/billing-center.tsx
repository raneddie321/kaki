"use client";

import { useState } from "react";

/**
 * Payments & plans — UI preview.
 *
 * Everything here is display-only until a payment provider is connected (see BILLING.md).
 * Upgrade / add-card actions open a "launching soon" dialog; no card details are ever collected.
 */

type Cycle = "monthly" | "yearly";
type Plan = { id: string; name: string; tagline: string; monthly: number; yearly: number; features: string[]; highlight?: boolean };

const PLANS: Plan[] = [
  {
    id: "free",
    name: "RanEddie Free",
    tagline: "Everything you need to get started.",
    monthly: 0,
    yearly: 0,
    features: ["One RanEddie ID for every product", "5 GB Cloudly storage", "Cloud saves for RanEddie games", "Standard support"],
  },
  {
    id: "plus",
    name: "RanEddie Plus",
    tagline: "More space and more for your games.",
    monthly: 4.99,
    yearly: 49.99,
    features: ["Everything in Free", "200 GB Cloudly storage", "Ad-free BlueBear Stream", "Member discounts in RanEddie stores", "Priority support"],
    highlight: true,
  },
  {
    id: "premium",
    name: "RanEddie Premium",
    tagline: "The whole RanEddie world, for the whole family.",
    monthly: 12.99,
    yearly: 129.99,
    features: ["Everything in Plus", "2 TB Cloudly storage, shared with up to 6 people", "Core Pass for RanEddie Games", "workIT Assist and rEmail Assist", "Protector Y for every device"],
  },
];

const money = (n: number) => (n === 0 ? "Free" : `$${n.toFixed(2)}`);

export default function BillingCenter({ name, email }: { name: string; email: string }) {
  const [cycle, setCycle] = useState<Cycle>("monthly");
  const [dialog, setDialog] = useState<string | null>(null);
  const current = PLANS[0];

  return (
    <>
      <div className="account-panel billing-current">
        <div className="account-panel-heading">
          <div>
            <span className="account-overline">YOUR PLAN</span>
            <h3>{current.name}</h3>
            <p>{current.tagline}</p>
          </div>
          <span className="account-verified">Active</span>
        </div>
        <div className="billing-current-grid">
          <div><small>Price</small><b>Free</b></div>
          <div><small>Renews</small><b>—</b></div>
          <div><small>Storage</small><b>5 GB</b></div>
          <div><small>Members</small><b>Just you</b></div>
        </div>
      </div>

      <div className="account-panel">
        <div className="account-panel-heading">
          <div>
            <span className="account-overline">PLANS</span>
            <h3>Choose what fits you</h3>
            <p>Switch or cancel any time. Prices include applicable taxes where required.</p>
          </div>
          <div className="billing-cycle" role="tablist" aria-label="Billing cycle">
            <button role="tab" aria-selected={cycle === "monthly"} className={cycle === "monthly" ? "active" : ""} onClick={() => setCycle("monthly")}>Monthly</button>
            <button role="tab" aria-selected={cycle === "yearly"} className={cycle === "yearly" ? "active" : ""} onClick={() => setCycle("yearly")}>Yearly <em>2 months free</em></button>
          </div>
        </div>
        <div className="billing-plans">
          {PLANS.map((p) => {
            const price = cycle === "monthly" ? p.monthly : p.yearly;
            const isCurrent = p.id === current.id;
            return (
              <article key={p.id} className={`billing-plan${p.highlight ? " highlight" : ""}${isCurrent ? " current" : ""}`}>
                {p.highlight && <span className="billing-badge">Most popular</span>}
                <h4>{p.name}</h4>
                <p>{p.tagline}</p>
                <div className="billing-price">
                  <b>{money(price)}</b>
                  {price > 0 && <small>/{cycle === "monthly" ? "month" : "year"}</small>}
                </div>
                <ul>{p.features.map((f) => <li key={f}>{f}</li>)}</ul>
                {isCurrent ? (
                  <button className="account-outline-btn billing-btn" disabled>Current plan</button>
                ) : (
                  <button className="account-primary-btn billing-btn" onClick={() => setDialog(`Upgrade to ${p.name}`)}>Upgrade</button>
                )}
              </article>
            );
          })}
        </div>
      </div>

      <div className="account-panel">
        <div className="account-panel-heading">
          <div><span className="account-overline">PAYMENT METHODS</span><h3>Cards and wallets</h3><p>Used for subscriptions and purchases across RanEddie.</p></div>
          <button className="account-text-action" onClick={() => setDialog("Add a payment method")}>+ Add payment method</button>
        </div>
        <div className="billing-empty">
          <div className="billing-card-art" aria-hidden><span /><span /></div>
          <div><b>No payment methods saved</b><p>Add a card or wallet when you&apos;re ready to upgrade or make a purchase.</p></div>
        </div>
      </div>

      <div className="account-panel">
        <div className="account-panel-heading">
          <div><span className="account-overline">BILLING HISTORY</span><h3>Invoices and receipts</h3><p>Download receipts for every payment.</p></div>
        </div>
        <div className="billing-table" role="table" aria-label="Billing history">
          <div className="billing-row head" role="row"><span role="columnheader">Date</span><span role="columnheader">Description</span><span role="columnheader">Amount</span><span role="columnheader">Status</span></div>
          <div className="billing-row empty" role="row"><span role="cell">No payments yet. Your invoices will appear here.</span></div>
        </div>
      </div>

      <div className="account-panel">
        <div className="account-panel-heading">
          <div><span className="account-overline">BILLING DETAILS</span><h3>Invoice information</h3><p>Shown on your receipts.</p></div>
          <button className="account-text-action" onClick={() => setDialog("Edit billing details")}>Edit</button>
        </div>
        <div className="billing-current-grid">
          <div><small>Name</small><b>{name || "—"}</b></div>
          <div><small>Email</small><b>{email || "—"}</b></div>
          <div><small>Country</small><b>—</b></div>
          <div><small>Tax ID</small><b>—</b></div>
        </div>
      </div>

      {dialog && (
        <div className="billing-dialog-backdrop" role="presentation" onClick={() => setDialog(null)}>
          <div className="billing-dialog" role="dialog" aria-modal="true" aria-labelledby="billing-dialog-title" onClick={(e) => e.stopPropagation()}>
            <div className="account-empty-icon">▤</div>
            <h3 id="billing-dialog-title">{dialog}</h3>
            <p>Payments for RanEddie ID are launching soon. You haven&apos;t been charged, and no payment details were collected.</p>
            <button className="account-primary-btn" onClick={() => setDialog(null)} autoFocus>Got it</button>
          </div>
        </div>
      )}
    </>
  );
}
