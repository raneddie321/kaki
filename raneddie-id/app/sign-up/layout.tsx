import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Create your RanEddie ID",
  description: "Create a free RanEddie ID: one account for every RanEddie product.",
  alternates: { canonical: "/sign-up" },
  openGraph: { title: "Create your RanEddie ID | RanEddie ID", description: "Create a free RanEddie ID: one account for every RanEddie product.", url: "/sign-up" },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
