import { Check, X, Sparkles, Zap, Building2, ArrowLeft, Send } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

const PLANS = [
  {
    name: "Plus",
    price: "$9",
    period: "/month",
    description: "For individual developers getting started",
    color: "200 80% 55%",
    features: [
      { text: "5 projects", included: true },
      { text: "AI chat (GPT-4o-mini)", included: true },
      { text: "Basic code generation", included: true },
      { text: "File management", included: true },
      { text: "Extensions marketplace", included: true },
      { text: "Terminal access", included: true },
      { text: "Community support", included: true },
      { text: "Custom themes", included: false },
      { text: "Advanced AI models", included: false },
      { text: "Team collaboration", included: false },
    ],
    cta: "Get Plus",
    popular: false,
  },
  {
    name: "Pro",
    price: "$29",
    period: "/month",
    description: "For professional developers and power users",
    color: "270 60% 60%",
    features: [
      { text: "Unlimited projects", included: true },
      { text: "AI chat (GPT-4, Claude, Gemini)", included: true },
      { text: "Advanced code generation", included: true },
      { text: "File management", included: true },
      { text: "Extensions marketplace", included: true },
      { text: "Terminal access", included: true },
      { text: "Priority support", included: true },
      { text: "Custom themes", included: true },
      { text: "Advanced AI models", included: true },
      { text: "Git integration", included: true },
    ],
    cta: "Get Pro",
    popular: true,
  },
  {
    name: "Enterprise",
    price: "Custom",
    period: "",
    description: "For teams and organizations at scale",
    color: "30 90% 55%",
    features: [
      { text: "Everything in Pro", included: true },
      { text: "Team collaboration", included: true },
      { text: "SSO / SAML", included: true },
      { text: "Admin dashboard", included: true },
      { text: "Custom AI model hosting", included: true },
      { text: "On-premise deployment", included: true },
      { text: "Dedicated support", included: true },
      { text: "SLA guarantee", included: true },
      { text: "Audit logs", included: true },
      { text: "Custom integrations", included: true },
    ],
    cta: "Contact Sales",
    popular: false,
    isEnterprise: true,
  },
];

export default function Pricing() {
  const navigate = useNavigate();
  const [showContact, setShowContact] = useState(false);
  const [contactForm, setContactForm] = useState({ name: "", email: "", company: "", message: "" });
  const [submitted, setSubmitted] = useState(false);

  const handleContactSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b border-border bg-card/50 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center gap-3">
          <button onClick={() => navigate("/")} className="p-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center">
              <Sparkles className="w-4 h-4 text-primary-foreground" />
            </div>
            <span className="font-bold text-foreground">PrimeCODE</span>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="py-20 text-center px-6">
        <h1 className="text-4xl md:text-5xl font-bold text-foreground mb-4 tracking-tight">
          Choose Your Plan
        </h1>
        <p className="text-lg text-muted-foreground max-w-xl mx-auto">
          Start building for free, upgrade when you need more power.
        </p>
      </section>

      {/* Plans */}
      <section className="max-w-5xl mx-auto px-6 pb-20">
        <div className="grid md:grid-cols-3 gap-6">
          {PLANS.map((plan) => (
            <div
              key={plan.name}
              className={`relative rounded-2xl border p-6 flex flex-col transition-all hover:-translate-y-1 hover:shadow-xl ${
                plan.popular
                  ? "border-primary bg-card shadow-lg shadow-primary/10 scale-[1.02]"
                  : "border-border bg-card hover:border-primary/30"
              }`}
            >
              {plan.popular && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-4 py-1 rounded-full bg-primary text-primary-foreground text-xs font-bold">
                  Most Popular
                </div>
              )}

              <div className="mb-6">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center mb-3" style={{ background: `hsl(${plan.color} / 0.15)` }}>
                  {plan.name === "Plus" && <Zap className="w-5 h-5" style={{ color: `hsl(${plan.color})` }} />}
                  {plan.name === "Pro" && <Sparkles className="w-5 h-5" style={{ color: `hsl(${plan.color})` }} />}
                  {plan.name === "Enterprise" && <Building2 className="w-5 h-5" style={{ color: `hsl(${plan.color})` }} />}
                </div>
                <h3 className="text-xl font-bold text-foreground">{plan.name}</h3>
                <p className="text-sm text-muted-foreground mt-1">{plan.description}</p>
                <div className="mt-4">
                  <span className="text-4xl font-bold text-foreground">{plan.price}</span>
                  <span className="text-muted-foreground">{plan.period}</span>
                </div>
              </div>

              <ul className="space-y-2.5 mb-8 flex-1">
                {plan.features.map((f, i) => (
                  <li key={i} className="flex items-center gap-2 text-sm">
                    {f.included ? (
                      <Check className="w-4 h-4 text-green-500 flex-shrink-0" />
                    ) : (
                      <X className="w-4 h-4 text-muted-foreground/40 flex-shrink-0" />
                    )}
                    <span className={f.included ? "text-foreground" : "text-muted-foreground/50"}>{f.text}</span>
                  </li>
                ))}
              </ul>

              <button
                onClick={() => plan.isEnterprise ? setShowContact(true) : null}
                className={`w-full py-3 rounded-xl font-medium text-sm transition-all ${
                  plan.popular
                    ? "bg-primary text-primary-foreground hover:bg-primary/90 shadow-lg shadow-primary/25"
                    : "bg-secondary text-foreground hover:bg-secondary/80"
                }`}
              >
                {plan.cta}
              </button>
            </div>
          ))}
        </div>
      </section>

      {/* Enterprise Contact Form */}
      {showContact && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowContact(false)}>
          <div className="bg-card border border-border rounded-2xl w-full max-w-md p-6 shadow-2xl" onClick={e => e.stopPropagation()}>
            {submitted ? (
              <div className="text-center py-8">
                <div className="w-16 h-16 rounded-full bg-green-500/20 flex items-center justify-center mx-auto mb-4">
                  <Check className="w-8 h-8 text-green-500" />
                </div>
                <h3 className="text-lg font-bold text-foreground mb-2">Thank you!</h3>
                <p className="text-sm text-muted-foreground">We'll get back to you within 24 hours.</p>
                <button onClick={() => { setShowContact(false); setSubmitted(false); }}
                  className="mt-6 px-6 py-2 rounded-xl bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors">
                  Close
                </button>
              </div>
            ) : (
              <>
                <h2 className="text-lg font-bold text-foreground mb-1">Contact Sales</h2>
                <p className="text-sm text-muted-foreground mb-6">Tell us about your team's needs.</p>
                <form onSubmit={handleContactSubmit} className="space-y-3">
                  <input value={contactForm.name} onChange={e => setContactForm(p => ({ ...p, name: e.target.value }))} required
                    placeholder="Full Name" className="w-full bg-secondary border border-border rounded-xl px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50" />
                  <input value={contactForm.email} onChange={e => setContactForm(p => ({ ...p, email: e.target.value }))} required type="email"
                    placeholder="Work Email" className="w-full bg-secondary border border-border rounded-xl px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50" />
                  <input value={contactForm.company} onChange={e => setContactForm(p => ({ ...p, company: e.target.value }))} required
                    placeholder="Company" className="w-full bg-secondary border border-border rounded-xl px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50" />
                  <textarea value={contactForm.message} onChange={e => setContactForm(p => ({ ...p, message: e.target.value }))}
                    placeholder="Tell us about your needs..." rows={3}
                    className="w-full bg-secondary border border-border rounded-xl px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50 resize-none" />
                  <div className="flex justify-end gap-2 pt-2">
                    <button type="button" onClick={() => setShowContact(false)} className="px-4 py-2 rounded-xl text-sm text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors">
                      Cancel
                    </button>
                    <button type="submit" className="flex items-center gap-2 px-5 py-2 rounded-xl bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-all">
                      <Send className="w-3.5 h-3.5" /> Send Message
                    </button>
                  </div>
                </form>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
