import { useState, useRef, useEffect } from "react";
import { Github, GitMerge, CreditCard, Database, Cloud, Server, ChevronDown, ExternalLink, X, Check, Loader2 } from "lucide-react";
import { toast } from "sonner";

interface Integration {
  id: string;
  name: string;
  icon: React.ReactNode;
  description: string;
  color: string;
  fields: { label: string; placeholder: string; key: string }[];
}

const INTEGRATIONS: Integration[] = [
  {
    id: "github", name: "GitHub", icon: <Github className="w-4 h-4" />, description: "Connect your repository for version control and CI/CD",
    color: "210 10% 95%",
    fields: [{ label: "Repository URL", placeholder: "https://github.com/user/repo", key: "repo" }, { label: "Personal Access Token", placeholder: "ghp_xxxx", key: "token" }],
  },
  {
    id: "gitlab", name: "GitLab", icon: <GitMerge className="w-4 h-4" />, description: "Connect to GitLab for source control and pipelines",
    color: "15 80% 55%",
    fields: [{ label: "Project URL", placeholder: "https://gitlab.com/user/project", key: "repo" }, { label: "Access Token", placeholder: "glpat-xxxx", key: "token" }],
  },
  {
    id: "stripe", name: "Stripe", icon: <CreditCard className="w-4 h-4" />, description: "Accept payments and manage subscriptions",
    color: "250 60% 60%",
    fields: [{ label: "Publishable Key", placeholder: "pk_live_xxxx", key: "pk" }, { label: "Secret Key", placeholder: "sk_live_xxxx", key: "sk" }],
  },
  {
    id: "supabase", name: "Supabase", icon: <Database className="w-4 h-4" />, description: "Backend as a service with database and auth",
    color: "153 60% 50%",
    fields: [{ label: "Project URL", placeholder: "https://xxxx.supabase.co", key: "url" }, { label: "Anon Key", placeholder: "eyJhbGci...", key: "anon" }],
  },
  {
    id: "aws", name: "AWS", icon: <Cloud className="w-4 h-4" />, description: "Deploy to Amazon Web Services cloud infrastructure",
    color: "30 90% 55%",
    fields: [{ label: "Access Key ID", placeholder: "AKIA...", key: "access" }, { label: "Secret Access Key", placeholder: "xxxx", key: "secret" }, { label: "Region", placeholder: "us-east-1", key: "region" }],
  },
  {
    id: "gcloud", name: "Google Cloud", icon: <Server className="w-4 h-4" />, description: "Deploy to Google Cloud Platform",
    color: "210 80% 55%",
    fields: [{ label: "Project ID", placeholder: "my-project-123", key: "project" }, { label: "Service Account JSON", placeholder: "Paste JSON key", key: "sa" }],
  },
];

interface TopMenuBarProps {
  projectName: string;
  onBack: () => void;
  onManageConnector?: (connectorId: string) => void;
}

export function TopMenuBar({ projectName, onBack, onManageConnector }: TopMenuBarProps) {
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [connectModal, setConnectModal] = useState<Integration | null>(null);
  const [connected, setConnected] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem("raneddie_integrations");
      return raw ? new Set(JSON.parse(raw)) : new Set();
    } catch { return new Set(); }
  });
  const [formValues, setFormValues] = useState<Record<string, string>>({});
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setOpenMenu(null);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const [connecting, setConnecting] = useState(false);

  const handleConnect = async (integration: Integration) => {
    // Validate fields
    const missingFields = integration.fields.filter(f => !formValues[f.key]?.trim());
    if (missingFields.length > 0) {
      toast.error(`Please fill in: ${missingFields.map(f => f.label).join(", ")}`);
      return;
    }

    setConnecting(true);

    // Store connection data
    try {
      const connData = JSON.parse(localStorage.getItem("raneddie_connection_data") || "{}");
      connData[integration.id] = { ...formValues, connectedAt: Date.now() };
      localStorage.setItem("raneddie_connection_data", JSON.stringify(connData));
    } catch {}

    // Simulate connection verification
    await new Promise(r => setTimeout(r, 1500));

    const newConnected = new Set(connected);
    newConnected.add(integration.id);
    setConnected(newConnected);
    localStorage.setItem("raneddie_integrations", JSON.stringify([...newConnected]));
    setConnectModal(null);
    setFormValues({});
    setConnecting(false);
    toast.success(`Successfully connected to ${integration.name}!`);
  };

  const handleDisconnect = (id: string) => {
    const integ = INTEGRATIONS.find(i => i.id === id);
    const newConnected = new Set(connected);
    newConnected.delete(id);
    setConnected(newConnected);
    localStorage.setItem("raneddie_integrations", JSON.stringify([...newConnected]));
    // Remove connection data
    try {
      const connData = JSON.parse(localStorage.getItem("raneddie_connection_data") || "{}");
      delete connData[id];
      localStorage.setItem("raneddie_connection_data", JSON.stringify(connData));
    } catch {}
    toast.info(`Disconnected from ${integ?.name || id}`);
  };

  const menus = [
    { id: "file", label: "File", items: [
      { label: "New File", shortcut: "Ctrl+N" },
      { label: "New Folder" },
      { label: "separator" },
      { label: "Save", shortcut: "Ctrl+S" },
      { label: "Save All", shortcut: "Ctrl+Shift+S" },
      { label: "separator" },
      { label: "Close Editor", shortcut: "Ctrl+W" },
    ]},
    { id: "edit", label: "Edit", items: [
      { label: "Undo", shortcut: "Ctrl+Z" },
      { label: "Redo", shortcut: "Ctrl+Shift+Z" },
      { label: "separator" },
      { label: "Cut", shortcut: "Ctrl+X" },
      { label: "Copy", shortcut: "Ctrl+C" },
      { label: "Paste", shortcut: "Ctrl+V" },
      { label: "separator" },
      { label: "Find", shortcut: "Ctrl+F" },
      { label: "Replace", shortcut: "Ctrl+H" },
    ]},
    { id: "view", label: "View", items: [
      { label: "Command Palette", shortcut: "Ctrl+Shift+P" },
      { label: "separator" },
      { label: "Explorer", shortcut: "Ctrl+Shift+E" },
      { label: "Search", shortcut: "Ctrl+Shift+F" },
      { label: "separator" },
      { label: "Terminal", shortcut: "Ctrl+`" },
      { label: "Toggle Sidebar", shortcut: "Ctrl+B" },
    ]},
    { id: "terminal", label: "Terminal", items: [
      { label: "New Terminal" },
      { label: "Split Terminal" },
    ]},
    { id: "help", label: "Help", items: [
      { label: "Documentation" },
      { label: "Keyboard Shortcuts" },
      { label: "About" },
    ]},
  ];

  return (
    <>
      <div ref={menuRef} className="h-9 bg-sidebar border-b border-border flex items-center px-2 gap-0 flex-shrink-0 select-none">
        {/* Logo / back */}
        <button onClick={onBack} className="flex items-center gap-1.5 px-2 py-1 rounded text-xs font-semibold text-primary hover:bg-secondary/60 transition-colors mr-1">
          <div className="w-4 h-4 rounded bg-primary/20 flex items-center justify-center text-[10px] font-bold text-primary">R</div>
          {projectName}
        </button>

        {/* Menus */}
        {menus.map(menu => (
          <div key={menu.id} className="relative">
            <button
              onClick={() => setOpenMenu(openMenu === menu.id ? null : menu.id)}
              onMouseEnter={() => openMenu && setOpenMenu(menu.id)}
              className={`px-2.5 py-1 text-[11px] rounded transition-colors ${openMenu === menu.id ? "bg-secondary text-foreground" : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"}`}
            >
              {menu.label}
            </button>
            {openMenu === menu.id && (
              <div className="absolute top-full left-0 mt-0.5 w-56 bg-popover border border-border rounded-lg shadow-2xl z-50 py-1">
                {menu.items.map((item, i) => item.label === "separator" ? (
                  <div key={i} className="my-1 h-px bg-border mx-2" />
                ) : (
                  <button key={i} onClick={() => setOpenMenu(null)} className="w-full flex items-center justify-between px-3 py-1.5 text-xs text-foreground hover:bg-secondary transition-colors">
                    <span>{item.label}</span>
                    {item.shortcut && <span className="text-[10px] text-muted-foreground">{item.shortcut}</span>}
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}

        {/* Spacer */}
        <div className="flex-1" />

        {/* Integrations dropdown */}
        <div className="relative">
          <button
            onClick={() => setOpenMenu(openMenu === "integrations" ? null : "integrations")}
            className={`flex items-center gap-1 px-2.5 py-1 text-[11px] rounded transition-colors ${openMenu === "integrations" ? "bg-secondary text-foreground" : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"}`}
          >
            Connectors
            <ChevronDown className="w-3 h-3" />
          </button>
          {openMenu === "integrations" && (
            <div className="absolute top-full right-0 mt-0.5 w-72 bg-popover border border-border rounded-lg shadow-2xl z-50 py-1">
              <div className="px-3 py-2 text-[10px] font-bold text-muted-foreground uppercase tracking-wider border-b border-border">
                Connect Services
              </div>
              {INTEGRATIONS.map(integ => (
                <div key={integ.id} className="flex items-center justify-between px-3 py-2 hover:bg-secondary transition-colors">
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div className="w-7 h-7 rounded-lg bg-secondary flex items-center justify-center flex-shrink-0 text-foreground">
                      {integ.icon}
                    </div>
                    <div className="min-w-0">
                      <div className="text-xs font-medium text-foreground">{integ.name}</div>
                      <div className="text-[10px] text-muted-foreground truncate">{integ.description}</div>
                    </div>
                  </div>
                  {connected.has(integ.id) ? (
                    <div className="flex items-center gap-1 flex-shrink-0 ml-2">
                      <button onClick={() => { onManageConnector?.(integ.id); setOpenMenu(null); }} className="text-[10px] px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-400 hover:bg-primary/15 hover:text-primary transition-colors">
                        Manage
                      </button>
                    </div>
                  ) : (
                    <button onClick={() => { setConnectModal(integ); setOpenMenu(null); setFormValues({}); }} className="text-[10px] px-2 py-0.5 rounded bg-primary/15 text-primary hover:bg-primary/25 transition-colors flex-shrink-0 ml-2">
                      Connect
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Connect Modal */}
      {connectModal && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center" onClick={() => setConnectModal(null)}>
          <div className="bg-card border border-border rounded-xl w-[420px] shadow-2xl" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between px-5 py-4 border-b border-border">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-secondary flex items-center justify-center text-foreground">{connectModal.icon}</div>
                <div>
                  <h3 className="text-sm font-semibold text-foreground">Connect {connectModal.name}</h3>
                  <p className="text-[11px] text-muted-foreground">{connectModal.description}</p>
                </div>
              </div>
              <button onClick={() => setConnectModal(null)} className="p-1 rounded text-muted-foreground hover:text-foreground hover:bg-secondary"><X className="w-4 h-4" /></button>
            </div>
            <div className="p-5 space-y-4">
              {connectModal.fields.map(field => (
                <div key={field.key}>
                  <label className="text-[11px] font-medium text-muted-foreground mb-1.5 block">{field.label}</label>
                  <input
                    type={field.key.includes("token") || field.key.includes("secret") || field.key === "sk" || field.key === "sa" ? "password" : "text"}
                    placeholder={field.placeholder}
                    value={formValues[field.key] || ""}
                    onChange={e => setFormValues(prev => ({ ...prev, [field.key]: e.target.value }))}
                    className="w-full bg-background border border-border rounded-lg px-3 py-2 text-xs text-foreground placeholder:text-muted-foreground/50 outline-none focus:ring-1 focus:ring-primary"
                  />
                </div>
              ))}
            </div>
            <div className="px-5 pb-5 flex items-center justify-between">
              <a href="#" className="text-[11px] text-primary hover:underline flex items-center gap-1">
                <ExternalLink className="w-3 h-3" /> View docs
              </a>
              <div className="flex gap-2">
                <button onClick={() => setConnectModal(null)} disabled={connecting} className="px-3 py-1.5 text-xs rounded-lg text-muted-foreground hover:bg-secondary transition-colors disabled:opacity-50">Cancel</button>
                <button onClick={() => handleConnect(connectModal)} disabled={connecting} className="px-4 py-1.5 text-xs rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors font-medium disabled:opacity-50 flex items-center gap-1.5">
                  {connecting ? <><Loader2 className="w-3 h-3 animate-spin" /> Connecting...</> : "Connect"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
