import type { Metadata } from "next";
import { ClerkProvider } from "@clerk/nextjs";
import "@fontsource-variable/inter";
import "@fontsource-variable/sora";
import "./globals.css";
import { ClerkHealth } from "@/components/clerk-health";

const DESCRIPTION =
  "RanEddie ID is one secure account for every RanEddie product: Fizz Browser, Snake Brawl, RanEddie apps and more. Sign in once, manage your profile, security and privacy.";

export const metadata: Metadata = {
  metadataBase: new URL("https://id.raneddie.com"),
  title: { default: "RanEddie ID: One account for everything RanEddie", template: "%s | RanEddie ID" },
  description: DESCRIPTION,
  applicationName: "RanEddie ID",
  keywords: ["RanEddie ID", "RanEddie account", "RanEddie sign in", "RanEddie login", "RanEddie", "רנאדי"],
  authors: [{ name: "RanEddie", url: "https://raneddie.com" }],
  publisher: "RanEddie",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    url: "https://id.raneddie.com",
    siteName: "RanEddie ID",
    title: "RanEddie ID: One account for everything RanEddie",
    description: DESCRIPTION,
    locale: "en_US",
    images: [{ url: "/icon.png", alt: "RanEddie ID" }],
  },
  twitter: { card: "summary", site: "@RanEddiegk", title: "RanEddie ID", description: DESCRIPTION, images: ["/icon.png"] },
  robots: { index: true, follow: true, googleBot: { index: true, follow: true, "max-image-preview": "large", "max-snippet": -1 } },
};

const JSON_LD = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "WebSite",
      "@id": "https://id.raneddie.com/#website",
      name: "RanEddie ID",
      url: "https://id.raneddie.com",
      description: DESCRIPTION,
      publisher: { "@id": "https://raneddie.com/#organization" },
    },
    {
      "@type": "WebApplication",
      name: "RanEddie ID",
      url: "https://id.raneddie.com",
      applicationCategory: "SecurityApplication",
      operatingSystem: "Web",
      offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
      provider: { "@type": "Organization", "@id": "https://raneddie.com/#organization", name: "RanEddie", url: "https://raneddie.com" },
    },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full">
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(JSON_LD) }} />
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
