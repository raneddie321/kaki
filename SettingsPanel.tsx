import { useState, useEffect } from "react";
import { ArrowLeft, Sun, Moon, Type, Keyboard } from "lucide-react";

interface SettingsPanelProps {
  onClose: () => void;
}

interface EditorSettings {
  fontSize: number;
  tabSize: number;
  wordWrap: boolean;
  minimap: boolean;
  lineNumbers: boolean;
  theme: "dark" | "light";
  fontFamily: string;
  autoSave: boolean;
  formatOnSave: boolean;
}

const DEFAULT_SETTINGS: EditorSettings = {
  fontSize: 14,
  tabSize: 2,
  wordWrap: true,
  minimap: true,
  lineNumbers: true,
  theme: "dark",
  fontFamily: "JetBrains Mono, monospace",
  autoSave: true,
  formatOnSave: false,
};

function loadSettings(): EditorSettings {
  try {
    const raw = localStorage.getItem("primecode_settings");
    return raw ? { ...DEFAULT_SETTINGS, ...JSON.parse(raw) } : DEFAULT_SETTINGS;
  } catch {
    return DEFAULT_SETTINGS;
  }
}

function saveSettings(s: EditorSettings) {
  localStorage.setItem("primecode_settings", JSON.stringify(s));
  window.dispatchEvent(new Event("settings-changed"));
}

export function SettingsPanel({ onClose }: SettingsPanelProps) {
  const [settings, setSettings] = useState<EditorSettings>(loadSettings);

  const update = <K extends keyof EditorSettings>(key: K, value: EditorSettings[K]) => {
    const next = { ...settings, [key]: value };
    setSettings(next);
    saveSettings(next);
  };

  return (
    <div className="flex flex-col h-full">
      <div className="px-4 py-3 border-b border-border flex items-center gap-3">
        <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="w-4 h-4" />
        </button>
        <h2 className="text-sm font-semibold text-foreground">Settings</h2>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* Appearance */}
        <div>
          <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Appearance</h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs text-foreground">Theme</span>
              <div className="flex rounded-lg overflow-hidden border border-border">
                <button onClick={() => update("theme", "dark")}
                  className={`flex items-center gap-1.5 px-3 py-1.5 text-xs ${settings.theme === "dark" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-secondary"}`}>
                  <Moon className="w-3 h-3" /> Dark
                </button>
                <button onClick={() => update("theme", "light")}
                  className={`flex items-center gap-1.5 px-3 py-1.5 text-xs ${settings.theme === "light" ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-secondary"}`}>
                  <Sun className="w-3 h-3" /> Light
                </button>
              </div>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-foreground">Font Size</span>
              <div className="flex items-center gap-2">
                <button onClick={() => update("fontSize", Math.max(10, settings.fontSize - 1))}
                  className="w-6 h-6 rounded bg-secondary text-foreground text-xs hover:bg-secondary/80">−</button>
                <span className="text-xs text-foreground w-6 text-center">{settings.fontSize}</span>
                <button onClick={() => update("fontSize", Math.min(24, settings.fontSize + 1))}
                  className="w-6 h-6 rounded bg-secondary text-foreground text-xs hover:bg-secondary/80">+</button>
              </div>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-xs text-foreground">Font Family</span>
              <select value={settings.fontFamily} onChange={e => update("fontFamily", e.target.value)}
                className="text-xs bg-secondary text-foreground border border-border rounded px-2 py-1">
                <option value="JetBrains Mono, monospace">JetBrains Mono</option>
                <option value="Fira Code, monospace">Fira Code</option>
                <option value="Source Code Pro, monospace">Source Code Pro</option>
                <option value="Consolas, monospace">Consolas</option>
                <option value="monospace">System Mono</option>
              </select>
            </div>
          </div>
        </div>

        {/* Editor */}
        <div>
          <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Editor</h3>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs text-foreground">Tab Size</span>
              <div className="flex rounded-lg overflow-hidden border border-border">
                {[2, 4].map(n => (
                  <button key={n} onClick={() => update("tabSize", n)}
                    className={`px-3 py-1.5 text-xs ${settings.tabSize === n ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-secondary"}`}>
                    {n}
                  </button>
                ))}
              </div>
            </div>
            {([
              ["wordWrap", "Word Wrap"],
              ["minimap", "Minimap"],
              ["lineNumbers", "Line Numbers"],
              ["autoSave", "Auto Save"],
              ["formatOnSave", "Format on Save"],
            ] as const).map(([key, label]) => (
              <label key={key} className="flex items-center justify-between cursor-pointer">
                <span className="text-xs text-foreground">{label}</span>
                <div className={`w-9 h-5 rounded-full transition-colors relative cursor-pointer ${settings[key] ? "bg-primary" : "bg-secondary"}`}
                  onClick={() => update(key, !settings[key])}>
                  <div className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform ${settings[key] ? "translate-x-4" : "translate-x-0.5"}`} />
                </div>
              </label>
            ))}
          </div>
        </div>

        {/* Keyboard Shortcuts */}
        <div>
          <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3 flex items-center gap-1.5">
            <Keyboard className="w-3 h-3" /> Keyboard Shortcuts
          </h3>
          <div className="space-y-2 text-xs">
            {[
              ["Ctrl + Shift + P", "Command Palette"],
              ["Ctrl + `", "Toggle Terminal"],
              ["Ctrl + B", "Toggle Sidebar"],
              ["Ctrl + S", "Save File"],
              ["Ctrl + Shift + F", "Search in Files"],
            ].map(([key, desc]) => (
              <div key={key} className="flex items-center justify-between">
                <span className="text-muted-foreground">{desc}</span>
                <kbd className="px-2 py-0.5 rounded bg-secondary text-foreground text-[10px] font-mono border border-border">{key}</kbd>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
