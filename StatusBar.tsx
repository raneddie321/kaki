import { GitBranch, Bell, Check, AlertTriangle, Terminal, LogOut } from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

interface StatusBarProps {
  filename?: string;
  language?: string;
  line?: number;
  column?: number;
  encoding?: string;
  eol?: string;
  branch?: string;
  problems?: { errors: number; warnings: number };
  terminalOpen?: boolean;
  onToggleTerminal?: () => void;
}

export function StatusBar({
  language = "Plain Text",
  line = 1,
  column = 1,
  encoding = "UTF-8",
  eol = "LF",
  branch = "main",
  problems = { errors: 0, warnings: 0 },
  terminalOpen,
  onToggleTerminal,
}: StatusBarProps) {
  const { username, signOut } = useAuth();

  return (
    <div className="h-6 bg-[hsl(270,60%,30%)] text-[11px] text-white/90 flex items-center px-2 select-none flex-shrink-0 justify-between">
      <div className="flex items-center gap-3">
        <span className="flex items-center gap-1 cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">
          <GitBranch className="w-3 h-3" />
          {branch}
        </span>
        <span className="flex items-center gap-1.5 cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">
          {problems.errors > 0 ? <AlertTriangle className="w-3 h-3" /> : <Check className="w-3 h-3" />}
          {problems.errors} <AlertTriangle className="w-3 h-3 text-yellow-300" /> {problems.warnings}
        </span>
      </div>
      <div className="flex items-center gap-3">
        {username && (
          <span className="px-1.5 py-0.5 rounded text-white/70">@{username}</span>
        )}
        <span className="cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">
          Ln {line}, Col {column}
        </span>
        <span className="cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">{encoding}</span>
        <span className="cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">{eol}</span>
        <span className="cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">{language}</span>
        {onToggleTerminal && (
          <button
            onClick={onToggleTerminal}
            className={`flex items-center gap-1 cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded transition-colors ${terminalOpen ? 'bg-white/15' : ''}`}
            title="Toggle Terminal (Ctrl+`)"
          >
            <Terminal className="w-3 h-3" />
            Terminal
          </button>
        )}
        <button
          onClick={signOut}
          className="flex items-center gap-1 cursor-pointer hover:bg-red-500/30 px-1.5 py-0.5 rounded transition-colors"
          title="Sign Out"
        >
          <LogOut className="w-3 h-3" />
        </button>
        <span className="flex items-center gap-1 cursor-pointer hover:bg-white/10 px-1.5 py-0.5 rounded">
          <Bell className="w-3 h-3" />
        </span>
      </div>
    </div>
  );
}
