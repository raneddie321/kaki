"use client";

import Link from "next/link";
import { useRedirect } from "@/lib/use-redirect";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { useSignIn } from "@clerk/nextjs";
import {
  Alert,
  AuthShell,
  BackButton,
  Button,
  CheckIcon,
  Field,
  Heading,
  IdentityChip,
  LockIcon,
  MailIcon,
  PasswordField,
  Stepper,
} from "@/components/ui";
import { clerkError, isEmail, navigateAfterAuth, passwordChecks } from "@/lib/auth-helpers";

type Step = "email" | "code" | "password";

function ForgotPassword() {
  const { signIn, fetchStatus } = useSignIn();
  const router = useRouter();
  const params = useSearchParams();
  const { target, withRedirect } = useRedirect();
  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState(params.get("email") ?? "");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<Record<string, string | null>>({});
  const [busy, setBusy] = useState(false);
  const loading = busy || fetchStatus === "fetching";

  // Step 1: send a reset code to the email
  async function sendCode(e?: FormEvent) {
    e?.preventDefault();
    setError(null);
    setFieldError({});
    if (!isEmail(email)) return setFieldError({ email: "Enter a valid email address." });
    setBusy(true);
    const created = await signIn.create({ identifier: email.trim() });
    if (created.error) {
      setBusy(false);
      return setError(clerkError(created.error).message);
    }
    const sent = await signIn.resetPasswordEmailCode.sendCode();
    setBusy(false);
    if (sent.error) return setError(clerkError(sent.error).message);
    setStep("code");
  }

  // Step 2: check the code
  async function verifyCode(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const { error } = await signIn.resetPasswordEmailCode.verifyCode({ code: code.trim() });
    setBusy(false);
    if (error) return setError(clerkError(error).message);
    setStep("password");
  }

  // Step 3: choose a new password; the player is signed in afterwards
  const checks = passwordChecks(password);
  async function submitPassword(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const errs: Record<string, string | null> = {};
    if (!checks.every((c) => c.ok)) errs.password = "Your new password doesn't meet all the requirements yet.";
    if (confirm !== password) errs.confirm = "Passwords don't match.";
    if (errs.password || errs.confirm) return setFieldError(errs);
    setFieldError({});
    setBusy(true);
    const { error } = await signIn.resetPasswordEmailCode.submitPassword({ password, signOutOfOtherSessions: true });
    if (error) {
      setBusy(false);
      return setError(clerkError(error).message);
    }
    if (signIn.status === "complete") {
      await signIn.finalize({ navigate: navigateAfterAuth(router, target("/?reset=1")) });
    } else {
      setBusy(false);
      setError("Your password was changed. Please sign in with the new password.");
    }
  }

  const stepNo = { email: 1, code: 2, password: 3 }[step];
  const stepper = <Stepper step={stepNo} total={3} label={["Email", "Code", "New password"][stepNo - 1]} />;

  return (
    <AuthShell>
      {step === "email" && (
        <form key="email" onSubmit={sendCode} className="rid-step" noValidate>
          <Link
            href={withRedirect("/sign-in")}
            className="mb-4 -ml-1 inline-flex items-center gap-1 rounded-lg px-1 py-1 text-sm font-semibold text-muted hover:text-ink"
          >
            ‹ Back to sign in
          </Link>
          {stepper}
          <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600">
            <LockIcon className="h-6 w-6" />
          </div>
          <Heading
            title="Forgot your password?"
            subtitle="Enter the email of your RanEddie ID and we'll send you a code to reset it."
          />
          {error && <Alert>{error}</Alert>}
          <Field
            label="Email"
            type="email"
            name="email"
            autoComplete="username"
            inputMode="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldError.email}
            autoFocus
          />
          <Button type="submit" loading={loading} className="mt-6">
            Send reset code
          </Button>
        </form>
      )}

      {step === "code" && (
        <form key="code" onSubmit={verifyCode} className="rid-step" noValidate>
          <BackButton onClick={() => setStep("email")} />
          {stepper}
          <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600">
            <MailIcon className="h-6 w-6" />
          </div>
          <Heading
            title="Check your email"
            subtitle={
              <>
                We sent a 6-digit code to <b className="text-ink">{email}</b>.
              </>
            }
          />
          {error && <Alert>{error}</Alert>}
          <Field
            label="Reset code"
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="000000"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
            className="[&_input]:text-center [&_input]:font-mono [&_input]:text-xl [&_input]:tracking-[0.5em]"
            autoFocus
          />
          <Button type="submit" loading={loading} disabled={code.length < 6} className="mt-6">
            Continue
          </Button>
          <button
            type="button"
            onClick={async () => {
              setError(null);
              const r = await signIn.resetPasswordEmailCode.sendCode();
              if (r.error) setError(clerkError(r.error).message);
            }}
            className="mt-4 w-full text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
          >
            Send a new code
          </button>
        </form>
      )}

      {step === "password" && (
        <form key="password" onSubmit={submitPassword} className="rid-step" noValidate>
          {stepper}
          <Heading title="Choose a new password" subtitle="You'll be signed in right after." />
          <IdentityChip email={email} onEdit={() => setStep("email")} />
          {error && <Alert>{error}</Alert>}
          <PasswordField
            label="New password"
            name="new-password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldError.password}
            autoFocus
          />
          <ul className="mt-3 space-y-1">
            {checks.map((c) => (
              <li key={c.label} className={`flex items-center gap-2 text-sm ${c.ok ? "text-emerald-600" : "text-muted"}`}>
                <span
                  className={`grid h-4 w-4 place-items-center rounded-full ${c.ok ? "bg-emerald-500 text-white" : "border border-line"}`}
                >
                  {c.ok && <CheckIcon className="h-3 w-3" />}
                </span>
                {c.label}
              </li>
            ))}
          </ul>
          <PasswordField
            label="Confirm new password"
            name="confirm-password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={fieldError.confirm}
            className="mt-5"
          />
          <Button type="submit" loading={loading} className="mt-6">
            Reset password
          </Button>
        </form>
      )}
    </AuthShell>
  );
}

export default function ForgotPasswordPage() {
  return (
    <Suspense>
      <ForgotPassword />
    </Suspense>
  );
}
