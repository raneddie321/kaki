import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Reset your password",
  description: "Reset the password for your RanEddie ID.",
  alternates: { canonical: "/forgot-password" },
  openGraph: { title: "Reset your password | RanEddie ID", description: "Reset the password for your RanEddie ID.", url: "/forgot-password" },
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
