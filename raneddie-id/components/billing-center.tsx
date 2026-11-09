"use client";

import { useState } from "react";

/**
 * Payments & plans — UI preview.
 *
 * Everything here is display-only until a payment provider is connected (see BILLING.md).
 * Upgrade / add-card actions open a "launching soon" dialog; no card details are ever collected.
 */

type Sub = { id: string; product: string; plan: string; monthly: number; detail: string; href: string; color: string };

/** Subscriptions you can have in RanEddie products. Status comes from the payment provider once connected. */
const PRODUCT_SUBS: Sub[] = [
  { id: "bluebear", product: "BlueBear Stream", plan: "Standard", monthly: 9.99, detail: "Films and series, 4 profiles, downloads", href: "https://raneddie.com/bluebear-media", color: "#2f6bff" },
  { id: "core", product: "RanEddie Games", plan: "Core Pass", monthly: 11.99, detail: "50+ games, online play, cloud saves", href: "https://raneddie.com/raneddie-games", color: "#22c55e" },
  { id: "cloudly", product: "Cloudly", plan: "200 GB", monthly: 2.99, detail: "Storage for files, photos and backups", href: "https://raneddie.com/cloudly", color: "#38bdf8" },
  { id: "workit", product: "workIT", plan: "Personal", monthly: 6.99, detail: "Write, Sheets, Slides, Meet and workIT Assist", href: "https://raneddie.com/workit", color: "#3b82f6" },
  { id: "protector", product: "Protector Y", plan: "Family", monthly: 4.99, detail: "Every device, VPN and password manager", href: "https://raneddie.com/protector-y", color: "#ef4444" },
  { id: "remail", product: "rEmail", plan: "Premium", monthly: 2.99, detail: "Custom domains, aliases and rEmail Assist", href: "https://raneddie.com/remail", color: "#f43f5e" },
  { id: "part", product: "RanEddie Part", plan: "Part Care", monthly: 3.99, detail: "Accidental damage cover for your devices", href: "https://raneddie.com/raneddie-part", color: "#ffb020" },
  { id: "edittime", product: "EditTime", plan: "Pro", monthly: 19.99, detail: "AI editing tools, collaboration, all exports", href: "https://raneddie.com/edittime", color: "#ff3d7f" },
];

const money = (n: number) => (n === 0 ? "Free" : `$${n.toFixed(2)}`);

export default function BillingCenter({ name, email }: { name: string; email: string }) {
  const [dialog, setDialog] = useState<string | null>(null);
  const active: Sub[] = []; // filled from the payment provider once connected

  return (
    <>
      <div className="account-panel billing-current">
        <div className="account-panel-heading">
          <div>
            <span className="account-overline">OVERVIEW</span>
            <h3>Your subscriptions</h3>
            <p>Every subscription you have across RanEddie products, in one place.</p>
          </div>
        </div>
        <div className="billing-current-grid">
          <div><small>Active</small><b>{active.length}</b></div>
          <div><small>Monthly total</small><b>{money(active.reduce((t, s) => t + s.monthly, 0)) === "Free" ? "$0.00" : money(active.reduce((t, s) => t + s.monthly, 0))}</b></div>
          <div><small>Next charge</small><b>—</b></div>
          <div><small>Payment method</small><b>None</b></div>
        </div>
      </div>

      <div className="account-panel">
        <div className="account-panel-heading">
          <div>
            <span className="account-overline">RANEDDIE PRODUCTS</span>
            <h3>Subscriptions by product</h3>
            <p>Manage, upgrade or cancel subscriptions you started in any RanEddie product.</p>
          </div>
        </div>
        <div className="billing-subs">
          {PRODUCT_SUBS.map((s) => {
            const on = active.some((a) => a.id === s.id);
            return (
              <div key={s.id} className="billing-sub">
                <i style={{ background: s.color }}>{s.product.slice(0, 1)}</i>
                <div className="billing-sub-copy">
                  <b>{s.product} <small>{s.plan}</small></b>
                  <p>{s.detail}</p>
                </div>
                <div className="billing-sub-price"><b>{money(s.monthly)}</b><small>/month</small></div>
                <span className={on ? "account-verified" : "account-pending"}>{on ? "Active" : "Not subscribed"}</span>
                <button className="account-text-action" onClick={() => setDialog(`${on ? "Manage" : "Subscribe to"} ${s.product} ${s.plan}`)}>{on ? "Manage" : "Subscribe"}</button>
              </div>
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
