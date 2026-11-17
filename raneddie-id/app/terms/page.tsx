import type { Metadata } from "next";
import { AuthShell, Heading } from "@/components/ui";

export const metadata: Metadata = {
  title: "Terms of Use",
  description: "The terms that apply to your RanEddie ID account.",
  alternates: { canonical: "/terms" },
  openGraph: { title: "Terms of Use | RanEddie ID", description: "The terms that apply to your RanEddie ID account.", url: "/terms" },
};

export default function TermsPage() {
  return (
    <AuthShell wide>
      <Heading title="Terms of Use" subtitle="The basics of using a RanEddie ID." />
      <div className="space-y-4 text-[15px] leading-relaxed text-muted">
        <p>A RanEddie ID is personal. Keep your password secret and don&apos;t share your account.</p>
        <p>Choose a username that isn&apos;t offensive and doesn&apos;t pretend to be someone else. We may change or remove usernames that break this rule.</p>
        <p>We may suspend accounts used for cheating, abuse or fraud.</p>
      </div>
    </AuthShell>
  );
}
