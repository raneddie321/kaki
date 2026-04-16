import { GitBranch, Plus, Minus, RotateCcw, Check, ChevronDown, FileEdit, FilePlus, FileX } from "lucide-react";
import { useState } from "react";
import type { FileNode } from "./FileExplorer";

interface GitPanelProps {
  files: FileNode[];
}

interface GitChange {
  path: string;
  type: "modified" | "added" | "deleted" | "untracked";
  staged: boolean;
}

export function GitPanel({ files }: GitPanelProps) {
  const [commitMsg, setCommitMsg] = useState("");
  const [branch] = useState("main");

  // Simulate changes based on files
  const [changes] = useState<GitChange[]>(() => {
    const allFiles: GitChange[] = [];
    const walk = (nodes: FileNode[]) => {
      for (const n of nodes) {
        if (n.type === "file" && n.content) {
          allFiles.push({ path: n.path, type: "modified", staged: false });
        }
        if (n.children) walk(n.children);
      }
    };
    walk(files);
    return allFiles.slice(0, 8); // show up to 8 simulated changes
  });

  const [stagedChanges, setStagedChanges] = useState<Set<string>>(new Set());

  const toggleStage = (path: string) => {
    setStagedChanges(prev => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path); else next.add(path);
      return next;
    });
  };

  const stageAll = () => setStagedChanges(new Set(changes.map(c => c.path)));
  const unstageAll = () => setStagedChanges(new Set());

  const staged = changes.filter(c => stagedChanges.has(c.path));
  const unstaged = changes.filter(c => !stagedChanges.has(c.path));

  const typeIcon = (type: GitChange["type"]) => {
    switch (type) {
      case "modified": return <FileEdit className="w-3 h-3 text-yellow-500" />;
      case "added": return <FilePlus className="w-3 h-3 text-green-500" />;
      case "deleted": return <FileX className="w-3 h-3 text-red-500" />;
      case "untracked": return <FilePlus className="w-3 h-3 text-green-400" />;
    }
  };

  const typeLetter = (type: GitChange["type"]) => {
    const map = { modified: "M", added: "A", deleted: "D", untracked: "U" };
    const colors = { modified: "text-yellow-500", added: "text-green-500", deleted: "text-red-500", untracked: "text-green-400" };
    return <span className={`text-[10px] font-mono font-bold ${colors[type]}`}>{map[type]}</span>;
  };

  return (
    <div className="flex flex-col h-full">
      <div className="p-3 border-b border-border">
        <div className="flex items-center gap-2 mb-2">
          <GitBranch className="w-4 h-4 text-muted-foreground" />
          <span className="text-xs text-foreground font-medium">{branch}</span>
        </div>
        <div className="flex gap-1">
          <input
            value={commitMsg}
            onChange={e => setCommitMsg(e.target.value)}
            placeholder="Commit message"
            className="flex-1 bg-secondary rounded px-2 py-1.5 text-xs text-foreground outline-none placeholder:text-muted-foreground border border-border focus:border-primary/50"
          />
        </div>
        <button
          disabled={staged.length === 0 || !commitMsg.trim()}
          className="w-full mt-2 py-1.5 rounded text-xs font-medium bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-30 transition-colors"
        >
          <Check className="w-3 h-3 inline mr-1" />
          Commit ({staged.length})
        </button>
      </div>

      <div className="flex-1 overflow-y-auto">
        {staged.length > 0 && (
          <div>
            <div className="flex items-center justify-between px-3 py-1.5">
              <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider">Staged Changes ({staged.length})</span>
              <button onClick={unstageAll} className="text-muted-foreground hover:text-foreground" title="Unstage All">
                <Minus className="w-3 h-3" />
              </button>
            </div>
            {staged.map(c => (
              <button key={c.path} onClick={() => toggleStage(c.path)}
                className="w-full flex items-center gap-2 px-3 py-1 text-xs text-foreground hover:bg-secondary/50 transition-colors">
                {typeIcon(c.type)}
                <span className="flex-1 truncate text-left">{c.path}</span>
                {typeLetter(c.type)}
              </button>
            ))}
          </div>
        )}

        <div>
          <div className="flex items-center justify-between px-3 py-1.5">
            <span className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider">Changes ({unstaged.length})</span>
            <button onClick={stageAll} className="text-muted-foreground hover:text-foreground" title="Stage All">
              <Plus className="w-3 h-3" />
            </button>
          </div>
          {unstaged.length === 0 && (
            <p className="text-[10px] text-muted-foreground px-3 py-2">No changes</p>
          )}
          {unstaged.map(c => (
            <button key={c.path} onClick={() => toggleStage(c.path)}
              className="w-full flex items-center gap-2 px-3 py-1 text-xs text-foreground hover:bg-secondary/50 transition-colors">
              {typeIcon(c.type)}
              <span className="flex-1 truncate text-left">{c.path}</span>
              {typeLetter(c.type)}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
