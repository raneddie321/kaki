import { useState, useEffect, useRef, useCallback } from "react";
import { Search, File, Terminal, Settings, Palette, GitBranch, Puzzle, MessageSquare, Play, FolderOpen, FilePlus, FolderPlus, Keyboard } from "lucide-react";

export interface Command {
  id: string;
  label: string;
  category: string;
  icon: typeof Search;
  shortcut?: string;
  action: () => void;
}

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
  commands: Command[];
}

export function CommandPalette({ open, onClose, commands }: CommandPaletteProps) {
  const [query, setQuery] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      setQuery("");
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === "P") {
        e.preventDefault();
        if (open) onClose();
        else onClose(); // toggle handled by parent
      }
      if (e.key === "Escape" && open) onClose();
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open, onClose]);

  if (!open) return null;

  const filtered = commands.filter((c) =>
    c.label.toLowerCase().includes(query.toLowerCase()) ||
    c.category.toLowerCase().includes(query.toLowerCase())
  );

  const grouped = filtered.reduce<Record<string, Command[]>>((acc, cmd) => {
    (acc[cmd.category] = acc[cmd.category] || []).push(cmd);
    return acc;
  }, {});

  const handleSelect = (cmd: Command) => {
    cmd.action();
    onClose();
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh]" onClick={onClose}>
      <div className="absolute inset-0 bg-black/50" />
      <div
        className="relative w-full max-w-lg bg-card border border-border rounded-xl shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
          <Search className="w-4 h-4 text-muted-foreground flex-shrink-0" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Type a command or search..."
            className="flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground"
          />
          <kbd className="text-[10px] px-1.5 py-0.5 rounded bg-secondary text-muted-foreground font-mono">ESC</kbd>
        </div>
        <div className="max-h-[50vh] overflow-y-auto">
          {Object.keys(grouped).length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-8">No commands found</p>
          )}
          {Object.entries(grouped).map(([cat, cmds]) => (
            <div key={cat}>
              <div className="px-4 py-1.5 text-[10px] font-medium text-muted-foreground uppercase tracking-wider bg-secondary/30">
                {cat}
              </div>
              {cmds.map((cmd) => (
                <button
                  key={cmd.id}
                  onClick={() => handleSelect(cmd)}
                  className="w-full flex items-center gap-3 px-4 py-2 text-sm text-foreground hover:bg-secondary/50 transition-colors"
                >
                  <cmd.icon className="w-4 h-4 text-muted-foreground flex-shrink-0" />
                  <span className="flex-1 text-left">{cmd.label}</span>
                  {cmd.shortcut && (
                    <kbd className="text-[10px] px-1.5 py-0.5 rounded bg-secondary text-muted-foreground font-mono">{cmd.shortcut}</kbd>
                  )}
                </button>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
