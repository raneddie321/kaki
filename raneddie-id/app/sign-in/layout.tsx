import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Sign in to your RanEddie ID to use Fizz Browser, Snake Brawl and every RanEddie product.",
  alternates: { canonical: "/sign-in" },
  openGraph: { title: "Sign in | RanEddie ID", description: "Sign in to your RanEddie ID to use Fizz Browser, Snake Brawl and every RanEddie product.", url: "/sign-in" },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
