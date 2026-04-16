import { X } from "lucide-react";

interface CodePreviewProps {
  code: string;
  language: string;
  onClose: () => void;
}

export function CodePreview({ code, language, onClose }: CodePreviewProps) {
  const getPreviewContent = () => {
    if (language === "html") return code;
    if (["javascript", "js", "jsx", "tsx"].includes(language)) {
      return `<!DOCTYPE html>
<html><head><style>body{background:#1a1a2e;color:#eee;font-family:monospace;padding:20px}</style></head>
<body><div id="root"></div>
<script>
try { ${code} } catch(e) { document.getElementById('root').innerText = 'Error: ' + e.message; }
</script></body></html>`;
    }
    if (language === "css") {
      return `<!DOCTYPE html>
<html><head><style>${code}</style></head>
<body><div class="preview"><h1>CSS Preview</h1><p>Your styles are applied.</p><button>Button</button><a href="#">Link</a></div></body></html>`;
    }
    return `<pre>${code}</pre>`;
  };

  return (
    <div className="border-t border-code-border">
      <div className="flex items-center justify-between px-4 py-2 bg-code-header">
        <span className="text-xs font-mono text-primary">Live Preview</span>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground transition-colors">
          <X className="w-4 h-4" />
        </button>
      </div>
      <div className="bg-background p-1">
        <iframe
          srcDoc={getPreviewContent()}
          className="w-full h-64 rounded border border-border bg-background"
          sandbox="allow-scripts"
          title="Code Preview"
        />
      </div>
    </div>
  );
}
