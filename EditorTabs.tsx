import { X, Circle } from "lucide-react";
import { FileIcon } from "./FileIcon";

export interface EditorTab {
  id: string;
  filename: string;
  path: string;
  language: string;
  content: string;
  isDirty?: boolean;
}

interface EditorTabsProps {
  tabs: EditorTab[];
  activeTabId: string | null;
  onSelectTab: (id: string) => void;
  onCloseTab: (id: string) => void;
}

function getFileIcon(filename: string): string {
  const ext = filename.split(".").pop()?.toLowerCase() || "";
  const map: Record<string, string> = {
    ts: "🟦", tsx: "⚛️", js: "🟨", jsx: "⚛️", html: "🌐", css: "🎨",
    json: "📋", md: "📝", py: "🐍", rb: "💎", go: "🐹", rs: "🦀",
    java: "☕", sql: "🗄️", yml: "⚙️", yaml: "⚙️", sh: "🐚", svg: "🎭",
  };
  return map[ext] || "📄";
}

export function EditorTabs({ tabs, activeTabId, onSelectTab, onCloseTab }: EditorTabsProps) {
  if (tabs.length === 0) return null;

  return (
    <div className="flex items-center bg-[hsl(220,15%,10%)] border-b border-border overflow-x-auto scrollbar-none">
      {tabs.map((tab) => {
        const isActive = tab.id === activeTabId;
        return (
          <div
            key={tab.id}
            onClick={() => onSelectTab(tab.id)}
            className={`group flex items-center gap-1.5 px-3 py-1.5 cursor-pointer border-r border-border text-xs min-w-0 max-w-[160px] transition-colors ${
              isActive
                ? "bg-[hsl(220,15%,13%)] text-foreground border-t-2 border-t-primary"
                : "text-muted-foreground hover:bg-[hsl(220,15%,12%)] border-t-2 border-t-transparent"
            }`}
          >
            <FileIcon filename={tab.filename} className="w-4 h-4" />
            <span className="truncate">{tab.filename}</span>
            {tab.isDirty && <Circle className="w-2 h-2 fill-current text-primary flex-shrink-0" />}
            <button
              onClick={(e) => { e.stopPropagation(); onCloseTab(tab.id); }}
              className="ml-auto p-0.5 rounded opacity-0 group-hover:opacity-100 hover:bg-secondary transition-all flex-shrink-0"
            >
              <X className="w-3 h-3" />
            </button>
          </div>
        );
      })}
    </div>
  );
}
