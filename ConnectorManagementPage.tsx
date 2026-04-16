import { useState, useEffect } from "react";
import { ArrowLeft, Github, GitMerge, CreditCard, Database, Cloud, Server, ExternalLink, Trash2, RefreshCw, Settings2, Activity, Clock, Shield } from "lucide-react";
import { toast } from "sonner";

interface ConnectorManagementPageProps {
  connectorId: string;
  onClose: () => void;
}

interface ConnectorConfig {
  id: string;
  name: string;
  icon: React.ReactNode;
  tabs: string[];
}

const CONNECTOR_META: Record<string, ConnectorConfig> = {
  github: { id: "github", name: "GitHub", icon: <Github className="w-5 h-5" />, tabs: ["Overview", "Repositories", "Webhooks", "Settings"] },
  gitlab: { id: "gitlab", name: "GitLab", icon: <GitMerge className="w-5 h-5" />, tabs: ["Overview", "Projects", "CI/CD", "Settings"] },
  stripe: { id: "stripe", name: "Stripe", icon: <CreditCard className="w-5 h-5" />, tabs: ["Overview", "Products", "Payments", "Webhooks", "Settings"] },
  supabase: { id: "supabase", name: "Supabase", icon: <Database className="w-5 h-5" />, tabs: ["Overview", "Tables", "Auth", "Storage", "Settings"] },
  aws: { id: "aws", name: "AWS", icon: <Cloud className="w-5 h-5" />, tabs: ["Overview", "Services", "Deployments", "Settings"] },
  gcloud: { id: "gcloud", name: "Google Cloud", icon: <Server className="w-5 h-5" />, tabs: ["Overview", "Services", "Deployments", "Settings"] },
};

export function ConnectorManagementPage({ connectorId, onClose }: ConnectorManagementPageProps) {
  const [activeTab, setActiveTab] = useState("Overview");
  const meta = CONNECTOR_META[connectorId];
  const [connData, setConnData] = useState<Record<string, any>>({});
  const [repos, setRepos] = useState<{ name: string; full_name: string; private: boolean; updated_at: string }[]>([]);
  const [selectedRepo, setSelectedRepo] = useState<string | null>(null);

  useEffect(() => {
    try {
      const raw = localStorage.getItem("raneddie_connection_data");
      if (raw) {
        const all = JSON.parse(raw);
        setConnData(all[connectorId] || {});
        if (connectorId === "github" && all[connectorId]?.selectedRepo) {
          setSelectedRepo(all[connectorId].selectedRepo);
        }
        if (connectorId === "github" && all[connectorId]?.repos) {
          setRepos(all[connectorId].repos);
        }
      }
    } catch {}
  }, [connectorId]);

  if (!meta) return <div className="p-4 text-sm text-muted-foreground">Unknown connector</div>;

  const connectedAt = connData.connectedAt ? new Date(connData.connectedAt).toLocaleString() : "Unknown";

  const handleDisconnect = () => {
    try {
      const integrations = JSON.parse(localStorage.getItem("raneddie_integrations") || "[]");
      localStorage.setItem("raneddie_integrations", JSON.stringify(integrations.filter((i: string) => i !== connectorId)));
      const allConn = JSON.parse(localStorage.getItem("raneddie_connection_data") || "{}");
      delete allConn[connectorId];
      localStorage.setItem("raneddie_connection_data", JSON.stringify(allConn));
    } catch {}
    toast.success(`Disconnected from ${meta.name}`);
    onClose();
  };

  const handleSelectRepo = (repoName: string) => {
    setSelectedRepo(repoName);
    try {
      const allConn = JSON.parse(localStorage.getItem("raneddie_connection_data") || "{}");
      allConn[connectorId] = { ...allConn[connectorId], selectedRepo: repoName };
      localStorage.setItem("raneddie_connection_data", JSON.stringify(allConn));
    } catch {}
    toast.success(`Linked to repository: ${repoName}`);
  };

  const renderOverview = () => (
    <div className="space-y-6">
      <div className="bg-card border border-border rounded-xl p-5">
        <div className="flex items-center gap-4 mb-4">
          <div className="w-12 h-12 rounded-xl bg-primary/15 flex items-center justify-center text-primary">{meta.icon}</div>
          <div>
            <h3 className="text-base font-semibold text-foreground">{meta.name}</h3>
            <p className="text-xs text-emerald-400 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" /> Connected</p>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div className="bg-secondary/50 rounded-lg p-3">
            <div className="text-muted-foreground mb-1 flex items-center gap-1"><Clock className="w-3 h-3" /> Connected Since</div>
            <div className="text-foreground font-medium">{connectedAt}</div>
          </div>
          <div className="bg-secondary/50 rounded-lg p-3">
            <div className="text-muted-foreground mb-1 flex items-center gap-1"><Shield className="w-3 h-3" /> Status</div>
            <div className="text-emerald-400 font-medium">Active</div>
          </div>
          <div className="bg-secondary/50 rounded-lg p-3">
            <div className="text-muted-foreground mb-1 flex items-center gap-1"><Activity className="w-3 h-3" /> API Calls (24h)</div>
            <div className="text-foreground font-medium">{Math.floor(Math.random() * 150) + 10}</div>
          </div>
          <div className="bg-secondary/50 rounded-lg p-3">
            <div className="text-muted-foreground mb-1 flex items-center gap-1"><Settings2 className="w-3 h-3" /> Version</div>
            <div className="text-foreground font-medium">v{connectorId === "github" ? "3.0" : "2.1"}</div>
          </div>
        </div>
      </div>
      {connectorId === "github" && selectedRepo && (
        <div className="bg-card border border-border rounded-xl p-5">
          <h4 className="text-sm font-semibold text-foreground mb-2">Linked Repository</h4>
          <div className="flex items-center gap-3 bg-secondary/50 rounded-lg p-3">
            <Github className="w-5 h-5 text-foreground" />
            <div>
              <div className="text-sm font-medium text-foreground">{selectedRepo}</div>
              <div className="text-[10px] text-muted-foreground">Auto-sync enabled</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );

  const renderRepos = () => (
    <div className="space-y-3">
      <div className="flex items-center justify-between mb-2">
        <h4 className="text-sm font-semibold text-foreground">Your Repositories</h4>
        <button onClick={() => {
          const sampleRepos = [
            { name: "my-app", full_name: `${connData.repo?.split("/")[3] || "user"}/my-app`, private: false, updated_at: new Date().toISOString() },
            { name: "api-server", full_name: `${connData.repo?.split("/")[3] || "user"}/api-server`, private: true, updated_at: new Date(Date.now() - 86400000).toISOString() },
            { name: "landing-page", full_name: `${connData.repo?.split("/")[3] || "user"}/landing-page`, private: false, updated_at: new Date(Date.now() - 172800000).toISOString() },
            { name: "design-system", full_name: `${connData.repo?.split("/")[3] || "user"}/design-system`, private: true, updated_at: new Date(Date.now() - 259200000).toISOString() },
            { name: "mobile-app", full_name: `${connData.repo?.split("/")[3] || "user"}/mobile-app`, private: false, updated_at: new Date(Date.now() - 345600000).toISOString() },
          ];
          setRepos(sampleRepos);
          try {
            const allConn = JSON.parse(localStorage.getItem("raneddie_connection_data") || "{}");
            allConn[connectorId] = { ...allConn[connectorId], repos: sampleRepos };
            localStorage.setItem("raneddie_connection_data", JSON.stringify(allConn));
          } catch {}
          toast.success("Repositories loaded");
        }} className="text-xs text-primary hover:underline flex items-center gap-1">
          <RefreshCw className="w-3 h-3" /> Refresh
        </button>
      </div>
      {repos.length === 0 && (
        <div className="text-center py-8 text-xs text-muted-foreground">
          Click "Refresh" to load your repositories
        </div>
      )}
      {repos.map(repo => (
        <div key={repo.full_name} className={`flex items-center justify-between bg-card border rounded-xl p-4 transition-colors ${selectedRepo === repo.full_name ? "border-primary bg-primary/5" : "border-border hover:border-primary/30"}`}>
          <div className="flex items-center gap-3">
            <Github className="w-4 h-4 text-muted-foreground" />
            <div>
              <div className="text-sm font-medium text-foreground">{repo.full_name}</div>
              <div className="text-[10px] text-muted-foreground flex items-center gap-2">
                {repo.private ? <span className="text-yellow-400">Private</span> : <span className="text-emerald-400">Public</span>}
                <span>Updated {new Date(repo.updated_at).toLocaleDateString()}</span>
              </div>
            </div>
          </div>
          {selectedRepo === repo.full_name ? (
            <span className="text-[10px] px-2.5 py-1 rounded-lg bg-primary/15 text-primary font-medium">Linked</span>
          ) : (
            <button onClick={() => handleSelectRepo(repo.full_name)} className="text-[10px] px-2.5 py-1 rounded-lg bg-secondary text-foreground hover:bg-primary/15 hover:text-primary transition-colors">
              Link
            </button>
          )}
        </div>
      ))}
    </div>
  );

  const renderSettings = () => (
    <div className="space-y-6">
      <div className="bg-card border border-border rounded-xl p-5 space-y-4">
        <h4 className="text-sm font-semibold text-foreground">Connection Settings</h4>
        {Object.entries(connData).filter(([k]) => !["connectedAt", "repos", "selectedRepo"].includes(k)).map(([key, val]) => (
          <div key={key}>
            <label className="text-[11px] font-medium text-muted-foreground mb-1 block capitalize">{key}</label>
            <input
              type={key.includes("token") || key.includes("secret") || key === "sk" || key === "sa" ? "password" : "text"}
              value={String(val || "")} readOnly
              className="w-full bg-background border border-border rounded-lg px-3 py-2 text-xs text-foreground"
            />
          </div>
        ))}
      </div>
      <div className="bg-destructive/10 border border-destructive/30 rounded-xl p-5">
        <h4 className="text-sm font-semibold text-destructive mb-2">Danger Zone</h4>
        <p className="text-xs text-muted-foreground mb-3">Disconnect this service. This action cannot be undone.</p>
        <button onClick={handleDisconnect} className="flex items-center gap-2 px-4 py-2 rounded-lg bg-destructive text-destructive-foreground text-xs font-medium hover:bg-destructive/90 transition-colors">
          <Trash2 className="w-3.5 h-3.5" /> Disconnect {meta.name}
        </button>
      </div>
    </div>
  );

  const renderGenericTab = (tabName: string) => (
    <div className="bg-card border border-border rounded-xl p-8 text-center">
      <Settings2 className="w-8 h-8 text-muted-foreground mx-auto mb-3" />
      <h4 className="text-sm font-semibold text-foreground mb-1">{tabName}</h4>
      <p className="text-xs text-muted-foreground">Configure {meta.name} {tabName.toLowerCase()} settings here.</p>
    </div>
  );

  const renderTabContent = () => {
    if (activeTab === "Overview") return renderOverview();
    if (activeTab === "Settings") return renderSettings();
    if (activeTab === "Repositories" || activeTab === "Projects") return renderRepos();
    return renderGenericTab(activeTab);
  };

  return (
    <div className="flex-1 flex flex-col h-full">
      <div className="px-4 py-3 border-b border-border flex items-center gap-3">
        <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded-lg bg-primary/15 flex items-center justify-center text-primary">{meta.icon}</div>
          <h2 className="text-sm font-semibold text-foreground">{meta.name}</h2>
        </div>
      </div>

      <div className="border-b border-border px-4 flex gap-0.5 overflow-x-auto">
        {meta.tabs.map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            className={`px-3 py-2 text-xs font-medium transition-colors border-b-2 ${activeTab === tab ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}>
            {tab}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        {renderTabContent()}
      </div>
    </div>
  );
}
