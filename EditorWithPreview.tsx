import { useState } from "react";
import { X, Save, Code, Eye } from "lucide-react";
import { MonacoEditor } from "./MonacoEditor";

interface EditorWithPreviewProps {
  code: string;
  language: string;
  filename?: string;
  onClose: () => void;
  onSave?: (content: string) => void;
  onFixWithAI?: (errorMessage: string, code: string, filename?: string) => void;
}

function isPreviewable(language: string): boolean {
  return ["html", "css", "javascript", "js", "jsx", "tsx", "svg", "xml"].includes(language);
}

function getPreviewContent(code: string, language: string): string {
  if (language === "html" || language === "svg" || language === "xml") return code;
  if (["javascript", "js", "jsx", "tsx"].includes(language)) {
    return `<!DOCTYPE html>
<html><head><style>body{background:#1a1a2e;color:#eee;font-family:system-ui,sans-serif;padding:20px}</style></head>
<body><div id="root"></div>
<script>
try { ${code} } catch(e) { document.getElementById('root').innerText = 'Error: ' + e.message; }
</script></body></html>`;
  }
  if (language === "css") {
    return `<!DOCTYPE html>
<html><head><style>${code}</style></head>
<body><div class="preview"><h1>CSS Preview</h1><p>Styles applied.</p><button>Button</button><a href="#">Link</a><div class="box">Box</div></div></body></html>`;
  }
  return `<!DOCTYPE html><html><head><style>body{background:#1a1a2e;color:#eee;font-family:monospace;padding:20px;white-space:pre-wrap}</style></head><body>${code}</body></html>`;
}

export function EditorWithPreview({ code, language, filename, onClose, onSave, onFixWithAI }: EditorWithPreviewProps) {
  const [view, setView] = useState<"code" | "preview">("code");
  const [currentCode, setCurrentCode] = useState(code);
  const canPreview = isPreviewable(language);

  const handleSaveFromEditor = (content: string) => {
    setCurrentCode(content);
    onSave?.(content);
  };

  if (view === "preview" && canPreview) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-card">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-foreground truncate">
              {filename || "Untitled"}
            </span>
            <div className="flex items-center bg-secondary rounded-lg p-0.5">
              <button
                onClick={() => setView("code")}
                className="flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-colors text-muted-foreground hover:text-foreground"
              >
                <Code className="w-3.5 h-3.5" /> Code
              </button>
              <button
                onClick={() => setView("preview")}
                className="flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-colors bg-primary text-primary-foreground"
              >
                <Eye className="w-3.5 h-3.5" /> Preview
              </button>
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="flex-1 bg-background min-h-0">
          <iframe
            srcDoc={getPreviewContent(currentCode, language)}
            className="w-full h-full border-none"
            sandbox="allow-scripts"
            title="Preview"
          />
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {canPreview && (
        <div className="absolute top-2 right-14 z-10 flex items-center bg-secondary rounded-lg p-0.5">
          <button
            onClick={() => setView("code")}
            className="flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-colors bg-primary text-primary-foreground"
          >
            <Code className="w-3.5 h-3.5" /> Code
          </button>
          <button
            onClick={() => setView("preview")}
            className="flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-medium transition-colors text-muted-foreground hover:text-foreground"
          >
            <Eye className="w-3.5 h-3.5" /> Preview
          </button>
        </div>
      )}
      <MonacoEditor
        code={currentCode}
        language={language}
        filename={filename}
        onClose={onClose}
        onSave={handleSaveFromEditor}
        onFixWithAI={onFixWithAI}
      />
    </div>
  );
}
