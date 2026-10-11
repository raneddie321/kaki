import { AuthShell, Heading } from "@/components/ui";

export default function PrivacyPage() {
  return (
    <AuthShell wide>
      <Heading title="Privacy Policy" subtitle="How RanEddie ID handles your information." />
      <div className="space-y-4 text-[15px] leading-relaxed text-muted">
        <p>
          RanEddie ID stores your email address, username, password (encrypted — nobody can read it, including RanEddie),
          date of birth and country. Accounts are managed by our authentication provider, Clerk.
        </p>
        <p>
          We use this information only to run your account, keep RanEddie games safe and age-appropriate, and contact
          you about your account (for example, password reset codes). We don&apos;t sell your information.
        </p>
        <p>You must be at least 13 years old to create a RanEddie ID.</p>
        <p>To delete your RanEddie ID and its data, contact us from the email on your account.</p>
      </div>
    </AuthShell>
  );
}
