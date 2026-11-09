"use client";

import Link from "next/link";
import { useRedirect } from "@/lib/use-redirect";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { useSignIn } from "@clerk/nextjs";
import { Alert, AuthShell, BackButton, Button, Field, Heading, IdentityChip, MailIcon, PasswordField } from "@/components/ui";
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
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const loading = busy || fetchStatus === "fetching";

  async function finish() {
    await signIn.finalize({ navigate: navigateAfterAuth(router, target()) });
  }

  /** Runs a step with the button spinner on, and always turns it off again (even if Clerk throws). */
  async function run(step: () => Promise<void>) {
    setError(null);
    setBusy(true);
    try {
      await step();
    } catch (cause) {
      setError(clerkError(cause).message);
    } finally {
      setBusy(false);
    }
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
        // New device: Clerk asks for a code sent to the player's email
        const canEmail = signIn.supportedSecondFactors?.some((f) => f.strategy === "email_code");
        if (canEmail) {
          const sent = await signIn.mfa.sendEmailCode();
          if (sent.error) return setError(clerkError(sent.error).message);
          setStep("verify");
          return;
        }
        const canTotp = signIn.supportedSecondFactors?.some((f) => f.strategy === "totp");
        if (canTotp) {
          setStep("totp");
          return;
        }
      }
      setError("This account needs another sign-in step that RanEddie ID doesn't support yet.");
    });
  }

  // Step 3 (only on new devices): email code
  function submitCode(e: FormEvent) {
    e.preventDefault();
    return run(async () => {
      const { error } = await signIn.mfa.verifyEmailCode({ code: code.trim() });
      if (error) return setError(clerkError(error).message);
      if (signIn.status === "complete") return finish();
      setError("We couldn't finish signing you in. Please try again.");
    });
  }

  // Step 3 (accounts with an authenticator app): TOTP code
  function submitTotp(e: FormEvent) {
    e.preventDefault();
    return run(async () => {
      const { error } = await signIn.mfa.verifyTOTP({ code: code.trim() });
      if (error) return setError(clerkError(error).message);
      if (signIn.status === "complete") return finish();
      setError("We couldn't finish signing you in. Please try again.");
    });
  }

  function restart() {
    setStep("email");
    setPassword("");
    setCode("");
    setError(null);
    signIn.reset();
  }

  return (
    <AuthShell>
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
            onClick={() =>
              run(async () => {
                const r = await signIn.mfa.sendEmailCode();
                if (r.error) setError(clerkError(r.error).message);
              })
            }
            className="mt-4 w-full text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
          >
            Send a new code
          </button>
        </form>
      )}

      {step === "totp" && (
        <form key="totp" onSubmit={submitTotp} className="rid-step" noValidate>
          <BackButton onClick={restart} label="Start over" />
          <Heading title="Two-step verification" subtitle="Enter the 6-digit code from your authenticator app." />
          {error && <Alert>{error}</Alert>}
          <Field
            label="Authenticator code"
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
        </form>
      )}
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
