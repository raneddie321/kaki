"use client";

import Image from "next/image";
import Link from "next/link";
import { useId, useState, type ReactNode, type InputHTMLAttributes, type ButtonHTMLAttributes } from "react";

/** Full-page frame: brand backdrop, logo header, the white card and the footer. */
export function AuthShell({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <main className="relative flex min-h-dvh flex-col items-center px-4 py-8 sm:py-14">
      <div className="rid-backdrop" aria-hidden />
      <Link href="/" className="mb-7 flex items-center gap-3" aria-label="RanEddie ID home">
        <Image src="/raneddie-logo.png" alt="" width={56} height={44} priority className="h-11 w-auto drop-shadow-[0_6px_18px_rgba(47,123,255,0.55)]" />
        <span className="font-display text-2xl font-bold tracking-tight text-white">
          RanEddie <span className="rid-gradient-text">ID</span>
        </span>
      </Link>
      <div
        className={`w-full ${wide ? "max-w-xl" : "max-w-md"} rounded-3xl bg-white p-6 shadow-[0_30px_80px_-20px_rgba(0,0,0,0.6)] ring-1 ring-white/10 sm:p-9`}
      >
        {children}
      </div>
      <footer className="mt-8 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs text-white/50">
        <span>© {new Date().getFullYear()} RanEddie</span>
        <Link href="/privacy" className="hover:text-white/80">Privacy</Link>
        <Link href="/terms" className="hover:text-white/80">Terms</Link>
        <span className="flex items-center gap-1">
          <LockIcon className="h-3 w-3" /> Secured by Clerk
        </span>
      </footer>
    </main>
  );
}

export function Heading({ title, subtitle }: { title: string; subtitle?: ReactNode }) {
  return (
    <div className="mb-6">
      <h1 className="font-display text-[26px] font-bold leading-tight tracking-tight text-ink">{title}</h1>
      {subtitle && <p className="mt-1.5 text-[15px] leading-relaxed text-muted">{subtitle}</p>}
    </div>
  );
}

/** Progress for multi-step forms: a segmented bar plus "Step x of y". */
export function Stepper({ step, total, label }: { step: number; total: number; label: string }) {
  return (
    <div className="mb-6" aria-label={`Step ${step} of ${total}: ${label}`}>
      <div className="mb-2 flex items-center justify-between text-xs font-semibold">
        <span className="uppercase tracking-wider text-brand-600">{label}</span>
        <span className="text-muted">
          Step {step} of {total}
        </span>
      </div>
      <div className="flex gap-1.5">
        {Array.from({ length: total }, (_, i) => (
          <div
            key={i}
            className={`h-1.5 flex-1 rounded-full transition-colors duration-300 ${
              i < step ? "bg-gradient-to-r from-brand-600 to-cyan-brand" : "bg-line"
            }`}
          />
        ))}
      </div>
    </div>
  );
}

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string | null;
  hint?: ReactNode;
  trailing?: ReactNode;
};

export function Field({ label, error, hint, trailing, className, ...input }: FieldProps) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-semibold text-ink">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          aria-invalid={!!error}
          aria-describedby={error ? `${id}-err` : undefined}
          className={`h-12 w-full rounded-xl border bg-field px-4 text-[15px] text-ink outline-none transition placeholder:text-muted/60 focus:bg-white focus:ring-4 ${
            error
              ? "border-red-400 focus:border-red-500 focus:ring-red-100"
              : "border-line focus:border-brand-500 focus:ring-brand-100"
          } ${trailing ? "pr-12" : ""}`}
          {...input}
        />
        {trailing && <div className="absolute inset-y-0 right-2 flex items-center">{trailing}</div>}
      </div>
      {error ? (
        <p id={`${id}-err`} className="mt-1.5 flex items-start gap-1.5 text-sm text-red-600">
          <AlertIcon className="mt-0.5 h-4 w-4 shrink-0" />
          {error}
        </p>
      ) : (
        hint && <div className="mt-1.5 text-sm text-muted">{hint}</div>
      )}
    </div>
  );
}

export function PasswordField(props: Omit<FieldProps, "type" | "trailing">) {
  const [show, setShow] = useState(false);
  return (
    <Field
      {...props}
      type={show ? "text" : "password"}
      trailing={
        <button
          type="button"
          onClick={() => setShow((s) => !s)}
          className="rounded-lg p-2 text-muted hover:bg-line/60 hover:text-ink"
          aria-label={show ? "Hide password" : "Show password"}
        >
          {show ? <EyeOffIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
        </button>
      }
    />
  );
}

export function SelectField({
  label,
  error,
  children,
  className,
  ...select
}: React.SelectHTMLAttributes<HTMLSelectElement> & { label?: string; error?: string | null }) {
  const id = useId();
  return (
    <div className={className}>
      {label && (
        <label htmlFor={id} className="mb-1.5 block text-sm font-semibold text-ink">
          {label}
        </label>
      )}
      <div className="relative">
        <select
          id={id}
          aria-invalid={!!error}
          className={`h-12 w-full appearance-none rounded-xl border bg-field pl-4 pr-10 text-[15px] text-ink outline-none transition focus:bg-white focus:ring-4 ${
            error ? "border-red-400 focus:ring-red-100" : "border-line focus:border-brand-500 focus:ring-brand-100"
          }`}
          {...select}
        >
          {children}
        </select>
        <ChevronIcon className="pointer-events-none absolute right-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
      </div>
    </div>
  );
}

export function Button({
  children,
  loading,
  variant = "primary",
  className = "",
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { loading?: boolean; variant?: "primary" | "ghost" }) {
  const base =
    "inline-flex h-12 w-full items-center justify-center gap-2 rounded-xl px-5 text-[15px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-60";
  const style =
    variant === "primary"
      ? "rid-btn-primary text-white"
      : "border border-line bg-white text-ink hover:bg-field";
  return (
    <button className={`${base} ${style} ${className}`} disabled={loading || rest.disabled} {...rest}>
      {loading && <Spinner className="h-5 w-5" />}
      {children}
    </button>
  );
}

/** A link that looks like {@link Button} (a button inside a link is invalid HTML). */
export function LinkButton({ href, children, variant = "primary" }: { href: string; children: ReactNode; variant?: "primary" | "ghost" }) {
  const style = variant === "primary" ? "rid-btn-primary text-white" : "border border-line bg-white text-ink hover:bg-field";
  return (
    <Link
      href={href}
      className={`inline-flex h-12 w-full items-center justify-center rounded-xl px-5 text-[15px] font-semibold transition ${style}`}
    >
      {children}
    </Link>
  );
}

export function BackButton({ onClick, label = "Back" }: { onClick: () => void; label?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mb-4 -ml-1 inline-flex items-center gap-1 rounded-lg px-1 py-1 text-sm font-semibold text-muted hover:text-ink"
    >
      <ChevronIcon className="h-4 w-4 rotate-90" />
      {label}
    </button>
  );
}

export function Alert({ children, tone = "error" }: { children: ReactNode; tone?: "error" | "info" | "success" }) {
  const styles = {
    error: "border-red-200 bg-red-50 text-red-700",
    info: "border-brand-100 bg-brand-50 text-brand-700",
    success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  }[tone];
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`mb-5 flex items-start gap-2 rounded-xl border px-4 py-3 text-sm ${styles}`}>
      {tone === "success" ? <CheckIcon className="mt-0.5 h-4 w-4 shrink-0" /> : <AlertIcon className="mt-0.5 h-4 w-4 shrink-0" />}
      <div>{children}</div>
    </div>
  );
}

/** The email the user typed, shown as a removable chip on later steps. */
export function IdentityChip({ email, onEdit }: { email: string; onEdit: () => void }) {
  return (
    <div className="mb-5 flex items-center justify-between gap-3 rounded-xl border border-line bg-field px-3 py-2">
      <div className="flex min-w-0 items-center gap-2.5">
        <div className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-gradient-to-br from-brand-500 to-cyan-brand text-sm font-bold uppercase text-white">
          {email.slice(0, 1)}
        </div>
        <span className="truncate text-sm font-medium text-ink">{email}</span>
      </div>
      <button type="button" onClick={onEdit} className="shrink-0 text-sm font-semibold text-brand-600 hover:text-brand-700">
        Edit
      </button>
    </div>
  );
}

export function Divider() {
  return <div className="my-6 h-px bg-line" />;
}

// ------------------------------------------------------------------ icons

type IconProps = { className?: string };

export function Spinner({ className }: IconProps) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden>
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function CheckIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </svg>
  );
}

export function AlertIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden>
      <circle cx="12" cy="12" r="9.5" />
      <path d="M12 7.5v5.5M12 16.5h.01" />
    </svg>
  );
}

export function LockIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2.5" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
    </svg>
  );
}

export function MailIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden>
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <path d="M4 7l8 6 8-6" />
    </svg>
  );
}

export function ShieldIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden>
      <path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6L12 3z" />
      <path d="M8.8 12.2l2.2 2.2 4.4-4.6" strokeLinecap="round" />
    </svg>
  );
}

function EyeIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
      <path d="M3 3l18 18M10.6 5.6A9.6 9.6 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-3.2 3.9M6.6 6.6C3.9 8.4 2.5 12 2.5 12S6 18.5 12 18.5c1.6 0 3-.4 4.2-1" />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
    </svg>
  );
}

function ChevronIcon({ className }: IconProps) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}
