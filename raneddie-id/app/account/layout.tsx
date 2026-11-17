import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Account Center",
  description: "Manage your RanEddie ID profile, security, privacy and subscriptions.",
  alternates: { canonical: "/account" },
  openGraph: { title: "Account Center | RanEddie ID", description: "Manage your RanEddie ID profile, security, privacy and subscriptions.", url: "/account" },
  robots: { index: false, follow: false },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
