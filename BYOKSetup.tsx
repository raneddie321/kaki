import { useState } from "react";
import { BYOK_PROVIDERS, type BYOKConfig, type BYOKProvider, loadBYOKConfig, saveBYOKConfig } from "@/lib/byokProviders";
import { Key, Check, ExternalLink, ChevronRight } from "lucide-react";

interface BYOKSetupProps {
  onConfigured: () => void;
}

export function BYOKSetup({ onConfigured }: BYOKSetupProps) {
  const existing = loadBYOKConfig();
  const [selectedProvider, setSelectedProvider] = useState<string>(existing?.providerId || "");
  const [apiKey, setApiKey] = useState(existing?.apiKey || "");
  const [customUrl, setCustomUrl] = useState(existing?.customUrl || "");
  const [selectedModel, setSelectedModel] = useState(existing?.selectedModel || "");

  const provider = BYOK_PROVIDERS.find(p => p.id === selectedProvider);

  const handleSave = () => {
    if (!selectedProvider || !apiKey) return;
    const config: BYOKConfig = {
      providerId: selectedProvider,
      apiKey,
      customUrl: selectedProvider === "custom" ? customUrl : undefined,
      selectedModel: selectedModel || provider?.models[0]?.id || "",
    };
    saveBYOKConfig(config);
    onConfigured();
  };

  if (!selectedProvider) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-6 p-6">
        <div className="w-14 h-14 rounded-2xl bg-accent/20 flex items-center justify-center">
          <Key className="w-7 h-7 text-accent" />
        </div>
        <div className="text-center">
          <h2 className="text-lg font-semibold text-foreground mb-1">Bring Your Own Key</h2>
          <p className="text-sm text-muted-foreground">Select a provider to get started</p>
        </div>
        <div className="w-full max-w-sm space-y-2">
          {BYOK_PROVIDERS.map(p => (
            <button
              key={p.id}
              onClick={() => { setSelectedProvider(p.id); setSelectedModel(p.models[0]?.id || ""); }}
              className="w-full flex items-center justify-between px-4 py-3 rounded-xl bg-card border border-border hover:border-primary/40 transition-colors text-left"
            >
              <div>
                <div className="text-sm font-medium text-foreground">{p.name}</div>
                <div className="text-xs text-muted-foreground">{p.description}</div>
              </div>
              <ChevronRight className="w-4 h-4 text-muted-foreground" />
            </button>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-6 p-6">
      <div className="w-14 h-14 rounded-2xl bg-accent/20 flex items-center justify-center">
        <Key className="w-7 h-7 text-accent" />
      </div>
      <div className="text-center">
        <h2 className="text-lg font-semibold text-foreground mb-1">{provider?.name || "Custom"}</h2>
        <p className="text-sm text-muted-foreground">Enter your API key to connect</p>
      </div>

      <div className="w-full max-w-sm space-y-4">
        {selectedProvider === "custom" && (
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">API Base URL</label>
            <input
              type="url"
              value={customUrl}
              onChange={e => setCustomUrl(e.target.value)}
              placeholder="https://api.example.com/v1"
              className="w-full px-3 py-2 rounded-lg bg-secondary border border-border text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50"
            />
          </div>
        )}

        <div>
          <label className="text-xs font-medium text-muted-foreground mb-1 block">API Key</label>
          <input
            type="password"
            value={apiKey}
            onChange={e => setApiKey(e.target.value)}
            placeholder={provider?.keyPlaceholder || "your-api-key"}
            className="w-full px-3 py-2 rounded-lg bg-secondary border border-border text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50"
          />
        </div>

        {provider && provider.models.length > 0 && (
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Model</label>
            <select
              value={selectedModel}
              onChange={e => setSelectedModel(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-secondary border border-border text-sm text-foreground outline-none focus:border-primary/50"
            >
              {provider.models.map(m => (
                <option key={m.id} value={m.id}>{m.label}</option>
              ))}
            </select>
          </div>
        )}

        {selectedProvider === "custom" && (
          <div>
            <label className="text-xs font-medium text-muted-foreground mb-1 block">Model ID</label>
            <input
              type="text"
              value={selectedModel}
              onChange={e => setSelectedModel(e.target.value)}
              placeholder="model-name"
              className="w-full px-3 py-2 rounded-lg bg-secondary border border-border text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50"
            />
          </div>
        )}

        <div className="flex gap-2">
          <button
            onClick={() => setSelectedProvider("")}
            className="flex-1 px-4 py-2.5 rounded-lg bg-secondary text-sm text-foreground hover:bg-secondary/80 transition-colors"
          >
            Back
          </button>
          <button
            onClick={handleSave}
            disabled={!apiKey || (selectedProvider === "custom" && !customUrl)}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-40 transition-colors"
          >
            <Check className="w-4 h-4" />
            Connect
          </button>
        </div>
      </div>
    </div>
  );
}
