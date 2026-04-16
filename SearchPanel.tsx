import { Search, Replace, ChevronDown, ChevronRight, File } from "lucide-react";
import { useState, useMemo } from "react";
import type { FileNode } from "./FileExplorer";
import { getAllFiles } from "./FileExplorer";

interface SearchPanelProps {
  files: FileNode[];
  onFileSelect: (file: FileNode) => void;
}

interface SearchResult {
  file: FileNode;
  matches: { line: number; text: string; col: number }[];
}

export function SearchPanel({ files, onFileSelect }: SearchPanelProps) {
  const [query, setQuery] = useState("");
  const [replaceText, setReplaceText] = useState("");
  const [showReplace, setShowReplace] = useState(false);
  const [caseSensitive, setCaseSensitive] = useState(false);
  const [wholeWord, setWholeWord] = useState(false);
  const [useRegex, setUseRegex] = useState(false);

  const allFiles = useMemo(() => getAllFiles(files), [files]);

  const results = useMemo<SearchResult[]>(() => {
    if (!query.trim()) return [];
    const out: SearchResult[] = [];
    for (const file of allFiles) {
      if (!file.content) continue;
      const lines = file.content.split("\n");
      const matches: SearchResult["matches"] = [];
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const searchIn = caseSensitive ? line : line.toLowerCase();
        const searchFor = caseSensitive ? query : query.toLowerCase();
        const col = searchIn.indexOf(searchFor);
        if (col !== -1) {
          matches.push({ line: i + 1, text: line.trim(), col });
        }
      }
      if (matches.length > 0) out.push({ file, matches });
    }
    return out;
  }, [query, allFiles, caseSensitive]);

  const totalMatches = results.reduce((s, r) => s + r.matches.length, 0);

  return (
    <div className="flex flex-col h-full">
      <div className="p-3 space-y-2 border-b border-border">
        <div className="flex items-center gap-1">
          <button onClick={() => setShowReplace(!showReplace)} className="p-1 text-muted-foreground hover:text-foreground">
            {showReplace ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
          </button>
          <div className="flex-1 flex items-center gap-1 bg-secondary rounded px-2 py-1.5">
            <Search className="w-3.5 h-3.5 text-muted-foreground" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search"
              className="flex-1 bg-transparent text-xs text-foreground outline-none placeholder:text-muted-foreground"
            />
          </div>
        </div>
        <div className="flex gap-1 ml-6">
          <button onClick={() => setCaseSensitive(!caseSensitive)}
            className={`px-1.5 py-0.5 text-[10px] rounded font-mono ${caseSensitive ? "bg-primary/20 text-primary" : "text-muted-foreground hover:text-foreground"}`}>
            Aa
          </button>
          <button onClick={() => setWholeWord(!wholeWord)}
            className={`px-1.5 py-0.5 text-[10px] rounded font-mono border ${wholeWord ? "border-primary/30 text-primary" : "border-transparent text-muted-foreground hover:text-foreground"}`}>
            Ab
          </button>
          <button onClick={() => setUseRegex(!useRegex)}
            className={`px-1.5 py-0.5 text-[10px] rounded font-mono ${useRegex ? "bg-primary/20 text-primary" : "text-muted-foreground hover:text-foreground"}`}>
            .*
          </button>
        </div>
        {showReplace && (
          <div className="flex items-center gap-1 ml-6">
            <div className="flex-1 flex items-center gap-1 bg-secondary rounded px-2 py-1.5">
              <Replace className="w-3.5 h-3.5 text-muted-foreground" />
              <input
                value={replaceText}
                onChange={(e) => setReplaceText(e.target.value)}
                placeholder="Replace"
                className="flex-1 bg-transparent text-xs text-foreground outline-none placeholder:text-muted-foreground"
              />
            </div>
          </div>
        )}
        {query && (
          <p className="text-[10px] text-muted-foreground ml-6">
            {totalMatches} result{totalMatches !== 1 ? "s" : ""} in {results.length} file{results.length !== 1 ? "s" : ""}
          </p>
        )}
      </div>
      <div className="flex-1 overflow-y-auto">
        {results.map((r) => (
          <ResultGroup key={r.file.path} result={r} onFileSelect={onFileSelect} query={query} />
        ))}
      </div>
    </div>
  );
}

function ResultGroup({ result, onFileSelect, query }: { result: SearchResult; onFileSelect: (f: FileNode) => void; query: string }) {
  const [expanded, setExpanded] = useState(true);
  return (
    <div>
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-1.5 px-3 py-1 text-xs text-foreground hover:bg-secondary/50 transition-colors"
      >
        {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
        <File className="w-3 h-3 text-muted-foreground" />
        <span className="truncate">{result.file.name}</span>
        <span className="ml-auto text-[10px] text-muted-foreground bg-secondary rounded-full px-1.5">{result.matches.length}</span>
      </button>
      {expanded && result.matches.map((m, i) => (
        <button
          key={i}
          onClick={() => onFileSelect(result.file)}
          className="w-full flex items-center gap-2 pl-10 pr-3 py-0.5 text-[11px] text-muted-foreground hover:bg-secondary/30 transition-colors text-left"
        >
          <span className="text-muted-foreground/60 w-6 text-right flex-shrink-0">{m.line}</span>
          <span className="truncate">
            {highlightMatch(m.text, query)}
          </span>
        </button>
      ))}
    </div>
  );
}

function highlightMatch(text: string, query: string) {
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx === -1) return text;
  return (
    <>
      {text.slice(0, idx)}
      <span className="bg-yellow-500/30 text-yellow-200 rounded-sm px-0.5">{text.slice(idx, idx + query.length)}</span>
      {text.slice(idx + query.length)}
    </>
  );
}
