import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Connect",
  description: "Connect a RanEddie product to your RanEddie ID.",
  alternates: { canonical: "/connect" },
  openGraph: { title: "Connect | RanEddie ID", description: "Connect a RanEddie product to your RanEddie ID.", url: "/connect" },
  robots: { index: false, follow: false },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
