import type { Metadata } from "next";
import { ClerkProvider } from "@clerk/nextjs";
import "@fontsource-variable/inter";
import "@fontsource-variable/sora";
import "./globals.css";
import { ClerkHealth } from "@/components/clerk-health";

export const metadata: Metadata = {
  title: "RanEddie ID",
  description: "One account for every RanEddie game.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full">
        <ClerkProvider
          signInUrl="/sign-in"
          signUpUrl="/sign-up"
          signInFallbackRedirectUrl="/"
          signUpFallbackRedirectUrl="/"
        >
          <ClerkHealth>{children}</ClerkHealth>
        </ClerkProvider>
      </body>
    </html>
  );
}
