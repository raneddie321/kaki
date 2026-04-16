import { useCallback, useRef, useState, useEffect } from "react";
import Editor, { OnMount } from "@monaco-editor/react";
import { X, Save, Wand2, Loader2 } from "lucide-react";
import type * as monacoType from "monaco-editor";

interface MonacoEditorProps {
  code: string;
  language: string;
  filename?: string;
  onClose: () => void;
  onSave?: (content: string) => void;
  readOnly?: boolean;
  onCursorChange?: (line: number, col: number) => void;
  onFixWithAI?: (errorMessage: string, code: string, filename?: string) => void;
}

function getMonacoLanguage(lang: string): string {
  const map: Record<string, string> = {
    js: "javascript", jsx: "javascript", ts: "typescript", tsx: "typescript",
    py: "python", html: "html", css: "css", json: "json", md: "markdown",
    yml: "yaml", yaml: "yaml", sh: "shell", sql: "sql", xml: "xml", svg: "xml",
    scss: "scss", less: "less", vue: "html", svelte: "html",
    java: "java", c: "c", cpp: "cpp", cs: "csharp", go: "go", rs: "rust",
    rb: "ruby", php: "php", swift: "swift", kt: "kotlin",
    text: "plaintext", txt: "plaintext", markdown: "markdown",
    javascript: "javascript", typescript: "typescript", python: "python",
    bash: "shell",
  };
  return map[lang] || "plaintext";
}

// Common snippets for web languages
function registerCompletionProviders(monaco: typeof monacoType) {
  // HTML completions
  monaco.languages.registerCompletionItemProvider("html", {
    triggerCharacters: ["<", " ", "=", '"'],
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };
      const htmlTags = ["div", "span", "p", "h1", "h2", "h3", "h4", "h5", "h6", "a", "img", "ul", "ol", "li", "table", "tr", "td", "th", "form", "input", "button", "textarea", "select", "option", "label", "section", "header", "footer", "nav", "main", "article", "aside", "video", "audio", "canvas", "script", "style", "link", "meta"];
      const suggestions = htmlTags.map(tag => ({
        label: tag,
        kind: monaco.languages.CompletionItemKind.Keyword,
        insertText: `${tag}>$0</${tag}>`,
        insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
        range,
        detail: `<${tag}>`,
      }));
      return { suggestions };
    },
  });

  // CSS completions
  monaco.languages.registerCompletionItemProvider("css", {
    triggerCharacters: [":"],
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position);
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };
      const props = ["display", "flex-direction", "justify-content", "align-items", "margin", "padding", "border", "background", "color", "font-size", "font-weight", "width", "height", "position", "top", "left", "right", "bottom", "z-index", "overflow", "opacity", "transition", "transform", "box-shadow", "border-radius", "gap", "grid-template-columns", "grid-template-rows"];
      return {
        suggestions: props.map(p => ({
          label: p,
          kind: monaco.languages.CompletionItemKind.Property,
          insertText: `${p}: $0;`,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          range,
        })),
      };
    },
  });

  // JS/TS snippet completions  
  const jsSnippets = [
    { label: "log", insertText: "console.log($0);", detail: "console.log()" },
    { label: "func", insertText: "function ${1:name}(${2:params}) {\n\t$0\n}", detail: "Function declaration" },
    { label: "arrow", insertText: "const ${1:name} = (${2:params}) => {\n\t$0\n};", detail: "Arrow function" },
    { label: "if", insertText: "if (${1:condition}) {\n\t$0\n}", detail: "If statement" },
    { label: "for", insertText: "for (let ${1:i} = 0; ${1:i} < ${2:length}; ${1:i}++) {\n\t$0\n}", detail: "For loop" },
    { label: "foreach", insertText: "${1:array}.forEach((${2:item}) => {\n\t$0\n});", detail: "forEach loop" },
    { label: "map", insertText: "${1:array}.map((${2:item}) => $0)", detail: "Array.map()" },
    { label: "filter", insertText: "${1:array}.filter((${2:item}) => $0)", detail: "Array.filter()" },
    { label: "try", insertText: "try {\n\t$0\n} catch (${1:error}) {\n\tconsole.error(${1:error});\n}", detail: "Try/catch" },
    { label: "async", insertText: "async function ${1:name}(${2:params}) {\n\t$0\n}", detail: "Async function" },
    { label: "await", insertText: "const ${1:result} = await $0;", detail: "Await expression" },
    { label: "import", insertText: "import { $0 } from '${1:module}';", detail: "Import statement" },
    { label: "export", insertText: "export $0", detail: "Export statement" },
    { label: "class", insertText: "class ${1:Name} {\n\tconstructor(${2:params}) {\n\t\t$0\n\t}\n}", detail: "Class declaration" },
    { label: "useState", insertText: "const [${1:state}, set${2:State}] = useState($0);", detail: "React useState" },
    { label: "useEffect", insertText: "useEffect(() => {\n\t$0\n}, [${1:deps}]);", detail: "React useEffect" },
    { label: "useCallback", insertText: "const ${1:fn} = useCallback((${2:params}) => {\n\t$0\n}, [${3:deps}]);", detail: "React useCallback" },
    { label: "useMemo", insertText: "const ${1:value} = useMemo(() => $0, [${2:deps}]);", detail: "React useMemo" },
  ];

  for (const lang of ["javascript", "typescript"]) {
    monaco.languages.registerCompletionItemProvider(lang, {
      provideCompletionItems: (model, position) => {
        const word = model.getWordUntilPosition(position);
        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn,
        };
        return {
          suggestions: jsSnippets.map(s => ({
            label: s.label,
            kind: monaco.languages.CompletionItemKind.Snippet,
            insertText: s.insertText,
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            range,
            detail: s.detail,
          })),
        };
      },
    });
  }
}

let providersRegistered = false;

export function MonacoEditor({ code, language, filename, onClose, onSave, readOnly, onCursorChange, onFixWithAI }: MonacoEditorProps) {
  const editorRef = useRef<any>(null);
  const monacoRef = useRef<typeof monacoType | null>(null);
  const [errors, setErrors] = useState<{ message: string; line: number }[]>([]);
  const [fixingError, setFixingError] = useState(false);

  const handleMount: OnMount = (editor, monaco) => {
    editorRef.current = editor;
    monacoRef.current = monaco;

    if (!providersRegistered) {
      registerCompletionProviders(monaco);
      providersRegistered = true;
    }

    // Ctrl+S to save
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
      if (onSave) onSave(editor.getValue());
    });

    // Track cursor position
    editor.onDidChangeCursorPosition((e: any) => {
      onCursorChange?.(e.position.lineNumber, e.position.column);
    });

    // Listen for marker (error) changes
    monaco.editor.onDidChangeMarkers((_uris) => {
      const model = editor.getModel();
      if (!model) return;
      const markers = monaco.editor.getModelMarkers({ resource: model.uri });
      const errs = markers
        .filter(m => m.severity === monaco.MarkerSeverity.Error)
        .map(m => ({ message: m.message, line: m.startLineNumber }));
      setErrors(errs);
    });
  };

  const handleSave = useCallback(() => {
    if (editorRef.current && onSave) {
      onSave(editorRef.current.getValue());
    }
  }, [onSave]);

  const handleFixWithAI = useCallback(() => {
    if (!onFixWithAI || errors.length === 0 || !editorRef.current) return;
    setFixingError(true);
    const errorSummary = errors.map(e => `Line ${e.line}: ${e.message}`).join("\n");
    onFixWithAI(errorSummary, editorRef.current.getValue(), filename);
    setTimeout(() => setFixingError(false), 1000);
  }, [onFixWithAI, errors, filename]);

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-card">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-foreground truncate">
            {filename || "Untitled"}
          </span>
          <span className="text-[10px] px-2 py-0.5 rounded-full bg-secondary text-muted-foreground">
            {language}
          </span>
        </div>
        <div className="flex items-center gap-1">
          {errors.length > 0 && onFixWithAI && (
            <button
              onClick={handleFixWithAI}
              disabled={fixingError}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-destructive/10 text-destructive hover:bg-destructive/20 transition-colors"
            >
              {fixingError ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Wand2 className="w-3.5 h-3.5" />}
              Fix {errors.length} error{errors.length > 1 ? "s" : ""} with AI
            </button>
          )}
          {onSave && (
            <button
              onClick={handleSave}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
            >
              <Save className="w-3.5 h-3.5" /> Save
            </button>
          )}
          <button onClick={onClose} className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>
      <div className="flex-1 min-h-0">
        <Editor
          height="100%"
          language={getMonacoLanguage(language)}
          value={code}
          theme="vs-dark"
          onMount={handleMount}
          options={{
            readOnly,
            minimap: { enabled: true },
            fontSize: 13,
            lineNumbers: "on",
            wordWrap: "on",
            scrollBeyondLastLine: false,
            automaticLayout: true,
            padding: { top: 12 },
            renderLineHighlight: "line",
            cursorBlinking: "smooth",
            smoothScrolling: true,
            bracketPairColorization: { enabled: true },
            suggestOnTriggerCharacters: true,
            quickSuggestions: { other: true, comments: false, strings: true },
            acceptSuggestionOnCommitCharacter: true,
            tabCompletion: "on",
            parameterHints: { enabled: true },
            suggest: {
              showKeywords: true,
              showSnippets: true,
              showClasses: true,
              showFunctions: true,
              showVariables: true,
              showModules: true,
              showProperties: true,
              showConstants: true,
              showMethods: true,
              preview: true,
              shareSuggestSelections: true,
            },
            snippetSuggestions: "top",
            formatOnPaste: true,
            formatOnType: true,
            linkedEditing: true,
            autoClosingBrackets: "always",
            autoClosingQuotes: "always",
            autoSurround: "languageDefined",
            inlineSuggest: { enabled: true },
          }}
        />
      </div>
    </div>
  );
}
