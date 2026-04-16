import { useState, useCallback } from "react";
import { Plus, X } from "lucide-react";
import { TerminalPanel } from "./TerminalPanel";

interface SplitTerminalProps {
  onCreateFile?: (filename: string, content: string) => void;
  onEditFile?: (filename: string) => void;
  onRunApp?: () => void;
}

interface TerminalTab {
  id: string;
  label: string;
}

export function SplitTerminal({ onCreateFile, onEditFile, onRunApp }: SplitTerminalProps) {
  const [tabs, setTabs] = useState<TerminalTab[]>([{ id: "1", label: "Terminal 1" }]);
  const [activeId, setActiveId] = useState("1");
  const [counter, setCounter] = useState(2);

  const addTerminal = useCallback(() => {
    const newId = String(counter);
    setTabs(prev => [...prev, { id: newId, label: `Terminal ${counter}` }]);
    setActiveId(newId);
    setCounter(c => c + 1);
  }, [counter]);

  const closeTerminal = useCallback((id: string) => {
    setTabs(prev => {
      const next = prev.filter(t => t.id !== id);
      if (next.length === 0) {
        const newTab = { id: String(counter), label: `Terminal ${counter}` };
        setCounter(c => c + 1);
        setActiveId(newTab.id);
        return [newTab];
      }
      if (activeId === id) setActiveId(next[next.length - 1].id);
      return next;
    });
  }, [activeId, counter]);

  const splitTerminal = useCallback(() => {
    addTerminal();
  }, [addTerminal]);

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center bg-sidebar border-b border-border h-8 px-1 gap-0.5 flex-shrink-0">
        {tabs.map(tab => (
          <div key={tab.id}
            className={`flex items-center gap-1 px-2 py-1 text-[10px] rounded cursor-pointer transition-colors group ${
              tab.id === activeId ? "bg-background text-foreground" : "text-muted-foreground hover:text-foreground hover:bg-secondary/50"
            }`}
            onClick={() => setActiveId(tab.id)}
          >
            <span>{tab.label}</span>
            <button onClick={(e) => { e.stopPropagation(); closeTerminal(tab.id); }}
              className="opacity-0 group-hover:opacity-100 hover:text-destructive transition-opacity p-0.5">
              <X className="w-2.5 h-2.5" />
            </button>
          </div>
        ))}
        <button onClick={addTerminal} className="p-1 text-muted-foreground hover:text-foreground transition-colors ml-0.5" title="New Terminal">
          <Plus className="w-3 h-3" />
        </button>
        <button onClick={splitTerminal} className="p-1 text-muted-foreground hover:text-foreground transition-colors text-[10px]" title="Split Terminal">
          ⊞
        </button>
      </div>
      <div className="flex-1 min-h-0 relative">
        {tabs.map(tab => (
          <div key={tab.id} className={`absolute inset-0 ${tab.id === activeId ? "block" : "hidden"}`}>
            <TerminalPanel onCreateFile={onCreateFile} onEditFile={onEditFile} onRunApp={onRunApp} />
          </div>
        ))}
      </div>
    </div>
  );
}
