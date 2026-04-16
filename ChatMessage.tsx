import { Bot, User, FileCode, Loader2, Check } from "lucide-react";
import { useMemo } from "react";
import { extractFileOperations } from "@/lib/fileOperations";

interface ChatMessageProps {
  role: "user" | "assistant";
  content: string;
  isStreaming?: boolean;
  mode?: string;
  onOpenPreview?: (code: string, language: string, filename?: string) => void;
}

interface ParsedPart {
  type: "text" | "file-op";
  content: string;
  filename?: string;
  operation?: string;
  language?: string;
}

function parseContent(content: string, isStreaming?: boolean): ParsedPart[] {
  const parts: ParsedPart[] = [];

  const extracted = extractFileOperations(content);
  const fileOps = extracted.map(op => ({
    index: op.index,
    end: op.end,
    filename: op.path,
    op: op.type === "create" ? "creating" : op.type === "modify" ? "changing" : "deleting",
  }));

  // Also hide any raw code blocks not already covered
  const codeRegex = /```(?:\w+)?\n[\s\S]*?```/g;
  let match;
  const hiddenRanges: { index: number; end: number }[] = [];
  while ((match = codeRegex.exec(content)) !== null) {
    const s = match.index, e = match.index + match[0].length;
    const covered = fileOps.some(op => s < op.end && op.index < e);
    if (!covered) hiddenRanges.push({ index: s, end: e });
  }

  const allRanges = [
    ...fileOps.map(op => ({ ...op, kind: "file-op" as const })),
    ...hiddenRanges.map(r => ({ ...r, kind: "hidden" as const, filename: "", op: "" })),
  ].sort((a, b) => a.index - b.index);

  if (allRanges.length === 0) {
    if (content.trim()) parts.push({ type: "text", content });
    return parts;
  }

  let lastIndex = 0;
  const seenFiles = new Set<string>();

  for (const item of allRanges) {
    if (item.index > lastIndex) {
      const text = content.slice(lastIndex, item.index).trim();
      if (text) parts.push({ type: "text", content: text });
    }
    if (item.kind === "file-op" && !seenFiles.has(item.filename)) {
      seenFiles.add(item.filename);
      parts.push({ type: "file-op", content: "", filename: item.filename, operation: item.op });
    }
    lastIndex = item.end;
  }

  if (lastIndex < content.length) {
    const text = content.slice(lastIndex).trim();
    if (text) parts.push({ type: "text", content: text });
  }

  return parts;
}

export function ChatMessage({ role, content, isStreaming, mode, onOpenPreview }: ChatMessageProps) {
  const parts = useMemo(() => parseContent(content, isStreaming), [content, isStreaming]);
  const botName = mode === "primecode" ? "PrimeCODE" : "RanEddie";

  return (
    <div className={`flex gap-3 py-4 px-4 ${role === "assistant" ? "bg-card/50" : ""}`}>
      <div className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center ${
        role === "assistant" ? "bg-primary/20 text-primary" : "bg-secondary text-secondary-foreground"
      }`}>
        {role === "assistant" ? <Bot className="w-4 h-4" /> : <User className="w-4 h-4" />}
      </div>
      <div className="flex-1 min-w-0">
        <span className="text-xs font-medium text-muted-foreground mb-1 block">
          {role === "assistant" ? botName : "You"}
        </span>
        <div className="text-sm leading-relaxed space-y-1.5">
          {parts.map((part, i) =>
            part.type === "file-op" ? (
              <div key={i} className="flex items-center gap-2 px-3 py-2 rounded-lg bg-primary/5 border border-primary/10">
                <FileCode className="w-4 h-4 text-primary flex-shrink-0" />
                <span className="text-xs font-medium text-foreground">{part.filename}</span>
                <span className="text-[10px] text-muted-foreground">—</span>
                {isStreaming ? (
                  <span className="flex items-center gap-1.5 text-[10px] text-primary">
                    <Loader2 className="w-3 h-3 animate-spin" />
                    {part.operation}...
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-[10px] text-primary">
                    <Check className="w-3 h-3" />
                    done
                  </span>
                )}
              </div>
            ) : (
              <span key={i} className="whitespace-pre-wrap">{part.content}</span>
            )
          )}
          {isStreaming && <span className="typing-cursor" />}
        </div>
      </div>
    </div>
  );
}
