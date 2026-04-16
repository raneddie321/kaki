import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Key, Plus, Trash2, Edit2, Check, X, Bot, Shield, Save, Eye, EyeOff, Settings, Cpu, Globe, Zap, Users, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { toast } from "sonner";
import { supabase } from "@/integrations/supabase/client";

const ADMIN_PASS_KEY = "raneddie_admin_pass";
const API_KEYS_KEY = "raneddie_admin_api_keys";
const CUSTOM_MODELS_KEY = "raneddie_admin_custom_models";
const REQUESTY_KEY = "raneddie_requesty_api_key";

interface APIKeyEntry {
  id: string;
  provider: string;
  label: string;
  key: string;
  enabled: boolean;
}

interface CustomModel {
  id: string;
  modelId: string;
  label: string;
  provider: string;
  description: string;
  category: "fast" | "balanced" | "powerful" | "reasoning";
  enabled: boolean;
}

interface UserEntry {
  user_id: string;
  email: string;
  username: string | null;
  display_name: string | null;
  credits_remaining: number;
  credits_total: number;
  plan: string;
  week_start: string;
  created_at: string;
}

const DEFAULT_PROVIDERS = [
  { id: "openai", name: "OpenAI", icon: "🟢", placeholder: "sk-..." },
  { id: "anthropic", name: "Anthropic", icon: "🟠", placeholder: "sk-ant-..." },
  { id: "openrouter", name: "OpenRouter", icon: "🔵", placeholder: "sk-or-..." },
  { id: "google", name: "Google AI Studio", icon: "🔴", placeholder: "AIza..." },
  { id: "groq", name: "Groq", icon: "⚡", placeholder: "gsk_..." },
  { id: "together", name: "Together AI", icon: "🟣", placeholder: "tog-..." },
  { id: "fireworks", name: "Fireworks AI", icon: "🔥", placeholder: "fw_..." },
  { id: "mistral", name: "Mistral AI", icon: "🇫🇷", placeholder: "mist-..." },
  { id: "perplexity", name: "Perplexity", icon: "🌐", placeholder: "pplx-..." },
  { id: "siliconflow", name: "SiliconFlow", icon: "💎", placeholder: "sf-..." },
  { id: "deepseek", name: "DeepSeek", icon: "🐋", placeholder: "sk-..." },
  { id: "cohere", name: "Cohere", icon: "🧬", placeholder: "co-..." },
  { id: "replicate", name: "Replicate", icon: "🔄", placeholder: "r8_..." },
  { id: "huggingface", name: "Hugging Face", icon: "🤗", placeholder: "hf_..." },
];

const MODEL_PROVIDERS = [
  "OpenAI", "Anthropic", "Google", "Meta", "Mistral AI", "Cohere",
  "OpenRouter", "Groq", "Together AI", "Fireworks AI", "SiliconFlow",
  "DeepSeek", "Perplexity", "Replicate", "Hugging Face", "Custom",
];

const CATEGORY_OPTIONS: { value: CustomModel["category"]; label: string; color: string }[] = [
  { value: "fast", label: "Fast", color: "text-green-400" },
  { value: "balanced", label: "Balanced", color: "text-blue-400" },
  { value: "powerful", label: "Powerful", color: "text-purple-400" },
  { value: "reasoning", label: "Reasoning", color: "text-orange-400" },
];

function loadApiKeys(): APIKeyEntry[] {
  try {
    const raw = localStorage.getItem(API_KEYS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}
function saveApiKeys(keys: APIKeyEntry[]) {
  localStorage.setItem(API_KEYS_KEY, JSON.stringify(keys));
}
function loadCustomModels(): CustomModel[] {
  try {
    const raw = localStorage.getItem(CUSTOM_MODELS_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch { return []; }
}
function saveCustomModels(models: CustomModel[]) {
  localStorage.setItem(CUSTOM_MODELS_KEY, JSON.stringify(models));
}

function PasswordGate({ onUnlock }: { onUnlock: () => void }) {
  const [password, setPassword] = useState("");
  const [isSetup, setIsSetup] = useState(false);
  const [confirm, setConfirm] = useState("");

  useEffect(() => {
    setIsSetup(!localStorage.getItem(ADMIN_PASS_KEY));
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (isSetup) {
      if (password.length < 4) { toast.error("Password must be at least 4 characters"); return; }
      if (password !== confirm) { toast.error("Passwords don't match"); return; }
      localStorage.setItem(ADMIN_PASS_KEY, btoa(password));
      toast.success("Admin password set!");
      onUnlock();
    } else {
      const stored = localStorage.getItem(ADMIN_PASS_KEY);
      if (stored && atob(stored) === password) {
        onUnlock();
      } else {
        toast.error("Incorrect password");
      }
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-background">
      <div className="w-full max-w-sm p-8 rounded-2xl bg-card border border-border shadow-2xl">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-12 h-12 rounded-xl bg-primary/20 flex items-center justify-center">
            <Shield className="w-6 h-6 text-primary" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-foreground">Admin Dashboard</h1>
            <p className="text-xs text-muted-foreground">{isSetup ? "Create your admin password" : "Enter your password"}</p>
          </div>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <Input
            type="password" placeholder="Password" value={password}
            onChange={(e) => setPassword(e.target.value)} autoFocus
            className="bg-secondary border-border"
          />
          {isSetup && (
            <Input
              type="password" placeholder="Confirm password" value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              className="bg-secondary border-border"
            />
          )}
          <Button type="submit" className="w-full">{isSetup ? "Set Password & Enter" : "Unlock"}</Button>
        </form>
      </div>
    </div>
  );
}

type Tab = "keys" | "models" | "users" | "settings";

export default function Admin() {
  const navigate = useNavigate();
  const [unlocked, setUnlocked] = useState(false);
  const [tab, setTab] = useState<Tab>("keys");
  const [apiKeys, setApiKeys] = useState<APIKeyEntry[]>(loadApiKeys);
  const [customModels, setCustomModels] = useState<CustomModel[]>(loadCustomModels);
  const [visibleKeys, setVisibleKeys] = useState<Set<string>>(new Set());
  const [requestyKey, setRequestyKey] = useState(() => localStorage.getItem(REQUESTY_KEY) || "");
  const [showRequestyKey, setShowRequestyKey] = useState(false);

  // Users state
  const [users, setUsers] = useState<UserEntry[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [editingUser, setEditingUser] = useState<string | null>(null);
  const [editCreditsRemaining, setEditCreditsRemaining] = useState("");
  const [editCreditsTotal, setEditCreditsTotal] = useState("");
  const [editWeekStart, setEditWeekStart] = useState("");

  // New key form
  const [newProvider, setNewProvider] = useState(DEFAULT_PROVIDERS[0].id);
  const [newKey, setNewKey] = useState("");

  // New model form
  const [showNewModel, setShowNewModel] = useState(false);
  const [newModel, setNewModel] = useState<Omit<CustomModel, "id" | "enabled">>({
    modelId: "", label: "", provider: "", description: "", category: "balanced",
  });

  // Editing
  const [editingKeyId, setEditingKeyId] = useState<string | null>(null);
  const [editKeyValue, setEditKeyValue] = useState("");

  const fetchUsers = async () => {
    setUsersLoading(true);
    try {
      const { data, error } = await supabase.functions.invoke("admin-users", { method: "GET" });
      if (error) throw error;
      setUsers(data?.users || []);
    } catch (err: any) {
      toast.error("Failed to load users: " + err.message);
    } finally {
      setUsersLoading(false);
    }
  };

  const updateUserCredits = async (userId: string) => {
    try {
      const body: any = { action: "update_credits", user_id: userId };
      if (editCreditsRemaining !== "") body.credits_remaining = parseInt(editCreditsRemaining);
      if (editCreditsTotal !== "") body.credits_total = parseInt(editCreditsTotal);
      if (editWeekStart !== "") body.week_start = new Date(editWeekStart).toISOString();

      const { error } = await supabase.functions.invoke("admin-users", {
        method: "POST",
        body,
      });
      if (error) throw error;
      toast.success("User credits updated");
      setEditingUser(null);
      fetchUsers();
    } catch (err: any) {
      toast.error("Failed to update: " + err.message);
    }
  };

  useEffect(() => {
    if (unlocked && tab === "users") {
      fetchUsers();
    }
  }, [unlocked, tab]);

  if (!unlocked) return <PasswordGate onUnlock={() => setUnlocked(true)} />;

  const addApiKey = () => {
    if (!newKey.trim()) { toast.error("Please enter an API key"); return; }
    const prov = DEFAULT_PROVIDERS.find(p => p.id === newProvider)!;
    const entry: APIKeyEntry = {
      id: crypto.randomUUID(), provider: prov.id, label: prov.name, key: newKey.trim(), enabled: true,
    };
    const updated = [...apiKeys, entry];
    setApiKeys(updated);
    saveApiKeys(updated);
    setNewKey("");
    toast.success(`Added ${prov.name} key`);
  };

  const deleteApiKey = (id: string) => {
    const updated = apiKeys.filter(k => k.id !== id);
    setApiKeys(updated);
    saveApiKeys(updated);
    toast.success("Key removed");
  };

  const toggleApiKey = (id: string) => {
    const updated = apiKeys.map(k => k.id === id ? { ...k, enabled: !k.enabled } : k);
    setApiKeys(updated);
    saveApiKeys(updated);
  };

  const saveEditKey = (id: string) => {
    const updated = apiKeys.map(k => k.id === id ? { ...k, key: editKeyValue } : k);
    setApiKeys(updated);
    saveApiKeys(updated);
    setEditingKeyId(null);
    toast.success("Key updated");
  };

  const toggleVisibility = (id: string) => {
    setVisibleKeys(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const maskKey = (key: string) => key.slice(0, 6) + "•".repeat(Math.max(0, key.length - 10)) + key.slice(-4);

  const addModel = () => {
    if (!newModel.modelId || !newModel.label || !newModel.provider) {
      toast.error("Fill in model ID, label, and provider"); return;
    }
    const model: CustomModel = { ...newModel, id: crypto.randomUUID(), enabled: true };
    const updated = [...customModels, model];
    setCustomModels(updated);
    saveCustomModels(updated);
    setNewModel({ modelId: "", label: "", provider: "", description: "", category: "balanced" });
    setShowNewModel(false);
    toast.success(`Added model: ${model.label}`);
  };

  const deleteModel = (id: string) => {
    const updated = customModels.filter(m => m.id !== id);
    setCustomModels(updated);
    saveCustomModels(updated);
    toast.success("Model removed");
  };

  const toggleModel = (id: string) => {
    const updated = customModels.map(m => m.id === id ? { ...m, enabled: !m.enabled } : m);
    setCustomModels(updated);
    saveCustomModels(updated);
  };

  const resetPassword = () => {
    localStorage.removeItem(ADMIN_PASS_KEY);
    setUnlocked(false);
    toast.success("Password reset. Set a new one.");
  };

  const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
    { id: "keys", label: "API Keys", icon: Key },
    { id: "models", label: "AI Models", icon: Bot },
    { id: "users", label: "Users", icon: Users },
    { id: "settings", label: "Settings", icon: Settings },
  ];

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="sticky top-0 z-20 border-b border-border bg-card/80 backdrop-blur-xl">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 py-3 flex items-center gap-4">
          <button onClick={() => navigate("/")} className="p-2 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-primary/20 flex items-center justify-center">
              <Shield className="w-5 h-5 text-primary" />
            </div>
            <div>
              <h1 className="text-base font-bold text-foreground">Admin Dashboard</h1>
              <p className="text-xs text-muted-foreground">Manage providers, models, users & settings</p>
            </div>
          </div>
        </div>
      </header>

      {/* Tabs */}
      <div className="max-w-5xl mx-auto px-4 sm:px-6 mt-4">
        <div className="flex gap-1 p-1 rounded-xl bg-secondary/50 w-fit flex-wrap">
          {TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                tab === t.id ? "bg-primary text-primary-foreground shadow-lg" : "text-muted-foreground hover:text-foreground hover:bg-secondary"
              }`}>
              <t.icon className="w-4 h-4" />
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-6">
        {/* API Keys Tab */}
        {tab === "keys" && (
          <div className="space-y-6">
            <div className="rounded-2xl border border-border bg-card p-5">
              <h2 className="text-sm font-semibold text-foreground mb-4 flex items-center gap-2">
                <Plus className="w-4 h-4 text-primary" /> Add API Key
              </h2>
              <div className="flex flex-col sm:flex-row gap-3">
                <select value={newProvider} onChange={(e) => setNewProvider(e.target.value)}
                  className="flex-shrink-0 h-10 rounded-lg border border-border bg-secondary text-foreground px-3 text-sm min-w-[180px]">
                  {DEFAULT_PROVIDERS.map(p => (
                    <option key={p.id} value={p.id}>{p.icon} {p.name}</option>
                  ))}
                </select>
                <Input
                  placeholder={DEFAULT_PROVIDERS.find(p => p.id === newProvider)?.placeholder || "Enter API key"}
                  value={newKey} onChange={(e) => setNewKey(e.target.value)}
                  type="password" className="flex-1 bg-secondary border-border"
                />
                <Button onClick={addApiKey} className="shrink-0">
                  <Plus className="w-4 h-4 mr-1" /> Add
                </Button>
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card overflow-hidden">
              <div className="px-5 py-3 border-b border-border">
                <h2 className="text-sm font-semibold text-foreground">Configured Keys ({apiKeys.length})</h2>
              </div>
              {apiKeys.length === 0 ? (
                <div className="p-12 text-center text-muted-foreground text-sm">
                  <Key className="w-10 h-10 mx-auto mb-3 opacity-30" />
                  No API keys configured yet
                </div>
              ) : (
                <div className="divide-y divide-border">
                  {apiKeys.map(k => {
                    const prov = DEFAULT_PROVIDERS.find(p => p.id === k.provider);
                    const isVisible = visibleKeys.has(k.id);
                    const isEditing = editingKeyId === k.id;
                    return (
                      <div key={k.id} className={`px-5 py-3 flex items-center gap-3 ${!k.enabled ? "opacity-50" : ""}`}>
                        <span className="text-lg">{prov?.icon || "🔑"}</span>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm font-medium text-foreground">{k.label}</div>
                          {isEditing ? (
                            <div className="flex items-center gap-2 mt-1">
                              <Input value={editKeyValue} onChange={(e) => setEditKeyValue(e.target.value)}
                                className="h-7 text-xs bg-secondary border-border" autoFocus />
                              <button onClick={() => saveEditKey(k.id)} className="text-green-400 hover:text-green-300"><Check className="w-4 h-4" /></button>
                              <button onClick={() => setEditingKeyId(null)} className="text-red-400 hover:text-red-300"><X className="w-4 h-4" /></button>
                            </div>
                          ) : (
                            <div className="text-xs text-muted-foreground font-mono">
                              {isVisible ? k.key : maskKey(k.key)}
                            </div>
                          )}
                        </div>
                        <div className="flex items-center gap-1">
                          <button onClick={() => toggleVisibility(k.id)}
                            className="p-1.5 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
                            {isVisible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                          </button>
                          <button onClick={() => { setEditingKeyId(k.id); setEditKeyValue(k.key); }}
                            className="p-1.5 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button onClick={() => toggleApiKey(k.id)}
                            className={`px-2 py-1 rounded-md text-[10px] font-medium transition-colors ${
                              k.enabled ? "bg-green-500/20 text-green-400" : "bg-secondary text-muted-foreground"
                            }`}>
                            {k.enabled ? "ON" : "OFF"}
                          </button>
                          <button onClick={() => deleteApiKey(k.id)}
                            className="p-1.5 rounded-lg hover:bg-destructive/20 text-muted-foreground hover:text-destructive transition-colors">
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Models Tab */}
        {tab === "models" && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">Custom AI Models ({customModels.length})</h2>
              <Button size="sm" onClick={() => setShowNewModel(!showNewModel)} variant={showNewModel ? "secondary" : "default"}>
                {showNewModel ? <X className="w-4 h-4 mr-1" /> : <Plus className="w-4 h-4 mr-1" />}
                {showNewModel ? "Cancel" : "Add Model"}
              </Button>
            </div>

            {showNewModel && (
              <div className="rounded-2xl border border-primary/30 bg-card p-5 space-y-4">
                <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                  <Bot className="w-4 h-4 text-primary" /> New Model
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <Input placeholder="Model ID (e.g. gpt-4o)" value={newModel.modelId}
                    onChange={(e) => setNewModel({ ...newModel, modelId: e.target.value })}
                    className="bg-secondary border-border" />
                  <Input placeholder="Display Label" value={newModel.label}
                    onChange={(e) => setNewModel({ ...newModel, label: e.target.value })}
                    className="bg-secondary border-border" />
                  <select value={newModel.provider} onChange={(e) => setNewModel({ ...newModel, provider: e.target.value })}
                    className="h-10 rounded-lg border border-border bg-secondary text-foreground px-3 text-sm">
                    <option value="">Select Provider</option>
                    {MODEL_PROVIDERS.map(p => <option key={p} value={p}>{p}</option>)}
                  </select>
                  <select value={newModel.category} onChange={(e) => setNewModel({ ...newModel, category: e.target.value as CustomModel["category"] })}
                    className="h-10 rounded-lg border border-border bg-secondary text-foreground px-3 text-sm">
                    {CATEGORY_OPTIONS.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
                  </select>
                </div>
                <Input placeholder="Description (optional)" value={newModel.description}
                  onChange={(e) => setNewModel({ ...newModel, description: e.target.value })}
                  className="bg-secondary border-border" />
                <Button onClick={addModel}>
                  <Save className="w-4 h-4 mr-1" /> Save Model
                </Button>
              </div>
            )}

            {customModels.length === 0 && !showNewModel ? (
              <div className="rounded-2xl border border-border bg-card p-12 text-center">
                <Cpu className="w-10 h-10 mx-auto mb-3 text-muted-foreground opacity-30" />
                <p className="text-sm text-muted-foreground">No custom models added yet</p>
                <p className="text-xs text-muted-foreground mt-1">Add models to make them available in the chat</p>
              </div>
            ) : (
              <div className="grid gap-3">
                {customModels.map(m => {
                  const cat = CATEGORY_OPTIONS.find(c => c.value === m.category);
                  return (
                    <div key={m.id} className={`rounded-xl border bg-card p-4 flex items-center gap-4 transition-opacity ${
                      m.enabled ? "border-border" : "border-border opacity-50"
                    }`}>
                      <div className="w-10 h-10 rounded-xl bg-secondary flex items-center justify-center shrink-0">
                        <Bot className={`w-5 h-5 ${cat?.color || "text-muted-foreground"}`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-foreground">{m.label}</span>
                          <span className={`text-[10px] px-2 py-0.5 rounded-full bg-secondary ${cat?.color || ""}`}>{cat?.label}</span>
                        </div>
                        <div className="text-xs text-muted-foreground">{m.provider} · <span className="font-mono">{m.modelId}</span></div>
                        {m.description && <div className="text-xs text-muted-foreground mt-0.5">{m.description}</div>}
                      </div>
                      <div className="flex items-center gap-1">
                        <button onClick={() => toggleModel(m.id)}
                          className={`px-2 py-1 rounded-md text-[10px] font-medium transition-colors ${
                            m.enabled ? "bg-green-500/20 text-green-400" : "bg-secondary text-muted-foreground"
                          }`}>
                          {m.enabled ? "ON" : "OFF"}
                        </button>
                        <button onClick={() => deleteModel(m.id)}
                          className="p-1.5 rounded-lg hover:bg-destructive/20 text-muted-foreground hover:text-destructive transition-colors">
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Users Tab */}
        {tab === "users" && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">All Users ({users.length})</h2>
              <Button size="sm" onClick={fetchUsers} disabled={usersLoading}>
                <RefreshCw className={`w-4 h-4 mr-1 ${usersLoading ? "animate-spin" : ""}`} /> Refresh
              </Button>
            </div>

            {usersLoading && users.length === 0 ? (
              <div className="rounded-2xl border border-border bg-card p-12 text-center">
                <RefreshCw className="w-10 h-10 mx-auto mb-3 text-muted-foreground opacity-30 animate-spin" />
                <p className="text-sm text-muted-foreground">Loading users...</p>
              </div>
            ) : users.length === 0 ? (
              <div className="rounded-2xl border border-border bg-card p-12 text-center">
                <Users className="w-10 h-10 mx-auto mb-3 text-muted-foreground opacity-30" />
                <p className="text-sm text-muted-foreground">No users found</p>
              </div>
            ) : (
              <div className="space-y-3">
                {users.map(u => {
                  const isEditing = editingUser === u.user_id;
                  return (
                    <div key={u.user_id} className="rounded-xl border border-border bg-card p-4">
                      <div className="flex items-start justify-between gap-4">
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-medium text-foreground">{u.display_name || u.username || "No name"}</span>
                            {u.username && <span className="text-xs text-muted-foreground">@{u.username}</span>}
                            <span className={`text-[10px] px-2 py-0.5 rounded-full bg-secondary ${
                              u.plan === "free" ? "text-muted-foreground" : "text-primary"
                            }`}>{u.plan}</span>
                          </div>
                          <div className="text-xs text-muted-foreground mt-0.5">{u.email}</div>
                          <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                            <span>Credits: <span className="text-foreground font-medium">{u.credits_remaining}</span> / {u.credits_total}</span>
                            <span>Renews: <span className="text-foreground font-medium">{new Date(u.week_start).toLocaleDateString()}</span></span>
                          </div>
                        </div>
                        <Button size="sm" variant={isEditing ? "secondary" : "outline"}
                          onClick={() => {
                            if (isEditing) {
                              setEditingUser(null);
                            } else {
                              setEditingUser(u.user_id);
                              setEditCreditsRemaining(String(u.credits_remaining));
                              setEditCreditsTotal(String(u.credits_total));
                              setEditWeekStart(u.week_start ? new Date(u.week_start).toISOString().slice(0, 16) : "");
                            }
                          }}>
                          {isEditing ? <X className="w-3.5 h-3.5 mr-1" /> : <Edit2 className="w-3.5 h-3.5 mr-1" />}
                          {isEditing ? "Cancel" : "Edit"}
                        </Button>
                      </div>

                      {isEditing && (
                        <div className="mt-4 pt-4 border-t border-border space-y-3">
                          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                            <div>
                              <label className="text-xs text-muted-foreground mb-1 block">Credits Remaining</label>
                              <Input type="number" value={editCreditsRemaining}
                                onChange={(e) => setEditCreditsRemaining(e.target.value)}
                                className="bg-secondary border-border" />
                            </div>
                            <div>
                              <label className="text-xs text-muted-foreground mb-1 block">Credits Total</label>
                              <Input type="number" value={editCreditsTotal}
                                onChange={(e) => setEditCreditsTotal(e.target.value)}
                                className="bg-secondary border-border" />
                            </div>
                            <div>
                              <label className="text-xs text-muted-foreground mb-1 block">Renewal Date</label>
                              <Input type="datetime-local" value={editWeekStart}
                                onChange={(e) => setEditWeekStart(e.target.value)}
                                className="bg-secondary border-border" />
                            </div>
                          </div>
                          <Button onClick={() => updateUserCredits(u.user_id)} size="sm">
                            <Save className="w-4 h-4 mr-1" /> Save Changes
                          </Button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Settings Tab */}
        {tab === "settings" && (
          <div className="space-y-6">
            <div className="rounded-2xl border border-primary/30 bg-card p-5 space-y-4">
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Zap className="w-4 h-4 text-primary" /> Requesty AI — Main AI Provider
              </h2>
              <p className="text-xs text-muted-foreground">
                Set your Requesty AI API key here. This will be used as the primary AI provider for all models.
              </p>
              <div className="flex flex-col sm:flex-row gap-3">
                <Input
                  placeholder="Enter your Requesty AI API key"
                  value={requestyKey}
                  onChange={(e) => setRequestyKey(e.target.value)}
                  type={showRequestyKey ? "text" : "password"}
                  className="flex-1 bg-secondary border-border"
                />
                <button onClick={() => setShowRequestyKey(!showRequestyKey)}
                  className="p-2 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
                  {showRequestyKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
                <Button onClick={() => {
                  localStorage.setItem(REQUESTY_KEY, requestyKey);
                  toast.success(requestyKey ? "Requesty AI key saved!" : "Requesty AI key removed.");
                }}>
                  <Save className="w-4 h-4 mr-1" /> Save
                </Button>
              </div>
              {requestyKey && (
                <div className="flex items-center gap-2 text-xs text-green-400">
                  <Check className="w-3 h-3" /> Requesty AI is active
                </div>
              )}
            </div>

            <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Shield className="w-4 h-4 text-primary" /> Security
              </h2>
              <div className="flex items-center justify-between p-3 rounded-xl bg-secondary/50">
                <div>
                  <div className="text-sm font-medium text-foreground">Reset Admin Password</div>
                  <div className="text-xs text-muted-foreground">You'll need to set a new password</div>
                </div>
                <Button variant="destructive" size="sm" onClick={resetPassword}>Reset</Button>
              </div>
              <div className="flex items-center justify-between p-3 rounded-xl bg-secondary/50">
                <div>
                  <div className="text-sm font-medium text-foreground">Clear All API Keys</div>
                  <div className="text-xs text-muted-foreground">Remove all saved provider keys</div>
                </div>
                <Button variant="destructive" size="sm" onClick={() => {
                  setApiKeys([]); saveApiKeys([]); toast.success("All keys cleared");
                }}>Clear</Button>
              </div>
              <div className="flex items-center justify-between p-3 rounded-xl bg-secondary/50">
                <div>
                  <div className="text-sm font-medium text-foreground">Clear Custom Models</div>
                  <div className="text-xs text-muted-foreground">Remove all custom model definitions</div>
                </div>
                <Button variant="destructive" size="sm" onClick={() => {
                  setCustomModels([]); saveCustomModels([]); toast.success("All models cleared");
                }}>Clear</Button>
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Globe className="w-4 h-4 text-primary" /> Info
              </h2>
              <div className="space-y-2 text-sm text-muted-foreground">
                <p>• API keys are stored locally in your browser's localStorage.</p>
                <p>• Custom models will appear in the model selector across the app.</p>
                <p>• Disabling a key/model hides it from the UI without deleting it.</p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
