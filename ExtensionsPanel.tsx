import { Download, Search, Star, ArrowLeft, ExternalLink, Tag, Calendar, User, Shield, Loader2, RefreshCw } from "lucide-react";
import { useState, useEffect, useCallback } from "react";

export interface Extension {
  id: string;
  name: string;
  author: string;
  description: string;
  longDescription: string;
  downloads: string;
  rating: number;
  installed: boolean;
  icon: string;
  version: string;
  lastUpdated: string;
  license: string;
  category: string;
  tags: string[];
  repository?: string;
  url?: string;
}

interface OpenVSXResult {
  extensions: Array<{
    namespace: string;
    name: string;
    displayName?: string;
    description?: string;
    version?: string;
    timestamp?: string;
    downloadCount?: number;
    averageRating?: number;
    files?: Record<string, string>;
    categories?: string[];
    tags?: string[];
    license?: string;
    repository?: string;
    url?: string;
  }>;
  totalSize?: number;
  offset?: number;
}

function formatDownloads(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + "M";
  if (n >= 1_000) return (n / 1_000).toFixed(1) + "K";
  return String(n);
}

function mapOpenVSXToExtension(ext: OpenVSXResult["extensions"][0], installedIds: Set<string>): Extension {
  const id = `${ext.namespace}.${ext.name}`;
  const iconUrl = ext.files?.["icon"] || "";
  return {
    id,
    name: ext.displayName || ext.name,
    author: ext.namespace,
    description: ext.description || "",
    longDescription: ext.description || "",
    downloads: formatDownloads(ext.downloadCount || 0),
    rating: ext.averageRating || 0,
    installed: installedIds.has(id),
    icon: iconUrl,
    version: ext.version || "0.0.0",
    lastUpdated: ext.timestamp ? ext.timestamp.split("T")[0] : "",
    license: ext.license || "Unknown",
    category: ext.categories?.[0] || "Other",
    tags: ext.tags || [],
    repository: ext.repository,
    url: ext.url,
  };
}

const POPULAR_QUERIES = ["python", "javascript", "git", "docker", "theme", "lint", "format", "rust", "go", "java"];

export function ExtensionsPanel() {
  const [extensions, setExtensions] = useState<Extension[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [selectedExtension, setSelectedExtension] = useState<Extension | null>(null);
  const [installedIds, setInstalledIds] = useState<Set<string>>(() => {
    try {
      const raw = localStorage.getItem("raneddie_installed_extensions");
      return new Set(raw ? JSON.parse(raw) : []);
    } catch { return new Set(); }
  });
  const [page, setPage] = useState(0);
  const [totalResults, setTotalResults] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const PAGE_SIZE = 30;

  const fetchExtensions = useCallback(async (query: string, offset = 0) => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({
        size: String(PAGE_SIZE),
        offset: String(offset),
        sortBy: "downloadCount",
        sortOrder: "desc",
      });
      if (query.trim()) params.set("query", query.trim());

      const res = await fetch(`https://open-vsx.org/api/-/search?${params}`);
      if (!res.ok) throw new Error(`API error: ${res.status}`);
      const data: OpenVSXResult = await res.json();

      const mapped = data.extensions.map(e => mapOpenVSXToExtension(e, installedIds));
      setExtensions(mapped);
      setTotalResults(data.totalSize || 0);
      setPage(offset / PAGE_SIZE);
    } catch (err: any) {
      setError(err.message || "Failed to fetch extensions");
    } finally {
      setLoading(false);
    }
  }, [installedIds]);

  useEffect(() => {
    fetchExtensions("");
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchExtensions(search, 0);
    }, 400);
    return () => clearTimeout(timer);
  }, [search]);

  const toggleInstall = (id: string) => {
    const ext = extensions.find(e => e.id === id);
    const next = new Set(installedIds);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setInstalledIds(next);
    localStorage.setItem("raneddie_installed_extensions", JSON.stringify([...next]));

    try {
      const metaRaw = localStorage.getItem("raneddie_installed_extensions_meta");
      const meta: Record<string, {
        name: string;
        icon: string;
        description?: string;
        version?: string;
        author?: string;
        repository?: string;
        url?: string;
        category?: string;
        tags?: string[];
      }> = metaRaw ? JSON.parse(metaRaw) : {};
      if (next.has(id) && ext) {
        meta[id] = {
          name: ext.name,
          icon: ext.icon,
          description: ext.description,
          version: ext.version,
          author: ext.author,
          repository: ext.repository,
          url: ext.url,
          category: ext.category,
          tags: ext.tags,
        };
      } else {
        delete meta[id];
        const runtimeRaw = localStorage.getItem("raneddie_extension_runtime");
        if (runtimeRaw) {
          const runtime = JSON.parse(runtimeRaw);
          delete runtime[id];
          localStorage.setItem("raneddie_extension_runtime", JSON.stringify(runtime));
        }
      }
      localStorage.setItem("raneddie_installed_extensions_meta", JSON.stringify(meta));
    } catch {}

    setExtensions(prev => prev.map(e => e.id === id ? { ...e, installed: next.has(id) } : e));
    if (selectedExtension?.id === id) {
      setSelectedExtension(prev => prev ? { ...prev, installed: next.has(id) } : null);
    }
    window.dispatchEvent(new Event("extensions-changed"));
  };

  // Detail page
  if (selectedExtension) {
    const ext = extensions.find(e => e.id === selectedExtension.id) || selectedExtension;
    return (
      <div className="flex flex-col h-full">
        <div className="p-3 border-b border-border flex items-center gap-2">
          <button onClick={() => setSelectedExtension(null)} className="p-1 rounded hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
            <ArrowLeft className="w-4 h-4" />
          </button>
          <span className="text-sm font-medium text-foreground truncate">{ext.name}</span>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
          <div className="flex items-start gap-3 mb-4">
            {ext.icon ? (
              <img src={ext.icon} alt={ext.name} className="w-12 h-12 rounded-lg object-contain flex-shrink-0" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
            ) : (
              <span className="w-12 h-12 rounded-lg bg-secondary flex items-center justify-center text-2xl flex-shrink-0">📦</span>
            )}
            <div className="flex-1">
              <h3 className="text-base font-semibold text-foreground">{ext.name}</h3>
              <p className="text-xs text-muted-foreground">{ext.author}</p>
              <div className="flex items-center gap-3 mt-1.5">
                <span className="flex items-center gap-1 text-xs text-muted-foreground"><Download className="w-3 h-3" />{ext.downloads}</span>
                {ext.rating > 0 && <span className="flex items-center gap-1 text-xs text-yellow-500"><Star className="w-3 h-3 fill-current" />{ext.rating.toFixed(1)}</span>}
              </div>
            </div>
          </div>

          <button
            onClick={() => toggleInstall(ext.id)}
            className={`w-full py-2 rounded-lg text-sm font-medium transition-colors mb-4 ${
              ext.installed ? "bg-primary/20 text-primary border border-primary/30" : "bg-primary text-primary-foreground hover:bg-primary/90"
            }`}
          >
            {ext.installed ? "✓ Installed — Uninstall" : "Install Extension"}
          </button>

          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="flex items-center gap-1.5 text-muted-foreground"><Tag className="w-3 h-3" />v{ext.version}</div>
              <div className="flex items-center gap-1.5 text-muted-foreground"><Calendar className="w-3 h-3" />{ext.lastUpdated}</div>
              <div className="flex items-center gap-1.5 text-muted-foreground"><Shield className="w-3 h-3" />{ext.license}</div>
              <div className="flex items-center gap-1.5 text-muted-foreground"><User className="w-3 h-3" />{ext.author}</div>
            </div>

            <div className="border-t border-border pt-3">
              <h4 className="text-xs font-medium text-foreground mb-1.5">About</h4>
              <p className="text-xs text-muted-foreground leading-relaxed">{ext.longDescription}</p>
            </div>

            <div className="border-t border-border pt-3">
              <h4 className="text-xs font-medium text-foreground mb-1.5">Category</h4>
              <span className="text-xs px-2 py-0.5 rounded-full bg-secondary text-secondary-foreground">{ext.category}</span>
            </div>

            {ext.tags.length > 0 && (
              <div className="border-t border-border pt-3">
                <h4 className="text-xs font-medium text-foreground mb-1.5">Tags</h4>
                <div className="flex flex-wrap gap-1">
                  {ext.tags.map(tag => (
                    <span key={tag} className="text-[10px] px-2 py-0.5 rounded-full bg-primary/10 text-primary">{tag}</span>
                  ))}
                </div>
              </div>
            )}

            {(ext.repository || ext.url) && (
              <div className="border-t border-border pt-3 space-y-1">
                {ext.url && (
                  <a href={ext.url} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-primary hover:underline">
                    <ExternalLink className="w-3 h-3" />View on PrimeCode Marketplace
                  </a>
                )}
                {ext.repository && (
                  <a href={ext.repository} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-xs text-primary hover:underline">
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

  return (
    <div className="flex flex-col h-full">
      <div className="p-3 border-b border-border">
        <div className="px-2 pb-2 text-[10px] font-bold text-muted-foreground uppercase tracking-wider">
          The Official PrimeCode Marketplace
        </div>
        <div className="flex items-center gap-2 bg-secondary rounded-lg px-3 py-2 mb-2">
          <Search className="w-4 h-4 text-muted-foreground" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search extensions..."
            className="flex-1 bg-transparent text-sm text-foreground outline-none border-none placeholder:text-muted-foreground"
          />
          {loading && <Loader2 className="w-4 h-4 text-muted-foreground animate-spin" />}
        </div>
        {!search && (
          <div className="flex gap-1 overflow-x-auto pb-1 scrollbar-none">
            {POPULAR_QUERIES.map(q => (
              <button key={q} onClick={() => setSearch(q)}
                className="text-[10px] px-2 py-1 rounded-full whitespace-nowrap bg-secondary text-muted-foreground hover:text-foreground transition-colors">
                {q}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="flex-1 overflow-y-auto">
        {error && (
          <div className="p-4 text-center">
            <p className="text-xs text-destructive mb-2">{error}</p>
            <button onClick={() => fetchExtensions(search)} className="text-xs text-primary hover:underline flex items-center gap-1 mx-auto">
              <RefreshCw className="w-3 h-3" /> Retry
            </button>
          </div>
        )}

        {!loading && !error && extensions.length === 0 && (
          <p className="text-xs text-muted-foreground p-4 text-center">No extensions found</p>
        )}

        {extensions.map((ext) => (
          <div
            key={ext.id}
            className="flex items-start gap-3 p-3 border-b border-border hover:bg-card/50 transition-colors cursor-pointer"
            onClick={() => setSelectedExtension(ext)}
          >
            {ext.icon ? (
              <img src={ext.icon} alt={ext.name} className="w-8 h-8 rounded object-contain flex-shrink-0" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
            ) : (
              <span className="w-8 h-8 rounded bg-secondary flex items-center justify-center text-lg flex-shrink-0">📦</span>
            )}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-foreground">{ext.name}</span>
                <span className="text-[10px] text-muted-foreground">v{ext.version}</span>
              </div>
              <span className="text-[10px] text-muted-foreground">{ext.author}</span>
              <p className="text-xs text-muted-foreground mt-0.5 line-clamp-1">{ext.description}</p>
              <div className="flex items-center gap-3 mt-1">
                <span className="flex items-center gap-1 text-[10px] text-muted-foreground"><Download className="w-2.5 h-2.5" />{ext.downloads}</span>
                {ext.rating > 0 && <span className="flex items-center gap-1 text-[10px] text-yellow-500"><Star className="w-2.5 h-2.5 fill-current" />{ext.rating.toFixed(1)}</span>}
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-secondary text-muted-foreground">{ext.category}</span>
              </div>
            </div>
            <button
              onClick={(e) => { e.stopPropagation(); toggleInstall(ext.id); }}
              className={`flex-shrink-0 px-2.5 py-1 rounded text-[10px] font-medium transition-colors ${
                ext.installed ? "bg-primary/20 text-primary" : "bg-primary text-primary-foreground hover:bg-primary/90"
              }`}
            >
              {ext.installed ? "✓" : "Install"}
            </button>
          </div>
        ))}

        {totalResults > PAGE_SIZE && (
          <div className="flex items-center justify-center gap-2 p-3 border-t border-border">
            <button
              disabled={page === 0}
              onClick={() => fetchExtensions(search, (page - 1) * PAGE_SIZE)}
              className="text-xs px-3 py-1.5 rounded-lg bg-secondary text-foreground hover:bg-secondary/80 disabled:opacity-30 transition-colors"
            >
              ← Prev
            </button>
            <span className="text-[10px] text-muted-foreground">
              {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, totalResults)} of {totalResults}
            </span>
            <button
              disabled={(page + 1) * PAGE_SIZE >= totalResults}
              onClick={() => fetchExtensions(search, (page + 1) * PAGE_SIZE)}
              className="text-xs px-3 py-1.5 rounded-lg bg-secondary text-foreground hover:bg-secondary/80 disabled:opacity-30 transition-colors"
            >
              Next →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
