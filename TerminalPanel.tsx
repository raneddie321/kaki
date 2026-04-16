import { useState, useRef, useEffect } from "react";

interface TerminalPanelProps {
  onClose?: () => void;
  onCreateFile?: (filename: string, content: string) => void;
  onEditFile?: (filename: string) => void;
  onRunApp?: () => void;
}

interface TerminalLine {
  type: "input" | "output" | "error";
  content: string;
}

export function TerminalPanel({ onClose, onCreateFile, onEditFile, onRunApp }: TerminalPanelProps) {
  const [lines, setLines] = useState<TerminalLine[]>([
    { type: "output", content: "RanEddie PowerShell v1.0" },
    { type: "output", content: "Type 'help' for available commands.\n" },
  ]);
  const [input, setInput] = useState("");
  const [cwd, setCwd] = useState("C:\\Users\\You\\Project");
  const [commandHistory, setCommandHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [lines]);

  const getDefaultContent = (filename: string): string => {
    const ext = filename.split(".").pop()?.toLowerCase() || "";
    switch (ext) {
      case "html":
        return `<!DOCTYPE html>\n<html lang="en">\n<head>\n  <meta charset="UTF-8">\n  <meta name="viewport" content="width=device-width, initial-scale=1.0">\n  <title>${filename}</title>\n</head>\n<body>\n  <h1>Hello World</h1>\n</body>\n</html>`;
      case "css":
        return `/* ${filename} */\n\nbody {\n  margin: 0;\n  padding: 20px;\n  font-family: system-ui, sans-serif;\n}\n`;
      case "js":
        return `// ${filename}\n\nconsole.log("Hello from ${filename}");\n`;
      case "ts":
        return `// ${filename}\n\nconst greeting: string = "Hello from ${filename}";\nconsole.log(greeting);\n`;
      case "json":
        return `{\n  "name": "${filename.replace(".json", "")}",\n  "version": "1.0.0"\n}`;
      case "py":
        return `# ${filename}\n\nprint("Hello from ${filename}")\n`;
      default:
        return "";
    }
  };

  const executeCommand = (cmd: string) => {
    const trimmed = cmd.trim();
    setLines((prev) => [...prev, { type: "input", content: `PS ${cwd}> ${trimmed}` }]);

    if (!trimmed) return;

    setCommandHistory((prev) => [trimmed, ...prev]);
    setHistoryIndex(-1);

    // Handle githubpages command
    if (trimmed.toLowerCase() === "githubpages" || trimmed.toLowerCase() === "public/githubpages") {
      addOutput("📦 Preparing project for GitHub Pages deployment...");
      setTimeout(() => {
        addOutput("🔧 Building production bundle...");
        setTimeout(() => {
          addOutput("✅ Build complete!");
          addOutput("🚀 Deploying to GitHub Pages...");
          setTimeout(() => {
            const projectName = "raneddie-project-" + Math.random().toString(36).slice(2, 8);
            const url = `https://raneddie.github.io/${projectName}/`;
            addOutput(`\n✅ Successfully deployed!`);
            addOutput(`🌐 Your site is live at: ${url}`);
            addOutput(`\nNote: It may take a few minutes for the site to be available.`);
          }, 1500);
        }, 1200);
      }, 1000);
      return;
    }

    // Handle /runapp shortcut
    if (trimmed.toLowerCase() === "/runapp") {
      if (onRunApp) {
        onRunApp();
        addOutput("🚀 Launching application in new tab...");
      } else {
        addError("No project loaded. Open a folder first.");
      }
      return;
    }

    const parts = trimmed.split(/\s+/);
    const command = parts[0].toLowerCase();
    const args = parts.slice(1);

    switch (command) {
      case "help":
        addOutput(
          "Available commands:\n" +
          "  help            - Show this help\n" +
          "  cls / clear     - Clear terminal\n" +
          "  echo <text>     - Print text\n" +
          "  dir / ls        - List files (simulated)\n" +
          "  cd <path>       - Change directory\n" +
          "  pwd             - Print working directory\n" +
          "  date            - Show current date/time\n" +
          "  whoami          - Show current user\n" +
          "  mkdir <name>    - Create directory (simulated)\n" +
          "  node -v         - Show Node version\n" +
          "  npm init        - Initialize project (simulated)\n" +
          "  git status      - Show git status (simulated)\n" +
          "  create <file>   - Create a new file and open in editor\n" +
          "  edit <file>     - Open an existing file in editor\n" +
          "  /runapp         - Launch project in new browser tab\n" +
          "  githubpages     - Deploy project to GitHub Pages"
        );
        break;
      case "cls":
      case "clear":
        setLines([]);
        break;
      case "echo":
        addOutput(args.join(" ") || "");
        break;
      case "dir":
      case "ls":
        addOutput(
          "    Directory: " + cwd + "\n\n" +
          "Mode        LastWriteTime     Length Name\n" +
          "----        -------------     ------ ----\n" +
          "d-----      04/12/2026         -     node_modules\n" +
          "d-----      04/12/2026         -     src\n" +
          "d-----      04/12/2026         -     public\n" +
          "-a----      04/12/2026      1024     package.json\n" +
          "-a----      04/12/2026       256     tsconfig.json\n" +
          "-a----      04/12/2026       512     vite.config.ts"
        );
        break;
      case "cd":
        if (args[0]) {
          if (args[0] === "..") {
            const pathParts = cwd.split("\\");
            if (pathParts.length > 1) setCwd(pathParts.slice(0, -1).join("\\"));
          } else {
            setCwd(cwd + "\\" + args[0]);
          }
        }
        break;
      case "pwd":
        addOutput(cwd);
        break;
      case "date":
        addOutput(new Date().toString());
        break;
      case "whoami":
        addOutput("You");
        break;
      case "mkdir":
        if (args[0]) addOutput(`Directory created: ${cwd}\\${args[0]}`);
        else addError("mkdir: missing operand");
        break;
      case "node":
        addOutput("v20.11.0");
        break;
      case "npm":
        if (args[0] === "init") {
          addOutput("Wrote to package.json:\n{\n  \"name\": \"my-project\",\n  \"version\": \"1.0.0\"\n}");
        } else {
          addOutput(`npm <command>\n\nUsage: npm init | npm install | npm start`);
        }
        break;
      case "git":
        if (args[0] === "status") {
          addOutput("On branch main\nnothing to commit, working tree clean");
        } else {
          addOutput("git: use 'git status' (simulated)");
        }
        break;
      case "create": {
        const filename = args.join(" ");
        if (!filename) {
          addError("Usage: create <filename>  (e.g. create index.html)");
          break;
        }
        const content = getDefaultContent(filename);
        if (onCreateFile) {
          onCreateFile(filename, content);
          addOutput(`✅ Created: ${filename}`);
        } else {
          addError("File system not available. Open a project folder first.");
        }
        break;
      }
      case "edit": {
        const editFilename = args.join(" ");
        if (!editFilename) {
          addError("Usage: edit <filename>  (e.g. edit index.html)");
          break;
        }
        if (onEditFile) {
          onEditFile(editFilename);
          addOutput(`📝 Opening: ${editFilename}`);
        } else {
          addError("File system not available. Open a project folder first.");
        }
        break;
      }
      default:
        addError(`'${command}' is not recognized as a command. Type 'help' for available commands.`);
    }
  };

  const addOutput = (content: string) => {
    setLines((prev) => [...prev, { type: "output", content }]);
  };

  const addError = (content: string) => {
    setLines((prev) => [...prev, { type: "error", content }]);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      executeCommand(input);
      setInput("");
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (commandHistory.length > 0) {
        const newIndex = Math.min(historyIndex + 1, commandHistory.length - 1);
        setHistoryIndex(newIndex);
        setInput(commandHistory[newIndex]);
      }
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      if (historyIndex > 0) {
        const newIndex = historyIndex - 1;
        setHistoryIndex(newIndex);
        setInput(commandHistory[newIndex]);
      } else {
        setHistoryIndex(-1);
        setInput("");
      }
    }
  };

  return (
    <div
      className="flex flex-col h-full bg-background font-mono text-sm cursor-text"
      onClick={() => inputRef.current?.focus()}
    >
      <div className="flex-1 overflow-y-auto p-3">
        {lines.map((line, i) => (
          <div
            key={i}
            className={`whitespace-pre-wrap leading-relaxed ${
              line.type === "input"
                ? "text-primary"
                : line.type === "error"
                ? "text-destructive"
                : "text-foreground"
            }`}
          >
            {line.content}
          </div>
        ))}
        <div className="flex items-center gap-1 text-primary">
          <span>PS {cwd}&gt;</span>
          <input
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            className="flex-1 bg-transparent outline-none border-none text-foreground"
            autoFocus
          />
        </div>
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
