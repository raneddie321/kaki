import { useState, useEffect, useCallback, useRef } from "react";
import { Power, PowerOff, Settings, BookOpen, Terminal, RefreshCw, ExternalLink } from "lucide-react";
import { toast } from "sonner";

interface ExtensionSidebarPageProps {
  extId: string;
  extInfo: {
    id: string;
    name: string;
    icon: string;
    description?: string;
    version?: string;
    author?: string;
    repository?: string;
    url?: string;
    category?: string;
    tags?: string[];
  } | null;
}

const RUNTIME_KEY = "raneddie_extension_runtime";

interface ExtRuntime {
  active: boolean;
  logs: string[];
  prefs: { autoActivate: boolean; notifications: boolean; verbose: boolean };
}

function loadRuntime(id: string, name: string): ExtRuntime {
  try {
    const raw = localStorage.getItem(RUNTIME_KEY);
    if (raw) {
      const all = JSON.parse(raw);
      if (all[id]) return all[id];
    }
  } catch {}
  return {
    active: true,
    logs: [`[${name}] Extension ready`, `[${name}] Sidebar opened`, `[${name}] Workspace connected`],
    prefs: { autoActivate: true, notifications: true, verbose: false },
  };
}

function saveRuntime(id: string, state: ExtRuntime) {
  try {
    const raw = localStorage.getItem(RUNTIME_KEY);
    const all = raw ? JSON.parse(raw) : {};
    all[id] = state;
    localStorage.setItem(RUNTIME_KEY, JSON.stringify(all));
  } catch {}
}

export function ExtensionSidebarPage({ extId, extInfo }: ExtensionSidebarPageProps) {
  const name = extInfo?.name || extId;
  const [rt, setRt] = useState<ExtRuntime>(() => loadRuntime(extId, name));
  const [showSettings, setShowSettings] = useState(false);
  const logsEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setRt(loadRuntime(extId, name));
    setShowSettings(false);
  }, [extId, name]);

  useEffect(() => { saveRuntime(extId, rt); }, [extId, rt]);
  useEffect(() => { logsEndRef.current?.scrollIntoView({ behavior: "smooth" }); }, [rt.logs]);

  const pushLog = (msg: string) => setRt(prev => ({ ...prev, logs: [...prev.logs, msg] }));

  const toggleActive = () => {
    if (rt.active) {
      setRt(prev => ({ ...prev, active: false, logs: [...prev.logs, `[${name}] Deactivated`] }));
      if (rt.prefs.notifications) toast.info(`${name} deactivated`);
    } else {
      setRt(prev => ({
        ...prev,
        active: true,
        logs: [
          `[${name}] Loading extension...`,
          `[${name}] Resolving dependencies...`,
          `[${name}] Extension activated successfully`,
          `[${name}] Watching workspace for changes...`,
        ],
      }));
      if (rt.prefs.notifications) toast.success(`${name} activated!`);
    }
  };

  const handleReload = () => {
    pushLog(`[${name}] Reloading...`);
    setTimeout(() => pushLog(`[${name}] Reload complete`), 400);
    if (rt.prefs.notifications) toast.success(`${name} reloaded`);
  };

  const setPref = (key: keyof ExtRuntime["prefs"], val: boolean) =>
    setRt(prev => ({ ...prev, prefs: { ...prev.prefs, [key]: val } }));

  return (
    <div className="flex flex-col h-full">
      <div className="px-4 py-3 border-b border-border flex items-center gap-2">
        {extInfo?.icon && (
          <img src={extInfo.icon} alt="" className="w-6 h-6 rounded object-contain"
            onError={e => { (e.target as HTMLImageElement).style.display = "none"; }} />
        )}
        <div className="min-w-0 flex-1">
          <span className="text-xs font-semibold text-foreground truncate block">{name}</span>
          <span className="text-[10px] text-muted-foreground">{extId}</span>
        </div>
        <span className={`w-2 h-2 rounded-full flex-shrink-0 ${rt.active ? "bg-primary" : "bg-muted-foreground/40"}`} />
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {/* Info card */}
        {extInfo?.description && (
          <div className="rounded-lg border border-border bg-card p-3 space-y-2">
            <p className="text-xs leading-relaxed text-muted-foreground">{extInfo.description}</p>
            <div className="grid grid-cols-2 gap-1.5 text-[10px]">
              {extInfo.version && <span className="text-muted-foreground">Version <span className="text-foreground font-medium">{extInfo.version}</span></span>}
              {extInfo.author && <span className="text-muted-foreground">Author <span className="text-foreground font-medium">{extInfo.author}</span></span>}
              {extInfo.category && <span className="text-muted-foreground">Category <span className="text-foreground font-medium">{extInfo.category}</span></span>}
            </div>
            {extInfo.tags && extInfo.tags.length > 0 && (
              <div className="flex flex-wrap gap-1 pt-0.5">
                {extInfo.tags.slice(0, 6).map(t => (
                  <span key={t} className="rounded-full bg-secondary px-2 py-0.5 text-[10px] text-secondary-foreground">{t}</span>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Controls */}
        <div className="space-y-1.5">
          <button onClick={toggleActive}
            className={`w-full flex items-center gap-2 px-3 py-2.5 rounded-lg text-xs font-medium transition-colors ${
              rt.active ? "bg-destructive/10 text-destructive hover:bg-destructive/20" : "bg-primary/10 text-primary hover:bg-primary/20"
            }`}>
            {rt.active ? <PowerOff className="w-3.5 h-3.5" /> : <Power className="w-3.5 h-3.5" />}
            {rt.active ? "Deactivate" : "Activate"}
          </button>
          {rt.active && (
            <button onClick={handleReload}
              className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg bg-secondary text-foreground text-xs hover:bg-secondary/80 transition-colors">
              <RefreshCw className="w-3.5 h-3.5" /> Reload Extension
            </button>
          )}
          <button onClick={() => setShowSettings(!showSettings)}
            className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg bg-secondary text-foreground text-xs hover:bg-secondary/80 transition-colors">
            <Settings className="w-3.5 h-3.5" /> Extension Settings
          </button>
          <button onClick={() => {
            const url = `https://open-vsx.org/extension/${extId.replace(".", "/")}`;
            window.open(url, "_blank");
          }}
            className="w-full flex items-center gap-2 px-3 py-2.5 rounded-lg bg-secondary text-foreground text-xs hover:bg-secondary/80 transition-colors">
            <BookOpen className="w-3.5 h-3.5" /> View Documentation
          </button>
        </div>

        {/* Settings panel */}
        {showSettings && (
          <div className="border border-border rounded-lg p-3 space-y-2">
            <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Settings</div>
            <label className="flex items-center justify-between text-xs">
              <span className="text-foreground">Auto-activate on startup</span>
              <input type="checkbox" checked={rt.prefs.autoActivate} onChange={e => setPref("autoActivate", e.target.checked)} className="accent-primary" />
            </label>
            <label className="flex items-center justify-between text-xs">
              <span className="text-foreground">Show notifications</span>
              <input type="checkbox" checked={rt.prefs.notifications} onChange={e => setPref("notifications", e.target.checked)} className="accent-primary" />
            </label>
            <label className="flex items-center justify-between text-xs">
              <span className="text-foreground">Verbose logging</span>
              <input type="checkbox" checked={rt.prefs.verbose} onChange={e => setPref("verbose", e.target.checked)} className="accent-primary" />
            </label>
          </div>
        )}

        <div className="h-px bg-border" />

        {/* Status */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-[11px]">
            <span className="text-muted-foreground">Status</span>
            <span className={`font-medium ${rt.active ? "text-primary" : "text-muted-foreground"}`}>
              {rt.active ? "● Active" : "○ Inactive"}
            </span>
          </div>
        </div>

        <div className="h-px bg-border" />

        {/* Output */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider flex items-center gap-1">
              <Terminal className="w-3 h-3" /> Output
            </div>
            {rt.logs.length > 0 && (
              <button onClick={() => setRt(prev => ({ ...prev, logs: [] }))} className="text-[10px] text-muted-foreground hover:text-foreground">Clear</button>
            )}
          </div>
          <div className="bg-background rounded-lg p-2 text-[10px] font-mono text-muted-foreground min-h-[100px] max-h-[250px] overflow-y-auto">
            {rt.logs.length === 0 ? (
              <div className="text-muted-foreground/50 italic">No output yet. Activate the extension to see logs.</div>
            ) : (
              rt.logs.map((log, i) => (
                <div key={i} className={log.includes("successfully") || log.includes("complete") || log.includes("ready") ? "text-primary" : log.includes("Deactivated") ? "text-destructive" : ""}>
                  {log}
                </div>
              ))
            )}
            <div ref={logsEndRef} />
          </div>

          {(extInfo?.repository || extInfo?.url) && (
            <div className="pt-1 space-y-1">
              {extInfo.url && (
                <a href={extInfo.url} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-[10px] text-primary hover:underline">
                  <ExternalLink className="w-3 h-3" />Marketplace page
                </a>
              )}
              {extInfo.repository && (
                <a href={extInfo.repository} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-[10px] text-primary hover:underline">
                  <ExternalLink className="w-3 h-3" />Repository
                </a>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
