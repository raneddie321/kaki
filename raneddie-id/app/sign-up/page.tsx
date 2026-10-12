"use client";

import Link from "next/link";
import { useRedirect } from "@/lib/use-redirect";
import { useRouter } from "next/navigation";
import { Suspense, useMemo, useState, type FormEvent } from "react";
import { useSignUp } from "@clerk/nextjs";
import {
  Alert,
  AuthShell,
  BackButton,
  Button,
  CheckIcon,
  Field,
  Heading,
  IdentityChip,
  MailIcon,
  PasswordField,
  SelectField,
  ShieldIcon,
  Spinner,
  Stepper,
} from "@/components/ui";
import {
  MIN_AGE,
  MONTHS,
  ageOn,
  clerkError,
  countries,
  daysInMonth,
  guessCountry,
  isEmail,
  navigateAfterAuth,
  passwordChecks,
  passwordStrength,
  usernameError,
} from "@/lib/auth-helpers";

type Step = "email" | "password" | "details" | "human" | "username" | "verify";
const STEPS: { id: Step; label: string }[] = [
  { id: "email", label: "Email" },
  { id: "password", label: "Password" },
  { id: "details", label: "About you" },
  { id: "human", label: "Security check" },
  { id: "username", label: "Username" },
];

function SignUp() {
  const { signUp, fetchStatus } = useSignUp();
  const router = useRouter();
  const { target, withRedirect } = useRedirect();

  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [month, setMonth] = useState("");
  const [day, setDay] = useState("");
  const [year, setYear] = useState("");
  const [country, setCountry] = useState("");
  const [human, setHuman] = useState<"idle" | "checking" | "ok">("idle");
  const [username, setUsername] = useState("");
  const [code, setCode] = useState("");

  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<Record<string, string | null>>({});
  const [humanFix, setHumanFix] = useState<Step | null>(null);
  const [busy, setBusy] = useState(false);
  const loading = busy || fetchStatus === "fetching";

  const countryList = useMemo(() => countries(), []);

  const stepIndex = STEPS.findIndex((s) => s.id === step);

  function go(next: Step) {
    setError(null);
    setFieldError({});
    setStep(next);
  }

  /** Editing anything the security check sent to Clerk means it has to run again. */
  function invalidateHuman() {
    if (human !== "idle") setHuman("idle");
  }

  // ---------------------------------------------------------------- step 1: email
  function submitEmail(e: FormEvent) {
    e.preventDefault();
    if (!isEmail(email)) return setFieldError({ email: "Enter a valid email address." });
    invalidateHuman();
    go("password");
  }

  // ---------------------------------------------------------------- step 2: password
  const checks = passwordChecks(password);
  const strength = passwordStrength(password);
  function submitPassword(e: FormEvent) {
    e.preventDefault();
    const errs: Record<string, string | null> = {};
    if (!checks.every((c) => c.ok)) errs.password = "Your password doesn't meet all the requirements yet.";
    if (confirm !== password) errs.confirm = "Passwords don't match.";
    if (errs.password || errs.confirm) return setFieldError(errs);
    invalidateHuman();
    setCountry((c) => c || guessCountry());
    go("details");
  }

  // ---------------------------------------------------------------- step 3: birth date + country
  const thisYear = new Date().getFullYear();
  const maxDay = daysInMonth(Number(year), Number(month));
  function submitDetails(e: FormEvent) {
    e.preventDefault();
    if (!month || !day || !year) return setFieldError({ birth: "Enter your full date of birth." });
    const birth = new Date(Number(year), Number(month), Number(day));
    if (birth > new Date()) return setFieldError({ birth: "That date is in the future." });
    if (ageOn(birth) < MIN_AGE)
      return setFieldError({ birth: `You must be at least ${MIN_AGE} years old to create a RanEddie ID.` });
    if (!country) return setFieldError({ country: "Choose your country." });
    invalidateHuman();
    go("human");
  }

  const birthDate = month && day && year ? `${year}-${String(Number(month) + 1).padStart(2, "0")}-${day.padStart(2, "0")}` : "";

  // ---------------------------------------------------------------- step 4: security check
  // Ticking the box starts the sign-up with Clerk. That is when Clerk's bot protection runs:
  // most people pass invisibly, suspicious sign-ups get a Cloudflare Turnstile challenge.
  async function runHumanCheck() {
    if (human !== "idle") return;
    setError(null);
    setHumanFix(null);
    setHuman("checking");
    const { error } = await signUp.password({
      emailAddress: email.trim(),
      password,
      unsafeMetadata: { birthDate, country },
    });
    if (error) {
      setHuman("idle");
      const { code, message, param } = clerkError(error);
      setError(message);
      if (code.startsWith("form_password") || param === "password") setHumanFix("password");
      else if (code === "form_identifier_exists" || param === "email_address") setHumanFix("email");
      return;
    }
    setHuman("ok");
  }

  // ---------------------------------------------------------------- step 5: username → create the account
  async function submitUsername(e: FormEvent) {
    e.preventDefault();
    const uErr = usernameError(username);
    if (uErr) return setFieldError({ username: uErr });
    setError(null);
    setFieldError({});
    setBusy(true);
    const { error } = await signUp.update({ username });
    if (error) {
      setBusy(false);
      const { code, message } = clerkError(error);
      if (code.startsWith("form_username") || code === "form_identifier_exists") setFieldError({ username: message });
      else setError(message);
      return;
    }
    if (signUp.status === "complete") {
      await signUp.finalize({ navigate: navigateAfterAuth(router, target("/?welcome=1")) });
      return;
    }
    if (signUp.unverifiedFields.includes("email_address")) {
      const sent = await signUp.verifications.sendEmailCode();
      setBusy(false);
      if (sent.error) return setError(clerkError(sent.error).message);
      go("verify");
      return;
    }
    await finishSignUp();
  }

  // ---------------------------------------------------------------- step 6: verify email
  async function submitCode(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    if (signUp.unverifiedFields.includes("email_address")) {
      const { error } = await signUp.verifications.verifyEmailCode({ code: code.trim() });
      if (error) {
        setBusy(false);
        return setError(clerkError(error).message);
      }
    }
    await finishSignUp();
  }

  /** The email is verified: fill in whatever else the Clerk settings require, then sign the player in. */
  async function finishSignUp() {
    if (signUp.status !== "complete" && signUp.missingFields.includes("legal_accepted")) {
      // The player accepted the Terms and Privacy Policy on the username step
      await signUp.update({ legalAccepted: true });
    }
    if (signUp.status !== "complete" && signUp.missingFields.includes("username")) {
      setBusy(false);
      setFieldError({ username: "Choose a username to finish." });
      setStep("username");
      return;
    }
    if (signUp.status === "complete") {
      const { error } = await signUp.finalize({ navigate: navigateAfterAuth(router, target("/?welcome=1")) });
      if (error) {
        setBusy(false);
        setError(clerkError(error).message);
      }
      return;
    }
    setBusy(false);
    const missing = [...signUp.missingFields, ...signUp.unverifiedFields].join(", ");
    setError(`Your email is verified, but your account still needs: ${missing || "unknown"}. Please contact support.`);
  }

  const stepper = stepIndex >= 0 && <Stepper step={stepIndex + 1} total={STEPS.length} label={STEPS[stepIndex].label} />;

  return (
    <AuthShell>
      {step === "email" && (
        <form key="email" onSubmit={submitEmail} className="rid-step" noValidate>
          {stepper}
          <Heading title="Create your RanEddie ID" subtitle="One account for all RanEddie games. Let's start with your email." />
          <Field
            label="Email"
            type="email"
            name="email"
            autoComplete="email"
            inputMode="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldError.email}
            autoFocus
          />
          <Button type="submit" className="mt-6">
            Next
          </Button>
          <p className="mt-6 text-center text-sm text-muted">
            Already have a RanEddie ID?{" "}
            <Link href={withRedirect("/sign-in")} className="font-semibold text-brand-600 hover:text-brand-700">
              Sign in
            </Link>
          </p>
        </form>
      )}

      {step === "password" && (
        <form key="password" onSubmit={submitPassword} className="rid-step" noValidate>
          <BackButton onClick={() => go("email")} />
          {stepper}
          <Heading title="Create a password" subtitle="Make it strong — it protects your progress and purchases." />
          <IdentityChip email={email} onEdit={() => go("email")} />
          <PasswordField
            label="Password"
            name="new-password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldError.password}
            autoFocus
          />
          {password && (
            <div className="mt-3">
              <div className="flex gap-1">
                {[1, 2, 3, 4].map((i) => (
                  <div
                    key={i}
                    className={`h-1 flex-1 rounded-full ${
                      i <= strength.score
                        ? ["", "bg-red-400", "bg-amber-400", "bg-brand-500", "bg-emerald-500"][strength.score]
                        : "bg-line"
                    }`}
                  />
                ))}
              </div>
              <p className="mt-1 text-xs font-semibold text-muted">{strength.label}</p>
            </div>
          )}
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
            label="Confirm password"
            name="confirm-password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={fieldError.confirm}
            className="mt-5"
          />
          <Button type="submit" className="mt-6">
            Next
          </Button>
        </form>
      )}

      {step === "details" && (
        <form key="details" onSubmit={submitDetails} className="rid-step" noValidate>
          <BackButton onClick={() => go("password")} />
          {stepper}
          <Heading title="Tell us about you" subtitle="We use this to keep RanEddie games safe and right for your age." />
          <fieldset>
            <legend className="mb-1.5 text-sm font-semibold text-ink">Date of birth</legend>
            <div className="grid grid-cols-[1.4fr_0.8fr_1fr] gap-2">
              <SelectField
                aria-label="Month"
                value={month}
                onChange={(e) => setMonth(e.target.value)}
                error={fieldError.birth}
              >
                <option value="">Month</option>
                {MONTHS.map((m, i) => (
                  <option key={m} value={i}>
                    {m}
                  </option>
                ))}
              </SelectField>
              <SelectField aria-label="Day" value={day} onChange={(e) => setDay(e.target.value)} error={fieldError.birth}>
                <option value="">Day</option>
                {Array.from({ length: maxDay }, (_, i) => (
                  <option key={i + 1} value={String(i + 1)}>
                    {i + 1}
                  </option>
                ))}
              </SelectField>
              <SelectField aria-label="Year" value={year} onChange={(e) => setYear(e.target.value)} error={fieldError.birth}>
                <option value="">Year</option>
                {Array.from({ length: 100 }, (_, i) => thisYear - i).map((y) => (
                  <option key={y} value={String(y)}>
                    {y}
                  </option>
                ))}
              </SelectField>
            </div>
            {fieldError.birth && <p className="mt-1.5 text-sm text-red-600">{fieldError.birth}</p>}
          </fieldset>
          <SelectField
            label="Country"
            value={country}
            onChange={(e) => setCountry(e.target.value)}
            error={fieldError.country}
            className="mt-5"
          >
            <option value="">Choose your country</option>
            {countryList.map((c) => (
              <option key={c.code} value={c.code}>
                {c.flag} {c.name}
              </option>
            ))}
          </SelectField>
          {fieldError.country && <p className="mt-1.5 text-sm text-red-600">{fieldError.country}</p>}
          <p className="mt-4 text-xs leading-relaxed text-muted">
            Your date of birth isn&apos;t shown to other players.
          </p>
          <Button type="submit" className="mt-6">
            Next
          </Button>
        </form>
      )}

      {step === "human" && (
        <div key="human" className="rid-step">
          <BackButton onClick={() => go("details")} />
          {stepper}
          <Heading title="Quick security check" subtitle="Confirm you're a real player, not a bot." />
          {error && (
            <Alert>
              {error}
              {humanFix && (
                <button
                  type="button"
                  onClick={() => go(humanFix)}
                  className="ml-1 font-semibold underline underline-offset-2"
                >
                  {humanFix === "email" ? "Change email" : "Change password"}
                </button>
              )}
            </Alert>
          )}
          <button
            type="button"
            onClick={runHumanCheck}
            disabled={human !== "idle"}
            aria-pressed={human === "ok"}
            className={`flex w-full items-center gap-4 rounded-2xl border-2 px-5 py-4 text-left transition ${
              human === "ok" ? "border-emerald-400 bg-emerald-50" : "border-line bg-field hover:border-brand-400"
            }`}
          >
            <span
              className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg border-2 ${
                human === "ok" ? "rid-pop border-emerald-500 bg-emerald-500 text-white" : "border-muted/40 bg-white"
              }`}
            >
              {human === "checking" && <Spinner className="h-5 w-5 text-brand-500" />}
              {human === "ok" && <CheckIcon className="h-5 w-5" />}
            </span>
            <span className="flex-1">
              <span className="block text-[15px] font-semibold text-ink">
                {human === "ok" ? "You're verified" : human === "checking" ? "Verifying…" : "I'm not a robot"}
              </span>
              <span className="block text-xs text-muted">Protected by Clerk bot protection</span>
            </span>
            <ShieldIcon className={`h-7 w-7 ${human === "ok" ? "text-emerald-500" : "text-brand-500"}`} />
          </button>
          <Button type="button" className="mt-6" disabled={human !== "ok"} onClick={() => go("username")}>
            Next
          </Button>
        </div>
      )}

      {step === "username" && (
        <form key="username" onSubmit={submitUsername} className="rid-step" noValidate>
          <BackButton onClick={() => go("human")} />
          {stepper}
          <Heading title="Pick a username" subtitle="This is how other players will see you in RanEddie games." />
          {error && <Alert>{error}</Alert>}
          <Field
            label="Username"
            name="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="SnakeMaster"
            maxLength={20}
            value={username}
            onChange={(e) => setUsername(e.target.value.replace(/\s/g, ""))}
            error={fieldError.username}
            hint="4–20 characters: letters, numbers, _ and -"
            autoFocus
          />
          <Button type="submit" loading={loading} className="mt-6">
            Create account
          </Button>
          <p className="mt-4 text-center text-xs leading-relaxed text-muted">
            By creating an account you agree to the{" "}
            <Link href="/terms" className="underline underline-offset-2">
              Terms
            </Link>{" "}
            and{" "}
            <Link href="/privacy" className="underline underline-offset-2">
              Privacy Policy
            </Link>
            .
          </p>
        </form>
      )}

      {step === "verify" && (
        <form key="verify" onSubmit={submitCode} className="rid-step" noValidate>
          <div className="mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-brand-600">
            <MailIcon className="h-6 w-6" />
          </div>
          <Heading
            title="Verify your email"
            subtitle={
              <>
                Last step! We sent a 6-digit code to <b className="text-ink">{email}</b>.
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
          <Button type="submit" loading={loading} disabled={code.length < 6} className="mt-6">
            Verify and finish
          </Button>
          <button
            type="button"
            onClick={async () => {
              setError(null);
              const r = await signUp.verifications.sendEmailCode();
              if (r.error) setError(clerkError(r.error).message);
            }}
            className="mt-4 w-full text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
          >
            Send a new code
          </button>
        </form>
      )}
    </AuthShell>
  );
}

export default function SignUpPage() {
  return (
    <Suspense>
      <SignUp />
    </Suspense>
  );
}
