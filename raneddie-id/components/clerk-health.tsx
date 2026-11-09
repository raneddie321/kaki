"use client";

import { useEffect, useState, type ReactNode } from "react";
import { ClerkFailed, ClerkLoading } from "@clerk/nextjs";

/** Where RanEddie ID is meant to be used. The production Clerk instance only works on raneddie.com. */
const OFFICIAL_URL = "https://id.raneddie.com";
const SLOW_AFTER_MS = 12_000;

/**
 * Without this, a Clerk load failure (blocked script, network error, or opening the app on a host the
 * production Clerk instance doesn't serve, such as *.vercel.app) leaves every page on its spinner forever.
 */
export function ClerkHealth({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <ClerkFailed>
        <Unavailable />
      </ClerkFailed>
      <ClerkLoading>
        <SlowLoadWatch />
      </ClerkLoading>
    </>
  );
}

function SlowLoadWatch() {
  const [slow, setSlow] = useState(false);
  useEffect(() => {
    const t = window.setTimeout(() => setSlow(true), SLOW_AFTER_MS);
    return () => window.clearTimeout(t);
  }, []);
  return slow ? <Unavailable slow /> : null;
}

function Unavailable({ slow = false }: { slow?: boolean }) {
  // Only rendered in the browser (after Clerk failed or timed out), so reading the location is safe.
  const [{ wrongHost, officialHref }] = useState(() => {
    if (typeof window === "undefined") return { wrongHost: false, officialHref: OFFICIAL_URL };
    const { hostname, pathname, search } = window.location;
    return {
      wrongHost: hostname !== "localhost" && hostname !== "raneddie.com" && !hostname.endsWith(".raneddie.com"),
      officialHref: OFFICIAL_URL + pathname + search,
    };
  });

  return (
    <div
      role="alertdialog"
      aria-live="assertive"
      aria-labelledby="rid-unavailable-title"
      className="fixed inset-0 z-[100] grid place-items-center bg-[#05070f]/85 px-4 backdrop-blur-sm"
    >
      <div className="w-full max-w-md rounded-3xl bg-white p-7 text-center shadow-2xl">
        <h2 id="rid-unavailable-title" className="font-display text-2xl font-bold text-ink">
          {slow ? "This is taking longer than usual" : "We couldn't reach RanEddie ID"}
        </h2>
        <p className="mt-2 text-[15px] leading-relaxed text-muted">
          {wrongHost
            ? "RanEddie ID only works on its official address. Continue there to sign in."
            : "Check your connection, turn off any content blocker for this site, and try again."}
        </p>
        <div className="mt-6 grid gap-3">
          {wrongHost ? (
            <a
              href={officialHref}
              className="flex h-12 items-center justify-center rounded-xl bg-brand-600 font-semibold text-white hover:bg-brand-700"
            >
              Go to id.raneddie.com
            </a>
          ) : (
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="h-12 rounded-xl bg-brand-600 font-semibold text-white hover:bg-brand-700"
            >
              Try again
            </button>
          )}
          <a href="https://raneddie.com" className="text-sm font-semibold text-brand-600 hover:text-brand-700">
            Back to raneddie.com
          </a>
        </div>
      </div>
    </div>
  );
}
