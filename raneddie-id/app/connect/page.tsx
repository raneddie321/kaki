"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { useClerk, useUser } from "@clerk/nextjs";
import { Alert, AuthShell, Button, CheckIcon, Heading, LinkButton, ShieldIcon, Spinner } from "@/components/ui";

/** The games we can connect, and the link that sends the player back into each one. */
const GAMES: Record<string, { name: string; returnUrl: string }> = {
  snakebrawl: { name: "Snake Brawl", returnUrl: "snakebrawl://raneddie" },
  fizzapp: { name: "Fizz App", returnUrl: "fizzapp://raneddie" },
};

/**
 * Opened by a game ("Connect RanEddie ID"). The player signs in or creates an account, approves,
 * and is sent back to the game with a one-time link code. The game trades that code, together with
 * a secret only it knows (PKCE verifier), for its game token.
 */
function Connect() {
  const params = useSearchParams();
  const { isLoaded, isSignedIn, user } = useUser();
  const { signOut } = useClerk();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [returnLink, setReturnLink] = useState<string | null>(null);

  const game = GAMES[params.get("game") ?? ""];
  const state = params.get("state") ?? "";
  const challenge = params.get("ch") ?? "";
  const valid = !!game && /^[A-Za-z0-9_-]{8,64}$/.test(state) && /^[A-Za-z0-9_-]{43,128}$/.test(challenge);
  const here = `/connect?${params.toString()}`;

  async function approve() {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch("/api/game/code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ challenge }),
      });
      const data = (await res.json()) as { code?: string };
      if (!res.ok || !data.code) throw new Error();
      const link = `${game.returnUrl}?state=${encodeURIComponent(state)}&code=${encodeURIComponent(data.code)}`;
      setReturnLink(link);
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- an app link, not a page here
      window.location.href = link;
    } catch {
      setError("Something went wrong. Please try again.");
    }
    setBusy(false);
  }

  if (!valid) {
    return (
      <AuthShell>
        <Heading title="Open this from the game" subtitle="To connect a game, tap “Connect RanEddie ID” inside the game." />
        <LinkButton href="/" variant="ghost">
          Go to RanEddie ID
        </LinkButton>
      </AuthShell>
    );
  }

  if (!isLoaded) {
    return (
      <AuthShell>
        <div className="flex justify-center py-16 text-brand-500">
          <Spinner className="h-8 w-8" />
        </div>
      </AuthShell>
    );
  }

  if (returnLink) {
    return (
      <AuthShell>
        <div className="rid-step text-center">
          <div className="rid-pop mx-auto mb-5 grid h-16 w-16 place-items-center rounded-full bg-emerald-500 text-white">
            <CheckIcon className="h-8 w-8" />
          </div>
          <Heading title="Connected!" subtitle={`Go back to ${game.name} to finish. Your progress will be saved to your RanEddie ID.`} />
          <a
            href={returnLink}
            className="rid-btn-primary inline-flex h-12 w-full items-center justify-center rounded-xl text-[15px] font-semibold text-white"
          >
            Return to {game.name}
          </a>
        </div>
      </AuthShell>
    );
  }

  const redirect = encodeURIComponent(here);
  return (
    <AuthShell>
      <div className="rid-step">
        <div className="mb-5 flex items-center gap-3 rounded-2xl bg-gradient-to-r from-brand-50 to-cyan-50 p-4">
          <div className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-white text-brand-600 shadow-sm">
            <ShieldIcon className="h-7 w-7" />
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-brand-600">Connect a game</p>
            <p className="font-display text-lg font-bold text-ink">{game.name}</p>
          </div>
        </div>

        {!isSignedIn ? (
          <>
            <Heading
              title="Save your progress"
              subtitle={`Sign in or create a RanEddie ID to keep your ${game.name} progress safe and play on any device.`}
            />
            <div className="space-y-3">
              <LinkButton href={`/sign-up?redirect_url=${redirect}`}>Create a RanEddie ID</LinkButton>
              <LinkButton href={`/sign-in?redirect_url=${redirect}`} variant="ghost">
                I already have one — sign in
              </LinkButton>
            </div>
          </>
        ) : (
          <>
            <Heading
              title={`Connect ${game.name}?`}
              subtitle={`${game.name} will save your progress to this RanEddie ID, so you can continue on any device.`}
            />
            {error && <Alert>{error}</Alert>}
            <div className="mb-5 flex items-center gap-3 rounded-xl border border-line bg-field px-4 py-3">
              <div className="grid h-10 w-10 place-items-center rounded-full bg-gradient-to-br from-brand-500 to-cyan-brand font-bold uppercase text-white">
                {(user.username ?? "?").slice(0, 1)}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold text-ink">{user.username}</p>
                <p className="truncate text-sm text-muted">{user.primaryEmailAddress?.emailAddress}</p>
              </div>
            </div>
            <Button type="button" loading={busy} onClick={approve}>
              Connect
            </Button>
            <button
              type="button"
              onClick={() => signOut({ redirectUrl: here })}
              className="mt-4 w-full text-center text-sm font-semibold text-brand-600 hover:text-brand-700"
            >
              Not you? Use another account
            </button>
          </>
        )}
      </div>
    </AuthShell>
  );
}

export default function ConnectPage() {
  return (
    <Suspense>
      <Connect />
    </Suspense>
  );
}
