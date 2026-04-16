import { X } from "lucide-react";

interface PreviewPanelProps {
  code: string;
  language: string;
  filename?: string;
  onClose: () => void;
}

export function PreviewPanel({ code, language, filename, onClose }: PreviewPanelProps) {
  const getPreviewContent = () => {
    if (language === "html") return code;
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
<body><div class="preview"><h1>CSS Preview</h1><p>Your styles are applied to this page.</p><button>Button</button><a href="#">Link</a><div class="box">Box</div></div></body></html>`;
    }
    return `<!DOCTYPE html><html><head><style>body{background:#1a1a2e;color:#eee;font-family:monospace;padding:20px;white-space:pre-wrap}</style></head><body>${code}</body></html>`;
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-card">
        <span className="text-sm font-medium text-foreground">
          Preview {filename ? `— ${filename}` : ""}
        </span>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors">
          <X className="w-4 h-4" />
        </button>
      </div>
      <div className="flex-1 bg-background">
        <iframe
          srcDoc={getPreviewContent()}
          className="w-full h-full border-none"
          sandbox="allow-scripts"
          title="Preview"
        />
      </div>
    </div>
  );
}
