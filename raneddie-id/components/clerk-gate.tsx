"use client";

import { useEffect, useState, type ReactNode } from "react";
import { useUser } from "@clerk/nextjs";
import { Button, Heading, Spinner } from "@/components/ui";

/** How long to wait for Clerk's browser script before telling the player something is wrong. */
const LOAD_TIMEOUT_MS = 12_000;

export type ClerkStatus = "loading" | "ready" | "failed";

/**
 * Tracks whether Clerk has finished loading in the browser.
 *
 * Clerk never loads when its script is blocked, the network is down, or the page is opened on a
 * host the Clerk production instance doesn't serve (for example a *.vercel.app preview URL). Before
 * this hook existed those pages showed a spinner forever; now they report "failed" after a timeout.
 */
export function useClerkStatus(timeoutMs = LOAD_TIMEOUT_MS): ClerkStatus {
  const { isLoaded } = useUser();
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    if (isLoaded) return;
    const timer = window.setTimeout(() => setTimedOut(true), timeoutMs);
    return () => window.clearTimeout(timer);
  }, [isLoaded, timeoutMs]);

  if (isLoaded) return "ready";
  return timedOut ? "failed" : "loading";
}

/** Shown instead of the page when Clerk could not load. */
export function ClerkLoadFailed() {
  return (
    <div className="rid-step text-center" role="alert">
      <Heading
        title="We couldn't load RanEddie ID"
        subtitle="The sign-in service didn't respond. Check your connection, turn off any content blocker for this page, and try again."
      />
      <Button type="button" onClick={() => window.location.reload()}>
        Try again
      </Button>
      <p className="mt-4 text-sm text-muted">
        Still stuck? Open{" "}
        <a href="https://accounts.raneddie.com" className="font-semibold text-brand-600 hover:text-brand-700">
          accounts.raneddie.com
        </a>{" "}
        directly.
      </p>
    </div>
  );
}

/** Renders `children` once Clerk is ready; a spinner while it loads; an error with a retry if it never does. */
export function ClerkGate({ children }: { children: ReactNode }) {
  const status = useClerkStatus();
  if (status === "ready") return <>{children}</>;
  if (status === "failed") return <ClerkLoadFailed />;
  return (
    <div className="flex justify-center py-16 text-brand-500" role="status" aria-label="Loading">
      <Spinner className="h-8 w-8" />
    </div>
  );
}
