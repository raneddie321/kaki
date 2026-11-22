"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useRef } from "react";
import { useAuth } from "@clerk/nextjs";
import { safeRedirect } from "./auth-helpers";

/**
 * ?redirect_url= support: where to go after signing in, and links that keep it while moving
 * between sign-in, sign-up and forgot-password. Needs a <Suspense> boundary above it.
 */
export function useRedirect() {
  const params = useSearchParams();
  const redirect = safeRedirect(params.get("redirect_url"));
  return {
    target: (fallback = "/") => redirect ?? fallback,
    withRedirect: (path: string) =>
      redirect ? path + (path.includes("?") ? "&" : "?") + "redirect_url=" + encodeURIComponent(redirect) : path,
  };
}

/**
 * Sign-in, sign-up and password-reset pages: someone who is already signed in (for example coming back from
 * a product that hadn't noticed the session yet) goes straight to where they were heading instead of seeing a
 * form that would only answer "you're already signed in". Checked once, when Clerk has loaded, so it never
 * fights with the navigation a form does after finishing.
 */
export function useLeaveIfSignedIn(fallback = "/") {
  const { isLoaded, isSignedIn } = useAuth();
  const { target } = useRedirect();
  const checked = useRef(false);
  useEffect(() => {
    if (!isLoaded || checked.current) return;
    checked.current = true;
    if (!isSignedIn) return;
    window.location.replace(target(fallback));
  }, [isLoaded, isSignedIn, target, fallback]);
  // Signed in: the page is on its way out (either here or by the form that just finished).
  return Boolean(isLoaded && isSignedIn);
}
