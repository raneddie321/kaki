"use client";

import Link from "next/link";
import { useRedirect } from "@/lib/use-redirect";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { useSignIn } from "@clerk/nextjs";
import { Alert, AuthShell, BackButton, Button, Field, Heading, IdentityChip, MailIcon, PasswordField, ShieldIcon } from "@/components/ui";
import { ClerkGate } from "@/components/clerk-gate";
import { clerkError, isEmail, navigateAfterAuth } from "@/lib/auth-helpers";

type Step = "email" | "password" | "verify" | "totp";

function SignIn() {
  const { signIn, fetchStatus } = useSignIn();
  const router = useRouter();
  const { target, withRedirect } = useRedirect();
  const [step, setStep] = useState<Step>("email");
  const params = useSearchParams();
  const [email, setEmail] = useState(() => params.get("email") ?? "");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [useBackupCode, setUseBackupCode] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const loading = busy || fetchStatus === "fetching";

  /**
   * Runs one step of the form. The spinner is always cleared afterwards — even when Clerk or the
   * network throws — except when the player is being sent on to the next page.
   */
  async function run(task: () => Promise<"navigating" | void>) {
    setError(null);
    setBusy(true);
    let navigating = false;
    try {
      navigating = (await task()) === "navigating";
    } catch (cause) {
      setError(clerkError(cause).message);
    } finally {
      if (!navigating) setBusy(false);
    }
  }

  async function finish(): Promise<"navigating" | void> {
    const { error } = await signIn.finalize({ navigate: navigateAfterAuth(router, target()) });
    if (error) {
      setError(clerkError(error).message);
      return;
    }
    return "navigating";
  }

  // Step 1: email. Starting the sign-in here tells the player right away if there's no account.
  function submitEmail(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!isEmail(email)) return setError("Enter a valid email address.");
    return run(async () => {
      const { error } = await signIn.create({ identifier: email.trim() });
      if (error) return setError(clerkError(error).message);
      setStep("password");
    });
  }

  // Step 2: password
  function submitPassword(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!password) return setError("Enter your password.");
    return run(async () => {
      const { error } = await signIn.password({ password });
      if (error) return setError(clerkError(error).message);
      if (signIn.status === "complete") return finish();
      if (signIn.status === "needs_client_trust" || signIn.status === "needs_second_factor") {
        const factors = signIn.supportedSecondFactors ?? [];
        // The player turned on an authenticator app: ask for that code
        if (factors.some((f) => f.strategy === "totp")) {
          setStep("totp");
          return;
        }
        // New device: Clerk asks for a code sent to the player's email
        if (factors.some((f) => f.strategy === "email_code")) {
          const sent = await signIn.mfa.sendEmailCode();
          if (sent.error) return setError(clerkError(sent.error).message);
          setStep("verify");
          return;
        }
      }
      setError("This account needs another sign-in step that RanEddie ID doesn't support yet.");
    });
  }

  // Step 3a (only on new devices): email code
  function submitCode(e: FormEvent) {
    e.preventDefault();
    return run(async () => {
      const { error } = await signIn.mfa.verifyEmailCode({ code: code.trim() });
      if (error) return setError(clerkError(error).message);
      if (signIn.status === "complete") return finish();
      setError("We couldn't finish signing you in. Please try again.");
    });
  }

  // Step 3b (only with an authenticator app): the app's code, or a backup code
  function submitTotp(e: FormEvent) {
    e.preventDefault();
    return run(async () => {
      const value = code.trim();
      const { error } = useBackupCode
        ? await signIn.mfa.verifyBackupCode({ code: value })
        : await signIn.mfa.verifyTOTP({ code: value });
      if (error) return setError(clerkError(error).message);
      if (signIn.status === "complete") return finish();
      setError("We couldn't finish signing you in. Please try again.");
    });
  }

  function restart() {
    setStep("email");
    setPassword("");
    setCode("");
    setUseBackupCode(false);
    setError(null);
    signIn.reset();
  }

  async function resendCode() {
    setError(null);
    try {
      const r = await signIn.mfa.sendEmailCode();
      if (r.error) setError(clerkError(r.error).message);
    } catch (cause) {
      setError(clerkError(cause).message);
    }
  }

  return (
    <AuthShell>
      <ClerkGate>
        {step === "email" && (
          <form key="email" onSubmit={submitEmail} className="rid-step" noValidate>
            <Heading title="Sign in" subtitle="Use your RanEddie ID to continue to your games." />
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
              autoFocus
            />
            <Button type="submit" loading={loading} className="mt-6">
              Next
            </Button>
            <p className="mt-6 text-center text-sm text-muted">
              New here?{" "}
              <Link href={withRedirect("/sign-up")} className="font-semibold text-brand-600 hover:text-brand-700">
                Create a RanEddie ID
              </Link>
            </p>
          </form>
        )}

        {step === "password" && (
          <form key="password" onSubmit={submitPassword} className="rid-step" noValidate>
            <BackButton onClick={restart} />
            <Heading title="Enter your password" />
            <IdentityChip email={email} onEdit={restart} />
            {error && <Alert>{error}</Alert>}
            <PasswordField
              label="Password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoFocus
            />
            <div className="mt-2 text-right">
              <Link
                href={withRedirect(`/forgot-password?email=${encodeURIComponent(email)}`)}
                className="text-sm font-semibold text-brand-600 hover:text-brand-700"
              >
                Forgot password?
              </Link>
            </div>
            <Button type="submit" loading={loading} className="mt-5">
              Sign in
            </Button>
          </form>
        )}

        {step === "verify" && (
          <form key="verify" onSubmit={submitCode} className="rid-step" noValidate>
            <BackButton onClick={restart} label="Start over" />
            <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600">
              <MailIcon className="h-6 w-6" />
            </div>
            <Heading
              title="Check your email"
              subtitle={
                <>
                  You&apos;re signing in on a new device. We sent a 6-digit code to <b className="text-ink">{email}</b>.
                </>
              }
            />
            {error && <Alert>{error}</Alert>}
            <Field
              label="Verification code"
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
            <Button type="submit" loading={loading} className="mt-6" disabled={code.length < 6}>
              Verify and sign in
            </Button>
            <button
              type="button"
              onClick={resendCode}
              className="mt-4 w-full text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
            >
              Send a new code
            </button>
          </form>
        )}

        {step === "totp" && (
          <form key="totp" onSubmit={submitTotp} className="rid-step" noValidate>
            <BackButton onClick={restart} label="Start over" />
            <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600">
              <ShieldIcon className="h-6 w-6" />
            </div>
            <Heading
              title={useBackupCode ? "Use a backup code" : "Two-step verification"}
              subtitle={
                useBackupCode
                  ? "Enter one of the backup codes you saved when you turned on two-step verification."
                  : "Open your authenticator app and enter the 6-digit code for RanEddie ID."
              }
            />
            {error && <Alert>{error}</Alert>}
            <Field
              key={useBackupCode ? "backup" : "totp"}
              label={useBackupCode ? "Backup code" : "Authenticator code"}
              name="code"
              inputMode={useBackupCode ? "text" : "numeric"}
              autoComplete="one-time-code"
              placeholder={useBackupCode ? "Backup code" : "000000"}
              maxLength={useBackupCode ? 24 : 6}
              value={code}
              onChange={(e) => setCode(useBackupCode ? e.target.value.trim() : e.target.value.replace(/\D/g, ""))}
              className={useBackupCode ? undefined : "[&_input]:text-center [&_input]:font-mono [&_input]:text-xl [&_input]:tracking-[0.5em]"}
              autoFocus
            />
            <Button type="submit" loading={loading} className="mt-6" disabled={code.length < 6}>
              Verify and sign in
            </Button>
            <button
              type="button"
              onClick={() => {
                setUseBackupCode((v) => !v);
                setCode("");
                setError(null);
              }}
              className="mt-4 w-full text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
            >
              {useBackupCode ? "Use my authenticator app instead" : "Use a backup code instead"}
            </button>
          </form>
        )}
      </ClerkGate>
    </AuthShell>
  );
}

export default function SignInPage() {
  return (
    <Suspense>
      <SignIn />
    </Suspense>
  );
}
