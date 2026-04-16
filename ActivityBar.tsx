import {
  Files, Search, GitBranch, Puzzle, Play, Bug, MessageSquare,
  Settings, User, LayoutGrid, Users,
} from "lucide-react";
import { useEffect, useState } from "react";

export type ActivityTab = "explorer" | "search" | "git" | "debug" | "extensions" | "chat" | "teams" | "run" | string;

export interface InstalledExtensionInfo {
  id: string;
  name: string;
  icon: string;
}

interface ActivityBarProps {
  activeTab: ActivityTab;
  onTabChange: (tab: ActivityTab) => void;
  installedExtensionCount?: number;
  installedExtensions?: InstalledExtensionInfo[];
}

const TOP_ITEMS: { id: ActivityTab; icon: typeof Files; label: string }[] = [
  { id: "explorer", icon: Files, label: "Explorer" },
  { id: "search", icon: Search, label: "Search" },
  { id: "git", icon: GitBranch, label: "Source Control" },
  { id: "debug", icon: Bug, label: "Run & Debug" },
  { id: "extensions", icon: Puzzle, label: "Extensions" },
  { id: "chat", icon: MessageSquare, label: "Chat" },
  { id: "teams", icon: Users, label: "Teams+" },
];

export function ActivityBar({ activeTab, onTabChange, installedExtensionCount = 0, installedExtensions = [] }: ActivityBarProps) {
  return (
    <div className="w-12 h-full bg-[hsl(220,18%,6%)] flex flex-col items-center py-1 border-r border-sidebar-border flex-shrink-0">
      {TOP_ITEMS.map(({ id, icon: Icon, label }) => (
        <button
          key={id}
          onClick={() => onTabChange(id)}
          className={`relative w-12 h-12 flex items-center justify-center transition-colors group ${
            activeTab === id
              ? "text-foreground border-l-2 border-primary bg-sidebar-hover/30"
              : "text-muted-foreground hover:text-foreground border-l-2 border-transparent"
          }`}
          title={label}
        >
          <Icon className="w-[22px] h-[22px]" />
          {id === "extensions" && installedExtensionCount > 0 && (
            <span className="absolute top-1.5 right-1.5 w-4 h-4 rounded-full bg-primary text-[9px] text-primary-foreground flex items-center justify-center font-bold">
              {installedExtensionCount > 9 ? "9+" : installedExtensionCount}
            </span>
          )}
        </button>
      ))}

      {/* Installed extensions */}
      {installedExtensions.length > 0 && (
        <>
          <div className="w-6 border-t border-sidebar-border my-1" />
          <div className="flex-1 overflow-y-auto overflow-x-hidden flex flex-col items-center gap-0.5 scrollbar-none min-h-0">
            {installedExtensions.map((ext) => (
              <button
                key={ext.id}
                onClick={() => onTabChange(`ext-${ext.id}`)}
                className={`relative w-12 h-12 flex items-center justify-center transition-colors ${
                  activeTab === `ext-${ext.id}`
                    ? "text-foreground border-l-2 border-primary bg-sidebar-hover/30"
                    : "text-muted-foreground hover:text-foreground border-l-2 border-transparent"
                }`}
                title={ext.name}
              >
                {ext.icon ? (
                  <img src={ext.icon} alt={ext.name} className="w-[22px] h-[22px] rounded object-contain" onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                ) : (
                  <Puzzle className="w-[22px] h-[22px]" />
                )}
              </button>
            ))}
          </div>
        </>
      )}

      <div className="flex-1" />
      <button onClick={() => onTabChange("settings" as ActivityTab)}
        className={`w-12 h-12 flex items-center justify-center transition-colors ${
          activeTab === "settings" ? "text-foreground" : "text-muted-foreground hover:text-foreground"
        }`} title="Settings">
        <Settings className="w-[22px] h-[22px]" />
      </button>
    </div>
  );
}
