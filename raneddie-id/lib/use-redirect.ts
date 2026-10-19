"use client";

import { useSearchParams } from "next/navigation";
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
