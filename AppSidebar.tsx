import {
  Plus, MessageSquare, Trash2, Pencil, X, Check, Zap,
  PanelLeftClose, FolderOpen, Terminal, Eye, Puzzle, ArrowLeft,
} from "lucide-react";
import { useState } from "react";
import type { Chat, ChatMode } from "@/lib/chatStore";

export type SidebarTab = "chats" | "files" | "extensions";

interface AppSidebarProps {
  chats: Chat[];
  activeChatId: string | null;
  activeTab: SidebarTab;
  onTabChange: (tab: SidebarTab) => void;
  onSelectChat: (id: string) => void;
  onNewChat: (mode: ChatMode) => void;
  onDeleteChat: (id: string) => void;
  onRenameChat: (id: string, title: string) => void;
  onClose: () => void;
  onToggleTerminal: () => void;
  onTogglePreview: () => void;
  terminalOpen: boolean;
  previewOpen: boolean;
  fileExplorerSlot?: React.ReactNode;
  extensionsSlot?: React.ReactNode;
  projectName?: string;
  onBack?: () => void;
}

export function AppSidebar({
  chats, activeChatId, activeTab, onTabChange,
  onSelectChat, onNewChat, onDeleteChat, onRenameChat, onClose,
  onToggleTerminal, onTogglePreview, terminalOpen, previewOpen,
  fileExplorerSlot, extensionsSlot, projectName, onBack,
}: AppSidebarProps) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState("");

  const startRename = (chat: Chat) => {
    setEditingId(chat.id);
    setEditValue(chat.title);
  };

  const confirmRename = () => {
    if (editingId && editValue.trim()) {
      onRenameChat(editingId, editValue.trim());
    }
    setEditingId(null);
  };

  return (
    <div className="w-64 h-full bg-sidebar flex flex-col border-r border-sidebar-border flex-shrink-0">
      {/* Header */}
      <div className="p-3 flex items-center justify-between border-b border-sidebar-border">
        <div className="flex items-center gap-2 min-w-0">
          {onBack && (
            <button onClick={onBack} className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-sidebar-hover transition-colors flex-shrink-0" title="Back to projects">
              <ArrowLeft className="w-4 h-4" />
            </button>
          )}
          <div className="w-7 h-7 rounded-lg bg-primary flex items-center justify-center flex-shrink-0">
            <span className="text-primary-foreground font-bold text-xs">{projectName ? projectName.charAt(0).toUpperCase() : "R"}</span>
          </div>
          <span className="font-semibold text-foreground text-sm truncate">{projectName || "RanEddie"}</span>
        </div>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors flex-shrink-0">
          <PanelLeftClose className="w-4 h-4" />
        </button>
      </div>

      {/* PrimeCODE */}
      <div className="p-2">
        <button
          onClick={() => onNewChat("primecode")}
          className="w-full flex items-center gap-2 px-3 py-2 rounded-lg bg-accent/30 border border-primary/30 text-primary hover:bg-accent/50 transition-colors text-xs font-medium"
        >
          <Zap className="w-3.5 h-3.5" />
          PrimeCODE
        </button>
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-sidebar-border">
        {([
          { id: "chats" as SidebarTab, icon: MessageSquare, label: "Chats" },
          { id: "files" as SidebarTab, icon: FolderOpen, label: "Files" },
          { id: "extensions" as SidebarTab, icon: Puzzle, label: "Extensions" },
        ]).map(({ id, icon: Icon, label }) => (
          <button
            key={id}
            onClick={() => onTabChange(id)}
            className={`flex-1 flex flex-col items-center gap-0.5 py-2 text-xs transition-colors ${
              activeTab === id
                ? "text-primary border-b-2 border-primary"
                : "text-muted-foreground hover:text-foreground"
            }`}
            title={label}
          >
            <Icon className="w-3.5 h-3.5" />
            <span className="text-[10px]">{label}</span>
          </button>
        ))}
      </div>

      {/* Panel toggles */}
      <div className="flex gap-1 p-2 border-b border-sidebar-border">
        <button
          onClick={onToggleTerminal}
          className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded text-xs transition-colors ${
            terminalOpen ? "bg-primary/20 text-primary" : "text-muted-foreground hover:bg-sidebar-hover hover:text-foreground"
          }`}
        >
          <Terminal className="w-3 h-3" />
          Terminal
        </button>
        <button
          onClick={onTogglePreview}
          className={`flex-1 flex items-center justify-center gap-1 px-2 py-1.5 rounded text-xs transition-colors ${
            previewOpen ? "bg-primary/20 text-primary" : "text-muted-foreground hover:bg-sidebar-hover hover:text-foreground"
          }`}
        >
          <Eye className="w-3 h-3" />
          Preview
        </button>
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-hidden">
        {activeTab === "chats" && (
          <div className="flex flex-col h-full">
            <div className="px-2 pt-2 pb-1">
              <button
                onClick={() => onNewChat("raneddie")}
                className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-muted-foreground hover:bg-sidebar-hover hover:text-foreground transition-colors text-xs"
              >
                <Plus className="w-3.5 h-3.5" />
                New Chat
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-2 pb-2">
              {chats.length === 0 && (
                <p className="text-xs text-muted-foreground px-3 py-2">No chats yet</p>
              )}
              {chats.map((chat) => (
                <div
                  key={chat.id}
                  className={`group flex items-center gap-1 px-2 py-1.5 rounded-lg cursor-pointer transition-colors mb-0.5 ${
                    chat.id === activeChatId ? "bg-sidebar-accent text-foreground" : "text-sidebar-foreground hover:bg-sidebar-hover"
                  }`}
                  onClick={() => onSelectChat(chat.id)}
                >
                  <MessageSquare className="w-3 h-3 flex-shrink-0" />
                  {editingId === chat.id ? (
                    <div className="flex-1 flex items-center gap-1">
                      <input
                        value={editValue}
                        onChange={(e) => setEditValue(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && confirmRename()}
                        className="flex-1 bg-input text-foreground text-xs px-1.5 py-0.5 rounded border-none outline-none"
                        autoFocus
                        onClick={(e) => e.stopPropagation()}
                      />
                      <button onClick={(e) => { e.stopPropagation(); confirmRename(); }} className="text-primary">
                        <Check className="w-3 h-3" />
                      </button>
                      <button onClick={(e) => { e.stopPropagation(); setEditingId(null); }} className="text-muted-foreground">
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  ) : (
                    <>
                      <span className="flex-1 text-xs truncate">
                        {chat.mode === "primecode" && <Zap className="w-3 h-3 inline mr-1 text-primary" />}
                        {chat.title}
                      </span>
                      <div className="hidden group-hover:flex items-center gap-0.5">
                        <button onClick={(e) => { e.stopPropagation(); startRename(chat); }} className="p-0.5 hover:text-primary">
                          <Pencil className="w-3 h-3" />
                        </button>
                        <button onClick={(e) => { e.stopPropagation(); onDeleteChat(chat.id); }} className="p-0.5 hover:text-destructive">
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </div>
                    </>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
        {activeTab === "files" && fileExplorerSlot}
        {activeTab === "extensions" && extensionsSlot}
      </div>
    </div>
  );
}
