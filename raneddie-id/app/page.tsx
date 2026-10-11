"use client";

import { useMemo } from "react";
import { useClerk, useUser } from "@clerk/nextjs";
import { Alert, AuthShell, Button, Divider, Heading, LinkButton, Spinner } from "@/components/ui";
import { MONTHS, countries } from "@/lib/auth-helpers";

export default function HomePage() {
  const { isLoaded, isSignedIn, user } = useUser();
  const { signOut } = useClerk();
  const countryList = useMemo(() => countries(), []);

  if (!isLoaded) {
    return (
      <AuthShell>
        <div className="flex justify-center py-16 text-brand-500">
          <Spinner className="h-8 w-8" />
        </div>
      </AuthShell>
    );
  }

  if (!isSignedIn) {
    return (
      <AuthShell>
        <div className="rid-step text-center">
          <Heading
            title="One account for every RanEddie game"
            subtitle="Save your progress, play on any device and keep your purchases safe with a RanEddie ID."
          />
          <div className="mt-2 space-y-3">
            <LinkButton href="/sign-up">Create a RanEddie ID</LinkButton>
            <LinkButton href="/sign-in" variant="ghost">
              Sign in
            </LinkButton>
          </div>
        </div>
      </AuthShell>
    );
  }

  const meta = (user.unsafeMetadata ?? {}) as { birthDate?: string; country?: string };
  const country = countryList.find((c) => c.code === meta.country);
  const birth = meta.birthDate ? meta.birthDate.split("-").map(Number) : null;
  const params = typeof window !== "undefined" ? new URLSearchParams(window.location.search) : null;

  return (
    <AuthShell>
      <div className="rid-step">
        {params?.get("welcome") && <Alert tone="success">Your RanEddie ID is ready. Welcome!</Alert>}
        {params?.get("reset") && <Alert tone="success">Your password was changed.</Alert>}
        <div className="flex items-center gap-4">
          <div className="grid h-16 w-16 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-brand-600 to-cyan-brand font-display text-2xl font-bold uppercase text-white shadow-lg shadow-brand-500/30">
            {(user.username ?? user.primaryEmailAddress?.emailAddress ?? "?").slice(0, 1)}
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-brand-600">RanEddie ID</p>
            <h1 className="truncate font-display text-2xl font-bold text-ink">{user.username ?? "Player"}</h1>
            <p className="truncate text-sm text-muted">{user.primaryEmailAddress?.emailAddress}</p>
          </div>
        </div>
        <Divider />
        <dl className="grid grid-cols-2 gap-4 text-sm">
          <Info label="Country" value={country ? `${country.flag} ${country.name}` : "—"} />
          <Info label="Date of birth" value={birth ? `${MONTHS[birth[1] - 1]} ${birth[2]}, ${birth[0]}` : "—"} />
          <Info label="Member since" value={user.createdAt ? user.createdAt.toLocaleDateString("en-US", { month: "long", year: "numeric" }) : "—"} />
          <Info label="Player ID" value={<span className="font-mono text-xs">{user.id.replace("user_", "").slice(0, 10)}</span>} />
        </dl>
        <Divider />
        <Button type="button" variant="ghost" onClick={() => signOut({ redirectUrl: "/" })}>
          Sign out
        </Button>
      </div>
    </AuthShell>
  );
}

function Info({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="rounded-xl bg-field px-4 py-3">
      <dt className="text-xs font-semibold uppercase tracking-wider text-muted">{label}</dt>
      <dd className="mt-1 font-semibold text-ink">{value}</dd>
    </div>
  );
}
