import { Download, Eye, Tag, X } from "lucide-react";
import { useState } from "react";
import { CodePreview } from "./CodePreview";

interface CodeBlockProps {
  code: string;
  language: string;
  filename?: string;
  onOpenPreview?: (code: string, language: string, filename?: string) => void;
}

export function CodeBlock({ code, language, filename, onOpenPreview }: CodeBlockProps) {
  const [showInlinePreview, setShowInlinePreview] = useState(false);
  const [tags, setTags] = useState<string[]>([]);
  const [showTagInput, setShowTagInput] = useState(false);
  const [tagValue, setTagValue] = useState("");

  const canPreview = ["html", "javascript", "js", "jsx", "tsx", "css"].includes(language.toLowerCase());

  const handleDownload = () => {
    const ext = getExtension(language);
    const name = filename || `code.${ext}`;
    const blob = new Blob([code], { type: "text/plain" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    a.click();
    URL.revokeObjectURL(url);
  };

  const addTag = () => {
    const t = tagValue.trim();
    if (t && !tags.includes(t)) {
      setTags([...tags, t]);
    }
    setTagValue("");
    setShowTagInput(false);
  };

  const removeTag = (tag: string) => {
    setTags(tags.filter((t) => t !== tag));
  };

  return (
    <div className="my-3 rounded-lg overflow-hidden border border-code-border animate-fade-in-up">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-code-header">
        <span className="text-xs font-mono text-muted-foreground">
          {filename || language}
        </span>
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setShowTagInput(!showTagInput)}
            className="flex items-center gap-1 text-xs px-2 py-1 rounded bg-secondary text-muted-foreground hover:text-foreground transition-colors"
            title="Tag this code"
          >
            <Tag className="w-3 h-3" />
          </button>
          {canPreview && (
            <button
              onClick={() => {
                if (onOpenPreview) {
                  onOpenPreview(code, language, filename);
                } else {
                  setShowInlinePreview(!showInlinePreview);
                }
              }}
              className="flex items-center gap-1 text-xs px-2 py-1 rounded bg-primary/20 text-primary hover:bg-primary/30 transition-colors"
            >
              <Eye className="w-3 h-3" />
              Preview
            </button>
          )}
          <button
            onClick={handleDownload}
            className="flex items-center gap-1 text-xs px-2 py-1 rounded bg-secondary text-secondary-foreground hover:bg-secondary/80 transition-colors"
          >
            <Download className="w-3 h-3" />
            Download
          </button>
        </div>
      </div>

      {/* Tags */}
      {(tags.length > 0 || showTagInput) && (
        <div className="flex items-center flex-wrap gap-1.5 px-4 py-1.5 bg-code-header/50 border-t border-code-border">
          {tags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-primary/20 text-primary"
            >
              {tag}
              <button onClick={() => removeTag(tag)} className="hover:text-destructive">
                <X className="w-2.5 h-2.5" />
              </button>
            </span>
          ))}
          {showTagInput && (
            <input
              value={tagValue}
              onChange={(e) => setTagValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") addTag();
                if (e.key === "Escape") setShowTagInput(false);
              }}
              onBlur={addTag}
              placeholder="Add tag..."
              autoFocus
              className="text-xs bg-transparent border-none outline-none text-foreground placeholder:text-muted-foreground w-20"
            />
          )}
        </div>
      )}

      {/* Code */}
      <pre className="p-4 overflow-x-auto bg-code-bg">
        <code className="text-sm font-mono text-foreground leading-relaxed">{code}</code>
      </pre>

      {/* Inline preview */}
      {showInlinePreview && (
        <CodePreview code={code} language={language} onClose={() => setShowInlinePreview(false)} />
      )}
    </div>
  );
}

function getExtension(lang: string): string {
  const map: Record<string, string> = {
    javascript: "js", js: "js", typescript: "ts", ts: "ts",
    jsx: "jsx", tsx: "tsx", python: "py", html: "html",
    css: "css", json: "json", bash: "sh", shell: "sh",
    sql: "sql", markdown: "md", yaml: "yml",
  };
  return map[lang.toLowerCase()] || "txt";
}
