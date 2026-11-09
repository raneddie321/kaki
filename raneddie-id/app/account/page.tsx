"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useClerk, useReverification, useUser } from "@clerk/nextjs";

type Section = "overview" | "profile" | "security" | "products" | "billing" | "privacy" | "preferences";

const navigation: { id: Section; label: string; description: string; icon: string }[] = [
  { id: "overview", label: "Overview", description: "Your account at a glance", icon: "◫" },
  { id: "profile", label: "Personal information", description: "Name, username and photo", icon: "◎" },
  { id: "security", label: "Security", description: "Password and verification", icon: "◇" },
  { id: "products", label: "Connected products", description: "Your RanEddie ecosystem", icon: "▦" },
  { id: "billing", label: "Payments & plans", description: "Billing and subscriptions", icon: "▤" },
  { id: "privacy", label: "Privacy", description: "Your data and account", icon: "◈" },
  { id: "preferences", label: "Preferences", description: "Appearance and language", icon: "⚙" },
];

function initials(first?: string | null, last?: string | null, username?: string | null, email?: string) {
  const value = [first, last].filter(Boolean).join(" ") || username || email || "R";
  return value.slice(0, 1).toUpperCase();
}

function dateLabel(date?: Date | null) {
  if (!date) return "—";
  return new Intl.DateTimeFormat("en", { month: "long", year: "numeric" }).format(date);
}

export default function AccountCenterPage() {
  const { isLoaded, isSignedIn, user } = useUser();
  const { signOut } = useClerk();
  const createEmailAddress = useReverification((email: string) => user?.createEmailAddress({ email }));
  const setPrimaryEmailAddress = useReverification((id: string) => user?.update({ primaryEmailAddressId: id }));
  const [section, setSection] = useState<Section>("overview");
  const [mobileNav, setMobileNav] = useState(false);
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [username, setUsername] = useState("");
  const [profileReady, setProfileReady] = useState(false);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<{ tone: "success" | "error"; message: string } | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [newEmail, setNewEmail] = useState("");
  const [emailCode, setEmailCode] = useState("");
  const [pendingEmailId, setPendingEmailId] = useState<string | null>(null);
  const [emailBusy, setEmailBusy] = useState(false);
  const [theme, setTheme] = useState<"system" | "light" | "dark">("system");
  const [connectedProducts, setConnectedProducts] = useState<{ snakebrawl: number | null; fizzapp: number | null }>({ snakebrawl: null, fizzapp: null });
  const [productsLoading, setProductsLoading] = useState(false);
  const [disconnecting, setDisconnecting] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return;
    let active = true;
    setProductsLoading(true);
    fetch("/api/account/products", { cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error("Could not load connected products.");
        return response.json() as Promise<{ products?: { snakebrawl?: number | null; fizzapp?: number | null } }>;
      })
      .then((data) => {
        if (active) setConnectedProducts({
          snakebrawl: data.products?.snakebrawl ?? null,
          fizzapp: data.products?.fizzapp ?? null,
        });
      })
      .catch(() => {
        if (active) setNotice({ tone: "error", message: "Connected product status could not be loaded." });
      })
      .finally(() => {
        if (active) setProductsLoading(false);
      });
    return () => { active = false; };
  }, [isLoaded, isSignedIn]);

  const primaryEmail = user?.primaryEmailAddress?.emailAddress ?? "";
  const emailVerified = user?.primaryEmailAddress?.verification?.status === "verified";
  const displayName = useMemo(() => {
    if (!user) return "Your account";
    return user.fullName || user.username || primaryEmail || "RanEddie member";
  }, [user, primaryEmail]);

  async function disconnectProduct(app: "snakebrawl" | "fizzapp") {
    setDisconnecting(app);
    setNotice(null);
    try {
      const response = await fetch("/api/account/products", {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ app }),
      });
      if (!response.ok) throw new Error("The product could not be disconnected.");
      setConnectedProducts((current) => ({ ...current, [app]: null }));
      setNotice({ tone: "success", message: app === "snakebrawl" ? "Snake Brawl was disconnected. Its saved cloud data remains in your account." : "Fizz App was disconnected." });
    } catch (error) {
      setNotice({ tone: "error", message: error instanceof Error ? error.message : "Could not disconnect the product." });
    } finally {
      setDisconnecting(null);
    }
  }

  function openSection(next: Section) {
    setSection(next);
    setNotice(null);
    setMobileNav(false);
    if (next === "profile" && user) {
      setFirstName(user.firstName ?? "");
      setLastName(user.lastName ?? "");
      setUsername(user.username ?? "");
      setProfileReady(true);
    }
  }

  async function saveProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    setSaving(true);
    setNotice(null);
    try {
      await user.update({
        firstName: firstName.trim() || undefined,
        lastName: lastName.trim() || undefined,
        username: username.trim() || undefined,
      });
      setNotice({ tone: "success", message: "Your profile was updated in RanEddie ID." });
      setProfileReady(false);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Clerk could not update your profile. Please try again.";
      setNotice({ tone: "error", message });
    } finally {
      setSaving(false);
    }
  }

  async function uploadAvatar(event: FormEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (!file || !user) return;
    if (!file.type.startsWith("image/")) {
      setNotice({ tone: "error", message: "Choose an image file." });
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setNotice({ tone: "error", message: "The image must be smaller than 5 MB." });
      return;
    }
    setSaving(true);
    setNotice(null);
    try {
      await user.setProfileImage({ file });
      setNotice({ tone: "success", message: "Your profile photo was updated." });
    } catch (error) {
      setNotice({ tone: "error", message: error instanceof Error ? error.message : "The image could not be uploaded." });
    } finally {
      setSaving(false);
      input.value = "";
    }
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user) return;
    if (newPassword.length < 8) {
      setNotice({ tone: "error", message: "Use at least 8 characters for your new password." });
      return;
    }
    setPasswordBusy(true);
    setNotice(null);
    try {
      await user.updatePassword({ currentPassword, newPassword, signOutOfOtherSessions: true });
      setCurrentPassword("");
      setNewPassword("");
      setNotice({ tone: "success", message: "Password updated. Other active sessions were signed out." });
    } catch (error) {
      setNotice({ tone: "error", message: error instanceof Error ? error.message : "Password update failed. Check your current password." });
    } finally {
      setPasswordBusy(false);
    }
  }

  async function startEmailChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user || !newEmail.trim()) return;
    setEmailBusy(true);
    setNotice(null);
    try {
      const createdAddress = await createEmailAddress(newEmail.trim());
      if (!createdAddress) throw new Error("Clerk did not create the new email address.");
      await user.reload();
      const address = user.emailAddresses.find((item) => item.id === createdAddress.id);
      if (!address) throw new Error("The new email address could not be loaded.");
      await address.prepareVerification({ strategy: "email_code" });
      setPendingEmailId(address.id);
      setNotice({ tone: "success", message: "A verification code was sent to your new email address." });
    } catch (error) {
      setNotice({ tone: "error", message: error instanceof Error ? error.message : "Could not start email verification." });
    } finally {
      setEmailBusy(false);
    }
  }

  async function verifyEmailChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!user || !pendingEmailId) return;
    setEmailBusy(true);
    setNotice(null);
    try {
      const address = user.emailAddresses.find((item) => item.id === pendingEmailId);
      if (!address) throw new Error("The pending email address could not be found. Start again.");
      await address.attemptVerification({ code: emailCode.trim() });
      await setPrimaryEmailAddress(pendingEmailId);
      await user.reload();
      setPendingEmailId(null);
      setNewEmail("");
      setEmailCode("");
      setNotice({ tone: "success", message: "Your new email is verified and now primary." });
    } catch (error) {
      setNotice({ tone: "error", message: error instanceof Error ? error.message : "Email verification failed." });
    } finally {
      setEmailBusy(false);
    }
  }

  if (!isLoaded) {
    return <main className="account-loading"><div className="account-spinner" /><p>Loading your RanEddie account…</p></main>;
  }

  if (!isSignedIn || !user) {
    return (
      <main className="account-unauth">
        <div className="account-unauth-card">
          <div className="account-brand-mark">R<span>ID</span></div>
          <p className="account-kicker">RANEDDIE ID</p>
          <h1>Your account, all in one place.</h1>
          <p>Sign in to manage your profile, security and connected RanEddie products.</p>
          <a className="account-primary-btn" href="/sign-in?redirect_url=%2Faccount">Sign in to RanEddie ID</a>
          <a className="account-back-link" href="/">Back to RanEddie</a>
        </div>
      </main>
    );
  }

  return (
    <main className="account-shell">
      <header className="account-topbar">
        <a href="/" className="account-wordmark" aria-label="RanEddie ID home">
          <span className="account-logo">R</span>
          <span>RanEddie <b>ID</b></span>
        </a>
        <div className="account-top-actions">
          <a href="https://raneddie.com" className="account-back-site">RanEddie website ↗</a>
          <button className="account-avatar account-avatar-small" onClick={() => openSection("profile")} aria-label="Open profile">
            {user.imageUrl ? <img src={user.imageUrl} alt="" /> : initials(user.firstName, user.lastName, user.username, primaryEmail)}
          </button>
        </div>
      </header>

      <div className="account-layout">
        <aside className={mobileNav ? "account-sidebar account-sidebar-open" : "account-sidebar"}>
          <div className="account-sidebar-heading">
            <span className="account-overline">YOUR WORKSPACE</span>
            <h2>Account center</h2>
            <p>Manage your RanEddie identity.</p>
          </div>
          <nav className="account-nav" aria-label="Account navigation">
            {navigation.map((item) => (
              <button key={item.id} className={section === item.id ? "account-nav-item active" : "account-nav-item"} onClick={() => openSection(item.id)}>
                <span className="account-nav-icon">{item.icon}</span>
                <span className="account-nav-copy"><b>{item.label}</b><small>{item.description}</small></span>
                <span className="account-nav-chevron">›</span>
              </button>
            ))}
          </nav>
          <div className="account-sidebar-footer">
            <div className="account-secure-indicator"><span /> Identity protected by Clerk</div>
            <button onClick={() => signOut({ redirectUrl: "/" })} className="account-signout">↗ Sign out</button>
          </div>
        </aside>

        <section className="account-content">
          <button className="account-mobile-menu" onClick={() => setMobileNav((value) => !value)}>{mobileNav ? "Close menu" : "☰ Account menu"}</button>
          <div className="account-page-heading">
            <div>
              <p className="account-overline">RANEDDIE ID / {section.toUpperCase()}</p>
              <h1>{navigation.find((item) => item.id === section)?.label}</h1>
              <p>{navigation.find((item) => item.id === section)?.description}</p>
            </div>
            <div className="account-live-pill"><span /> Live account data</div>
          </div>

          {notice && <div className={notice.tone === "success" ? "account-notice success" : "account-notice error"} role={notice.tone === "error" ? "alert" : "status"}>{notice.message}</div>}

          {section === "overview" && (
            <>
              <div className="account-welcome-card">
                <div className="account-welcome-glow" />
                <div className="account-welcome-copy">
                  <span className="account-overline">YOUR RANEDDIE ID</span>
                  <h2>Welcome back, {user.firstName || user.username || "there"}.</h2>
                  <p>One identity for every connected RanEddie experience.</p>
                  <button onClick={() => openSection("profile")} className="account-outline-btn">Manage profile <span>→</span></button>
                </div>
                <div className="account-welcome-avatar">
                  {user.imageUrl ? <img src={user.imageUrl} alt="" /> : initials(user.firstName, user.lastName, user.username, primaryEmail)}
                </div>
                <div className="account-welcome-orbit orbit-one" />
                <div className="account-welcome-orbit orbit-two" />
              </div>
              <div className="account-stat-grid">
                <article className="account-stat-card"><span className="account-stat-label">ACCOUNT STATUS</span><strong className="account-stat-value"><i className="account-status-dot" /> Active</strong><small>Your Clerk account is signed in</small></article>
                <article className="account-stat-card"><span className="account-stat-label">EMAIL VERIFICATION</span><strong className="account-stat-value">{emailVerified ? "Verified" : "Pending"}</strong><small>{primaryEmail || "No primary email"}</small></article>
                <article className="account-stat-card"><span className="account-stat-label">MEMBER SINCE</span><strong className="account-stat-value">{dateLabel(user.createdAt)}</strong><small>Part of the RanEddie ecosystem</small></article>
              </div>
              <div className="account-panel">
                <div className="account-panel-heading"><div><span className="account-overline">ACCOUNT DETAILS</span><h3>Your identity</h3></div><button onClick={() => openSection("profile")} className="account-text-action">Edit details →</button></div>
                <div className="account-detail-grid">
                  <div><small>Full name</small><b>{user.fullName || "Not added yet"}</b></div>
                  <div><small>Username</small><b>{user.username ? "@" + user.username : "Not set"}</b></div>
                  <div><small>Primary email</small><b>{primaryEmail || "Not set"}</b></div>
                  <div><small>RanEddie ID</small><b className="account-mono">{user.id}</b></div>
                </div>
              </div>
              <div className="account-panel">
                <div className="account-panel-heading"><div><span className="account-overline">ECOSYSTEM</span><h3>Connected products</h3></div><button onClick={() => openSection("products")} className="account-text-action">View products →</button></div>
                <p className="account-muted-paragraph">Product connections will appear here after each product completes its RanEddie ID integration.</p>
              </div>
            </>
          )}

          {section === "profile" && (
            <div className="account-panel account-panel-large">
              <div className="account-panel-heading"><div><span className="account-overline">PROFILE SETTINGS</span><h3>Personal information</h3><p>Changes are saved directly to your Clerk identity.</p></div></div>
              <div className="account-photo-row">
                <div className="account-avatar account-avatar-large">{user.imageUrl ? <img src={user.imageUrl} alt="Profile" /> : initials(user.firstName, user.lastName, user.username, primaryEmail)}</div>
                <div><b>Profile photo</b><p>JPG, PNG or WebP. Maximum 5 MB.</p><button className="account-secondary-btn" onClick={() => fileRef.current?.click()} disabled={saving}>Upload new photo</button><input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={uploadAvatar} /></div>
              </div>
              <form className="account-form" onSubmit={saveProfile}>
                <div className="account-form-grid">
                  <label>First name<input value={profileReady ? firstName : user.firstName ?? ""} onFocus={() => !profileReady && openSection("profile")} onChange={(e) => setFirstName(e.target.value)} placeholder="First name" /></label>
                  <label>Last name<input value={profileReady ? lastName : user.lastName ?? ""} onChange={(e) => setLastName(e.target.value)} placeholder="Last name" /></label>
                  <label className="account-form-full">Username<input value={profileReady ? username : user.username ?? ""} onChange={(e) => setUsername(e.target.value)} placeholder="Your username" minLength={4} maxLength={20} /><small>Username availability and enabled fields are validated by Clerk.</small></label>
                  <label className="account-form-full">Primary email<div className="account-readonly-field"><span>{primaryEmail || "No email address"}</span><span className={emailVerified ? "account-verified" : "account-pending"}>{emailVerified ? "Verified" : "Unverified"}</span></div></label>
                </div>
                <div className="account-form-actions"><button type="button" className="account-secondary-btn" onClick={() => { setFirstName(user.firstName ?? ""); setLastName(user.lastName ?? ""); setUsername(user.username ?? ""); setProfileReady(true); }}>Reset</button><button className="account-primary-btn" type="submit" disabled={saving}>{saving ? "Saving…" : "Save changes"}</button></div>
              </form>
              <div className="account-subsection">
                <h4>Change email address</h4>
                <p>Your new address must be verified before it becomes your primary email.</p>
                {!pendingEmailId ? (
                  <form className="account-inline-form" onSubmit={startEmailChange}>
                    <input type="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} placeholder="new@email.com" required />
                    <button className="account-secondary-btn" disabled={emailBusy}>{emailBusy ? "Sending…" : "Send verification code"}</button>
                  </form>
                ) : (
                  <form className="account-inline-form" onSubmit={verifyEmailChange}>
                    <input inputMode="numeric" value={emailCode} onChange={(e) => setEmailCode(e.target.value)} placeholder="Verification code" required />
                    <button className="account-primary-btn" disabled={emailBusy}>{emailBusy ? "Verifying…" : "Verify and update"}</button>
                  </form>
                )}
              </div>
            </div>
          )}

          {section === "security" && (
            <div className="account-stack">
              <div className="account-panel">
                <div className="account-panel-heading"><div><span className="account-overline">PASSWORD</span><h3>Update your password</h3><p>Your password is managed securely by Clerk.</p></div><span className="account-security-icon">◇</span></div>
                <form className="account-form" onSubmit={changePassword}>
                  <label>Current password<input type="password" autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required /></label>
                  <label>New password<input type="password" autoComplete="new-password" minLength={8} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required /></label>
                  <div className="account-form-actions"><button className="account-primary-btn" disabled={passwordBusy}>{passwordBusy ? "Updating…" : "Update password"}</button></div>
                </form>
              </div>
              <div className="account-panel">
                <div className="account-panel-heading"><div><span className="account-overline">AUTHENTICATION</span><h3>Account protection</h3></div></div>
                <div className="account-security-row"><div><b>Two-factor authentication</b><p>{user.twoFactorEnabled ? "Two-factor authentication is enabled." : "Two-factor authentication is not enabled on this account."}</p></div><span className={user.twoFactorEnabled ? "account-verified" : "account-pending"}>{user.twoFactorEnabled ? "Enabled" : "Not enabled"}</span></div>
                <div className="account-security-row"><div><b>Authenticator app</b><p>Authenticator enrollment is controlled by your Clerk instance settings.</p></div><span className={user.totpEnabled ? "account-verified" : "account-pending"}>{user.totpEnabled ? "Enabled" : "Not enabled"}</span></div>
                <div className="account-security-row"><div><b>Current session</b><p>You are signed in to this RanEddie ID session.</p></div><span className="account-verified">Active</span></div>
              </div>
            </div>
          )}

          {section === "products" && (
            <div className="account-panel">
              <div className="account-panel-heading"><div><span className="account-overline">RANEDDIE ECOSYSTEM</span><h3>Connected products</h3><p>Connection status is read from your private Clerk account metadata.</p></div></div>
              <div className="account-product-list">
                <div className="account-product-row"><div className="account-product-icon">R</div><div className="account-product-copy"><b>RanEddie ID</b><small>Central identity provider</small></div><span className="account-verified">Active</span></div>
                <div className="account-product-row"><div className="account-product-icon">S</div><div className="account-product-copy"><b>Snake Brawl</b><small>Game account and cloud-save connection</small></div>{connectedProducts.snakebrawl ? <><span className="account-verified">Connected</span><button className="account-text-action" disabled={disconnecting === "snakebrawl"} onClick={() => disconnectProduct("snakebrawl")}>{disconnecting === "snakebrawl" ? "Disconnecting…" : "Disconnect"}</button></> : <span className="account-pending">{productsLoading ? "Checking…" : "Not connected"}</span>}</div>
                <div className="account-product-row"><div className="account-product-icon">F</div><div className="account-product-copy"><b>Fizz App</b><small>Game-link and cloud-save integration</small></div>{connectedProducts.fizzapp ? <><span className="account-verified">Connected</span><button className="account-text-action" disabled={disconnecting === "fizzapp"} onClick={() => disconnectProduct("fizzapp")}>{disconnecting === "fizzapp" ? "Disconnecting…" : "Disconnect"}</button></> : <span className="account-pending">{productsLoading ? "Checking…" : "Not connected"}</span>}</div>
                <div className="account-product-row"><div className="account-product-icon">F</div><div className="account-product-copy"><b>FIZZ Platform</b><small>Browser and AI workspace</small></div><span className="account-pending">Identity migration pending</span></div>
                <div className="account-product-row"><div className="account-product-icon">P</div><div className="account-product-copy"><b>Prime Code</b><small>Developer tools</small></div><span className="account-pending">Not connected</span></div>
              </div>
              <p className="account-footnote">Disconnecting a game revokes its product token. Existing cloud-save data remains in your private RanEddie ID account. FIZZ Platform and Prime Code still require their own integrations.</p>
            </div>
          )}

          {section === "billing" && (
            <div className="account-panel account-empty-panel">
              <div className="account-empty-icon">▤</div><span className="account-overline">BILLING CENTER</span><h3>Payments are not connected yet</h3><p>RanEddie ID is ready to host a unified billing experience, but no payment processor is currently configured. No payment methods, subscriptions or invoices are being fabricated.</p><div className="account-info-note">Your identity and profile are active. Billing will become available after a supported payment provider is configured.</div>
            </div>
          )}

          {section === "privacy" && (
            <div className="account-panel">
              <div className="account-panel-heading"><div><span className="account-overline">DATA & PRIVACY</span><h3>Your account data</h3><p>Understand what is managed by your identity provider.</p></div></div>
              <div className="account-security-row"><div><b>Identity data</b><p>Your name, username, email and profile photo are stored in Clerk.</p></div><span className="account-verified">Clerk</span></div>
              <div className="account-security-row"><div><b>Account identifier</b><p className="account-mono">{user.id}</p></div><button className="account-text-action" onClick={() => navigator.clipboard?.writeText(user.id)}>Copy ID</button></div>
              <div className="account-security-row"><div><b>Account deletion</b><p>{user.deleteSelfEnabled ? "Your Clerk instance allows self-service account deletion." : "Self-service deletion is not currently enabled in the Clerk instance."}</p></div><span className="account-pending">{user.deleteSelfEnabled ? "Available" : "Not enabled"}</span></div>
              {user.deleteSelfEnabled && <button className="account-danger-btn" onClick={async () => { if (window.confirm("Permanently delete your RanEddie ID? This action cannot be undone.")) { await user.delete(); window.location.assign("/"); } }}>Delete my account</button>}
            </div>
          )}

          {section === "preferences" && (
            <div className="account-panel">
              <div className="account-panel-heading"><div><span className="account-overline">YOUR EXPERIENCE</span><h3>Preferences</h3><p>Choose how the account center looks on this device.</p></div></div>
              <div className="account-preference-row"><div><b>Appearance</b><p>Choose your preferred color scheme.</p></div><select value={theme} onChange={(e) => { const next = e.target.value as "system" | "light" | "dark"; setTheme(next); document.documentElement.dataset.accountTheme = next; }}><option value="system">System</option><option value="light">Light</option><option value="dark">Dark</option></select></div>
              <div className="account-preference-row"><div><b>Language</b><p>Account center interface language.</p></div><span className="account-pending">English</span></div>
              <p className="account-footnote">Appearance is currently a device-level preference. Account-wide preference synchronization will be added with a dedicated settings store.</p>
            </div>
          )}
          <footer className="account-footer"><span>© {new Date().getFullYear()} RanEddie Studio</span><span>One identity. Every experience.</span><a href="https://raneddie.com">raneddie.com</a></footer>
        </section>
      </div>
    </main>
  );
}
