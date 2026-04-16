import { useState, useCallback, useEffect, useMemo } from "react";
import { PanelLeft, ArrowLeft, Search, Terminal as TermIcon, FilePlus, FolderPlus, Keyboard } from "lucide-react";
import { TeamsPanel } from "@/components/TeamsPanel";
import { useAuth } from "@/hooks/useAuth";
import { supabase } from "@/integrations/supabase/client";
import { ResizablePanelGroup, ResizablePanel, ResizableHandle } from "@/components/ui/resizable";
import { TopMenuBar } from "@/components/TopMenuBar";
import { ChatView, type ChatModeType } from "@/components/ChatView";
import { ModelManager } from "@/components/ModelManager";
import { SettingsPanel } from "@/components/SettingsPanel";
import { FileExplorer, type FileNode, getAllFiles } from "@/components/FileExplorer";
import { SplitTerminal } from "@/components/SplitTerminal";
import { ConnectorManagementPage } from "@/components/ConnectorManagementPage";
import { EditorWithPreview } from "@/components/EditorWithPreview";
import { ExtensionsPanel } from "@/components/ExtensionsPanel";
import { ExtensionSidebarPage } from "@/components/ExtensionSidebarPage";
import { ActivityBar, type ActivityTab } from "@/components/ActivityBar";
import { StatusBar } from "@/components/StatusBar";
import { CommandPalette, type Command } from "@/components/CommandPalette";
import { EditorTabs, type EditorTab } from "@/components/EditorTabs";
import { Breadcrumbs } from "@/components/Breadcrumbs";
import { SearchPanel } from "@/components/SearchPanel";
import { GitPanel } from "@/components/GitPanel";
import { DebugPanel } from "@/components/DebugPanel";
import { createChat, type Chat, type ChatMode } from "@/lib/chatStore";
import { type Project, saveProjects, loadProjects } from "@/lib/projectStore";
import { extractFileOperations, normalizeFilePath } from "@/lib/fileOperations";
import { streamChat } from "@/lib/streamChat";
import { toast } from "sonner";

interface WorkspaceProps {
  project: Project;
  onBack: () => void;
}

// Context menu component
function ContextMenu({ x, y, items, onClose }: { x: number; y: number; items: { label: string; action: () => void; divider?: boolean }[]; onClose: () => void }) {
  useEffect(() => {
    const handler = () => onClose();
    document.addEventListener("click", handler);
    document.addEventListener("contextmenu", handler);
    return () => { document.removeEventListener("click", handler); document.removeEventListener("contextmenu", handler); };
  }, [onClose]);

  return (
    <div className="fixed z-[9999] bg-popover border border-border rounded-lg shadow-2xl py-1 min-w-[180px]" style={{ top: y, left: x }}>
      {items.map((item, i) => (
        item.divider ? <div key={i} className="h-px bg-border my-1" /> :
        <button key={i} onClick={() => { item.action(); onClose(); }}
          className="w-full text-left px-3 py-1.5 text-xs text-foreground hover:bg-secondary transition-colors">
          {item.label}
        </button>
      ))}
    </div>
  );
}

export default function Workspace({ project, onBack }: WorkspaceProps) {
  const { user, credits, refreshCredits } = useAuth();
  const [chats, setChats] = useState<Chat[]>(project.chats);
  const [activeChatId, setActiveChatId] = useState<string | null>(() => {
    try {
      return localStorage.getItem(`raneddie_last_chat_${project.id}`) || null;
    } catch { return null; }
  });
  const [activityTab, setActivityTab] = useState<ActivityTab>("explorer");
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [isStreaming, setIsStreaming] = useState(false);
  const [terminalOpen, setTerminalOpen] = useState(false);
  const [files, setFiles] = useState<FileNode[]>(project.files);
  const [selectedFile, setSelectedFile] = useState<FileNode | null>(null);
  const [chatMode, setChatMode] = useState<ChatModeType>("raneddie");
  const [showModelManager, setShowModelManager] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [managingConnector, setManagingConnector] = useState<string | null>(null);
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const [openTabs, setOpenTabs] = useState<EditorTab[]>([]);
  const [activeTabId, setActiveTabId] = useState<string | null>(null);
  const [cursorPos, setCursorPos] = useState({ line: 1, col: 1 });
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; items: { label: string; action: () => void; divider?: boolean }[] } | null>(null);

  const activeChat = chats.find((c) => c.id === activeChatId) || null;
  const allProjectFiles = useMemo(() => getAllFiles(files), [files]);
  const activeEditorTab = openTabs.find(t => t.id === activeTabId);

  const [installedExts, setInstalledExts] = useState<{
    id: string; name: string; icon: string;
    description?: string; version?: string; author?: string;
    repository?: string; url?: string; category?: string; tags?: string[];
  }[]>([]);

  const loadInstalledExts = useCallback(() => {
    try {
      const metaRaw = localStorage.getItem("raneddie_installed_extensions_meta");
      const meta: Record<string, any> = metaRaw ? JSON.parse(metaRaw) : {};
      setInstalledExts(Object.entries(meta).map(([id, v]) => ({
        id,
        name: v?.name || id,
        icon: v?.icon || "",
        description: v?.description,
        version: v?.version,
        author: v?.author,
        repository: v?.repository,
        url: v?.url,
        category: v?.category,
        tags: Array.isArray(v?.tags) ? v.tags : [],
      })));
    } catch { setInstalledExts([]); }
  }, []);

  useEffect(() => {
    loadInstalledExts();
    const handler = () => loadInstalledExts();
    window.addEventListener("extensions-changed", handler);
    return () => window.removeEventListener("extensions-changed", handler);
  }, [loadInstalledExts]);

  const installedExtCount = installedExts.length;

  useEffect(() => {
    try {
      const projects = loadProjects();
      const idx = projects.findIndex(p => p.id === project.id);
      if (idx !== -1) {
        projects[idx] = { ...projects[idx], chats, files, updatedAt: Date.now() };
        saveProjects(projects);
      }
    } catch (e) {
      console.warn("Failed to persist project data:", e);
    }
  }, [chats, files, project.id]);

  // Persist last active chat ID per project
  useEffect(() => {
    try {
      if (activeChatId) localStorage.setItem(`raneddie_last_chat_${project.id}`, activeChatId);
      else localStorage.removeItem(`raneddie_last_chat_${project.id}`);
    } catch {}
  }, [activeChatId, project.id]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === "p") { e.preventDefault(); setCommandPaletteOpen(prev => !prev); }
      if ((e.ctrlKey || e.metaKey) && e.key === "`") { e.preventDefault(); setTerminalOpen(prev => !prev); }
      if ((e.ctrlKey || e.metaKey) && e.key === "b") { e.preventDefault(); setSidebarOpen(prev => !prev); }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  const commands = useMemo<Command[]>(() => [
    { id: "toggle-terminal", label: "Toggle Terminal", category: "View", icon: TermIcon, shortcut: "Ctrl+`", action: () => setTerminalOpen(p => !p) },
    { id: "toggle-sidebar", label: "Toggle Sidebar", category: "View", icon: PanelLeft, shortcut: "Ctrl+B", action: () => setSidebarOpen(p => !p) },
    { id: "new-file", label: "New File", category: "File", icon: FilePlus, action: () => { setActivityTab("explorer"); setSidebarOpen(true); } },
    { id: "new-folder", label: "New Folder", category: "File", icon: FolderPlus, action: () => { setActivityTab("explorer"); setSidebarOpen(true); } },
    { id: "open-search", label: "Search in Files", category: "Edit", icon: Search, shortcut: "Ctrl+Shift+F", action: () => { setActivityTab("search"); setSidebarOpen(true); } },
    { id: "keyboard-shortcuts", label: "Keyboard Shortcuts", category: "Help", icon: Keyboard, action: () => toast.info("Ctrl+Shift+P: Command Palette\nCtrl+`: Terminal\nCtrl+B: Sidebar\nCtrl+S: Save") },
    { id: "open-settings", label: "Settings", category: "Preferences", icon: Keyboard, action: () => setShowSettings(true) },
  ], []);

  const applyFileOperations = useCallback((content: string) => {
    const ops = extractFileOperations(content, allProjectFiles.map(f => f.path));
    if (ops.length === 0) return;

    setFiles((prev) => {
      let tree = prev;
      for (const op of ops) {
        if (op.type === "delete") tree = deleteNodeFromTree(tree, op.path);
        else tree = addNodeToTree(tree, op.path, op.content || "");
      }
      return tree;
    });

    toast.success(`${ops.length} file ${ops.length === 1 ? "change" : "changes"} applied`);
  }, [allProjectFiles]);

  const handleNewChat = useCallback((mode: ChatMode) => {
    const chat = createChat(mode);
    setChats((prev) => [chat, ...prev]);
    setActiveChatId(chat.id);
    setChatMode(mode);
    if (mode === "primecode") setActivityTab("explorer");
  }, []);

  const handleDeleteChat = useCallback((id: string) => {
    setChats((prev) => prev.filter((c) => c.id !== id));
    if (activeChatId === id) setActiveChatId(null);
  }, [activeChatId]);

  const handleRenameChat = useCallback((id: string, title: string) => {
    setChats((prev) => prev.map((c) => (c.id === id ? { ...c, title } : c)));
  }, []);

  const openFileInTab = useCallback((file: FileNode) => {
    setSelectedFile(file);
    const existing = openTabs.find(t => t.path === file.path);
    if (existing) {
      setActiveTabId(existing.id);
    } else {
      const tab: EditorTab = {
        id: crypto.randomUUID(),
        filename: file.name,
        path: file.path,
        language: guessLanguage(file.name),
        content: file.content || "",
      };
      setOpenTabs(prev => [...prev, tab]);
      setActiveTabId(tab.id);
    }
  }, [openTabs]);

  const handleFileSelect = useCallback((file: FileNode) => {
    if (file.content !== undefined) openFileInTab(file);
  }, [openFileInTab]);

  const handleCloseTab = useCallback((id: string) => {
    setOpenTabs(prev => {
      const next = prev.filter(t => t.id !== id);
      if (activeTabId === id) setActiveTabId(next.length > 0 ? next[next.length - 1].id : null);
      return next;
    });
  }, [activeTabId]);

  const handleFileSave = useCallback((content: string) => {
    if (!activeEditorTab) return;
    setFiles(prev => modifyNodeInTree(prev, activeEditorTab.path, content));
    setOpenTabs(prev => prev.map(t => t.id === activeEditorTab.id ? { ...t, content, isDirty: false } : t));
    toast.success(`Saved: ${activeEditorTab.filename}`);
  }, [activeEditorTab]);

  const handleCreateFile = useCallback((path: string, content: string) => {
    setFiles(prev => addNodeToTree(prev, path, content));
    toast.success(`Created: ${path}`);
  }, []);

  const handleCreateFolder = useCallback((path: string) => {
    setFiles(prev => addFolderToTree(prev, path));
    toast.success(`Created folder: ${path}`);
  }, []);

  const handleDeleteFile = useCallback((path: string) => {
    setFiles(prev => deleteNodeFromTree(prev, path));
    setOpenTabs(prev => prev.filter(t => t.path !== path));
    toast.success(`Deleted: ${path}`);
  }, []);

  const handleTerminalCreate = useCallback((filename: string, content: string) => {
    setFiles(prev => addNodeToTree(prev, filename, content));
    const file: FileNode = { name: filename.split("/").pop()!, path: filename, type: "file", content };
    openFileInTab(file);
    toast.success(`Created: ${filename}`);
  }, [openFileInTab]);

  const handleTerminalEdit = useCallback((filename: string) => {
    const file = allProjectFiles.find((f) => f.name === filename || f.path === filename || f.path.endsWith("/" + filename));
    if (file && file.content !== undefined) openFileInTab(file);
    else toast.error(`File not found: ${filename}`);
  }, [allProjectFiles, openFileInTab]);

  const handleRunApp = useCallback(() => {
    const indexHtml = allProjectFiles.find((f) => f.name === "index.html");
    if (!indexHtml?.content) { toast.error("No index.html found."); return; }

    // Build a map of filename -> content for quick lookup
    const fileMap = new Map<string, string>();
    for (const f of allProjectFiles) {
      if (f.content) {
        fileMap.set(f.name, f.content);
        fileMap.set(f.path, f.content);
      }
    }

    let html = indexHtml.content;

    // Inline all <script src="..."> references
    html = html.replace(/<script\s+[^>]*src=["']([^"']+)["'][^>]*><\/script>/gi, (_match, src) => {
      const content = fileMap.get(src) || fileMap.get(src.replace('./', ''));
      if (content) return `<script>${content}</script>`;
      return `<script>console.warn("File not found: ${src}")</script>`;
    });

    // Inline all <link rel="stylesheet" href="..."> references
    html = html.replace(/<link\s+[^>]*href=["']([^"']+)["'][^>]*\/?>/gi, (match, href) => {
      if (!match.includes('stylesheet') && !href.endsWith('.css')) return match;
      const content = fileMap.get(href) || fileMap.get(href.replace('./', ''));
      if (content) return `<style>${content}</style>`;
      return `<!-- CSS not found: ${href} -->`;
    });

    // Also inject any CSS/JS files that weren't referenced in HTML
    const cssFiles = allProjectFiles.filter((f) => f.name.endsWith(".css") && f.content && !html.includes(f.content));
    const jsFiles = allProjectFiles.filter((f) => (f.name.endsWith(".js") || f.name.endsWith(".ts")) && f.content && f.name !== "index.html" && !html.includes(f.content));
    if (cssFiles.length > 0) html = html.replace("</head>", cssFiles.map(f => `<style>${f.content}</style>`).join("\n") + "\n</head>");
    if (jsFiles.length > 0) html = html.replace("</body>", jsFiles.map(f => `<script>${f.content}</script>`).join("\n") + "\n</body>");

    const blob = new Blob([html], { type: "text/html" });
    window.open(URL.createObjectURL(blob), "_blank");
    toast.success("App launched!");
  }, [allProjectFiles]);

  const handleSend = useCallback(async (message: string, options?: { model?: string; mode?: string; thinking?: boolean }) => {
    // Deduct a credit
    if (user) {
      const { data: allowed } = await supabase.rpc("use_credit", { p_user_id: user.id });
      if (allowed === false) {
        await refreshCredits();
        return;
      }
      refreshCredits();
    }

    let chat = activeChat;
    if (!chat) {
      chat = createChat(chatMode);
      setChats((prev) => [chat!, ...prev]);
      setActiveChatId(chat.id);
    }
    let fullMessage = message;
    {
      // Build full project file tree for AI context
      const fileTree = allProjectFiles.map(f => f.path).sort().join("\n");
      
      // Gather referenced files via @ mentions
      const refRegex = /@([\w/.\\-]+)/g;
      let refMatch;
      const referencedPaths: string[] = [];
      while ((refMatch = refRegex.exec(message)) !== null) referencedPaths.push(refMatch[1]);
      
      const fileContextParts: string[] = [];
      for (const refPath of referencedPaths) {
        const file = allProjectFiles.find((f) => f.path === refPath || f.path.endsWith(refPath));
        if (file?.content) fileContextParts.push(`[File: ${file.path}]\n\`\`\`\n${file.content}\n\`\`\``);
      }
      
      // If no specific files referenced, include the currently open file
      if (fileContextParts.length === 0 && selectedFile?.content) {
        fileContextParts.push(`[File: ${selectedFile.path}]\n\`\`\`\n${selectedFile.content}\n\`\`\``);
      }
      
      // Also include ALL project files content so AI fully understands the codebase
      const allFileContents: string[] = [];
      for (const f of allProjectFiles) {
        if (f.content && !fileContextParts.some(p => p.includes(`[File: ${f.path}]`))) {
          allFileContents.push(`[File: ${f.path}]\n\`\`\`\n${f.content}\n\`\`\``);
        }
      }
      
      // Build the full context message
      const projectContext = `[PROJECT FILE TREE]\n${fileTree}\n\n[REFERENCED/ACTIVE FILES]\n${fileContextParts.join("\n\n")}\n\n[ALL PROJECT FILES]\n${allFileContents.join("\n\n")}`;
      
      fullMessage = projectContext + "\n\n[USER REQUEST]\n" + message;
      fullMessage += "\n\n[SYSTEM INSTRUCTIONS - MANDATORY: When creating or modifying files, you MUST use these exact markers. This is critical - without them the files will NOT be saved:\n===CREATE_FILE: path/to/file.ext===\nfile content here\n===END_FILE===\n\nFor modifications:\n===MODIFY_FILE: path/to/file.ext===\nnew content here\n===END_FILE===\n\nFor deletions:\n===DELETE_FILE: path/to/file.ext===\n\nALWAYS wrap every file you create or change with these markers. Never show code without them.\n\nIMPORTANT: You have access to the COMPLETE project source code above. Study it carefully before making changes. Understand the existing patterns, imports, component structure, styling approach, and conventions. Make changes that are consistent with the existing codebase. When modifying a file, include the COMPLETE updated file content - never partial snippets.]";
    }
    // Store the clean user message for display, send fullMessage to AI
    const displayMsg = { role: "user" as const, content: message };
    const aiMsg = { role: "user" as const, content: fullMessage };
    const updatedDisplayMessages = [...chat.messages, displayMsg];
    const updatedAiMessages = [...chat.messages.map(m => m.role === "user" ? { ...m, content: m.content } : m), aiMsg];
    const isFirst = chat.messages.length === 0;
    const newTitle = isFirst ? message.slice(0, 40) + (message.length > 40 ? "..." : "") : chat.title;
    const chatId = chat.id;
    setChats((prev) => prev.map((c) => c.id === chatId ? { ...c, messages: updatedDisplayMessages, title: newTitle } : c));
    setIsStreaming(true);
    let assistantContent = "";
    const mode = chatMode;
    const innerMode = options?.mode; // "build" or "plan" from ChatInput
    await streamChat({
      messages: updatedAiMessages, mode, model: options?.model, thinking: options?.thinking,
      innerMode,
      onDelta: (text) => {
        assistantContent += text;
        setChats((prev) => prev.map((c) => {
          if (c.id !== chatId) return c;
          const msgs = [...updatedDisplayMessages];
          const lastMsg = msgs[msgs.length - 1];
          if (lastMsg?.role === "assistant") msgs[msgs.length - 1] = { ...lastMsg, content: assistantContent };
          else msgs.push({ role: "assistant", content: assistantContent });
          return { ...c, messages: msgs };
        }));
      },
      onDone: () => {
        setIsStreaming(false);
        // Only apply file operations in build mode, not plan mode
        if (innerMode !== "plan") applyFileOperations(assistantContent);
      },
      onError: (err) => { setIsStreaming(false); toast.error(err); },
    });
  }, [activeChat, chatMode, selectedFile, allProjectFiles, applyFileOperations, user, refreshCredits]);

  // Right-click handlers
  const handleExplorerContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setContextMenu({
      x: e.clientX, y: e.clientY,
      items: [
        { label: "📄 New File...", action: () => { setActivityTab("explorer"); setSidebarOpen(true); } },
        { label: "📁 New Folder...", action: () => { setActivityTab("explorer"); setSidebarOpen(true); } },
        { label: "", action: () => {}, divider: true },
        { label: "🔍 Find in Files", action: () => { setActivityTab("search"); setSidebarOpen(true); } },
        { label: "", action: () => {}, divider: true },
        { label: "📋 Paste", action: () => toast.info("Paste not available") },
      ],
    });
  }, []);

  const handleEditorContextMenu = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    setContextMenu({
      x: e.clientX, y: e.clientY,
      items: [
        { label: "✂️ Cut", action: () => document.execCommand("cut") },
        { label: "📋 Copy", action: () => document.execCommand("copy") },
        { label: "📋 Paste", action: () => document.execCommand("paste") },
        { label: "", action: () => {}, divider: true },
        { label: "🔍 Find", action: () => toast.info("Use Ctrl+F to find") },
        { label: "🔄 Find and Replace", action: () => toast.info("Use Ctrl+H to replace") },
        { label: "", action: () => {}, divider: true },
        { label: "📝 Format Document", action: () => toast.info("Document formatted") },
        { label: "💾 Save", action: () => { if (activeEditorTab) handleFileSave(activeEditorTab.content); } },
      ],
    });
  }, [activeEditorTab, handleFileSave]);

  const handleTabContextMenu = useCallback((e: React.MouseEvent, tabId: string) => {
    e.preventDefault();
    setContextMenu({
      x: e.clientX, y: e.clientY,
      items: [
        { label: "Close", action: () => handleCloseTab(tabId) },
        { label: "Close Others", action: () => setOpenTabs(prev => prev.filter(t => t.id === tabId)) },
        { label: "Close All", action: () => { setOpenTabs([]); setActiveTabId(null); } },
        { label: "", action: () => {}, divider: true },
        { label: "Copy Path", action: () => { const tab = openTabs.find(t => t.id === tabId); if (tab) { navigator.clipboard.writeText(tab.path); toast.success("Path copied"); } } },
      ],
    });
  }, [handleCloseTab, openTabs]);

  // Sidebar content
  const sidebarContent = () => {
    if (managingConnector) return <ConnectorManagementPage connectorId={managingConnector} onClose={() => setManagingConnector(null)} />;
    if (showModelManager) return <ModelManager onClose={() => setShowModelManager(false)} />;
    if (showSettings) return <SettingsPanel onClose={() => setShowSettings(false)} />;

    switch (activityTab) {
      case "explorer":
        return (
          <div className="flex flex-col h-full" onContextMenu={handleExplorerContextMenu}>
            <div className="px-4 py-2 text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Explorer</div>
            <FileExplorer files={files} onFilesLoaded={setFiles} onFileSelect={handleFileSelect}
              onCreateFile={handleCreateFile} onCreateFolder={handleCreateFolder} onDeleteFile={handleDeleteFile}
              selectedPath={selectedFile?.path} />
          </div>
        );
      case "search":
        return <SearchPanel files={files} onFileSelect={handleFileSelect} />;
      case "git":
        return <GitPanel files={files} />;
      case "debug":
        return <DebugPanel />;
      case "extensions":
        return <ExtensionsPanel />;
      case "chat":
        return (
          <div className="flex flex-col h-full">
            <div className="px-4 py-2 text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Chats</div>
            <div className="px-2 pb-1">
              <button onClick={() => handleNewChat("primecode")}
                className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg bg-accent/30 border border-primary/30 text-primary hover:bg-accent/50 transition-colors text-xs font-medium mb-1">
                ⚡ PrimeCODE
              </button>
              <button onClick={() => handleNewChat("raneddie")}
                className="w-full flex items-center gap-2 px-3 py-1.5 rounded-lg text-muted-foreground hover:bg-sidebar-hover hover:text-foreground transition-colors text-xs">
                + New Chat
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-2">
              {chats.map(chat => (
                <div key={chat.id} onClick={() => { setActiveChatId(chat.id); setChatMode(chat.mode); }}
                  className={`flex items-center gap-2 px-2 py-1.5 rounded-lg cursor-pointer text-xs mb-0.5 transition-colors ${
                    chat.id === activeChatId ? "bg-sidebar-accent text-foreground" : "text-sidebar-foreground hover:bg-sidebar-hover"
                  }`}>
                  <span className="truncate">{chat.title}</span>
                </div>
              ))}
            </div>
          </div>
        );
      case "teams":
        return (
          <div className="flex flex-col h-full">
            <div className="px-4 py-2 text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Teams+</div>
            <TeamsPanel projectId={project.id} />
          </div>
        );
      default: {
        if (activityTab.startsWith("ext-")) {
          const extId = activityTab.replace("ext-", "");
          const extInfo = installedExts.find(e => e.id === extId);
          return <ExtensionSidebarPage extId={extId} extInfo={extInfo || null} />;
        }
        return null;
      }
    }
  };

  return (
    <div className="flex flex-col h-screen w-full overflow-hidden bg-background">
      <TopMenuBar projectName={project.name} onBack={onBack} onManageConnector={(id) => { setManagingConnector(id); setSidebarOpen(true); }} />
      <CommandPalette open={commandPaletteOpen} onClose={() => setCommandPaletteOpen(false)} commands={commands} />

      {/* Context menu */}
      {contextMenu && <ContextMenu x={contextMenu.x} y={contextMenu.y} items={contextMenu.items} onClose={() => setContextMenu(null)} />}

      <div className="flex flex-1 min-h-0">
        {/* Activity Bar */}
        <ActivityBar activeTab={activityTab} onTabChange={(tab) => {
          if (tab === "settings") { setShowSettings(true); setShowModelManager(false); setSidebarOpen(true); return; }
          setShowSettings(false); setShowModelManager(false);
          if (activityTab === tab && sidebarOpen) setSidebarOpen(false);
          else { setActivityTab(tab); setSidebarOpen(true); }
        }} installedExtensionCount={installedExtCount} installedExtensions={installedExts} />

        {/* Resizable Sidebar + Main */}
        <ResizablePanelGroup direction="horizontal" className="flex-1 min-h-0">
          {sidebarOpen && (
            <>
              <ResizablePanel defaultSize={20} minSize={12} maxSize={40} id="sidebar-panel" order={1}>
                <div className="h-full bg-sidebar flex flex-col border-r border-sidebar-border">
                  <div className="flex-1 overflow-hidden">{sidebarContent()}</div>
                </div>
              </ResizablePanel>
              <ResizableHandle />
            </>
          )}

          <ResizablePanel defaultSize={sidebarOpen ? 80 : 100} minSize={30} id="main-panel" order={2}>
            <div className="flex-1 flex flex-col min-w-0 h-full">
              {/* Editor Tabs with context menu */}
              <div onContextMenu={(e) => {
                if (activeEditorTab) {
                  const tabEl = (e.target as HTMLElement).closest('[data-tab-id]');
                  if (tabEl) handleTabContextMenu(e, tabEl.getAttribute('data-tab-id')!);
                }
              }}>
                <EditorTabs tabs={openTabs} activeTabId={activeTabId} onSelectTab={setActiveTabId} onCloseTab={handleCloseTab} />
              </div>

              {activeEditorTab && <Breadcrumbs path={activeEditorTab.path} />}

              <ResizablePanelGroup direction="vertical" className="flex-1 min-h-0">
                <ResizablePanel defaultSize={terminalOpen ? 70 : 100} minSize={30}>
                  <ResizablePanelGroup direction="horizontal" className="h-full">
                    {activeEditorTab && (
                      <>
                        <ResizablePanel defaultSize={50} minSize={25} id="editor-split" order={1}>
                          <div className="h-full" onContextMenu={handleEditorContextMenu}>
                            <EditorWithPreview
                              code={activeEditorTab.content}
                              language={activeEditorTab.language}
                              filename={activeEditorTab.filename}
                              onClose={() => handleCloseTab(activeEditorTab.id)}
                              onSave={handleFileSave}
                              onFixWithAI={(errorMsg, code, fname) => {
                                handleSend(`Fix these errors in ${fname || "the file"}:\n${errorMsg}`, { mode: "build" });
                              }}
                            />
                          </div>
                        </ResizablePanel>
                        <ResizableHandle />
                      </>
                    )}
                    <ResizablePanel defaultSize={activeEditorTab ? 50 : 100} minSize={20} id="chat-split" order={2}>
                      <ChatView
                        chat={activeChat} isStreaming={isStreaming} onSend={handleSend}
                        onOpenPreview={(code, lang, filename) => {
                          if (filename) {
                            const file: FileNode = { name: filename, path: filename, type: "file", content: code };
                            openFileInTab(file);
                          }
                        }}
                        projectFiles={allProjectFiles}
                        onFileReference={(file) => setSelectedFile(file)}
                        chatMode={chatMode}
                        onChatModeChange={setChatMode}
                        onOpenModelManager={() => { setShowModelManager(true); setSidebarOpen(true); }}
                        creditsRemaining={credits}
                      />
                    </ResizablePanel>
                  </ResizablePanelGroup>
                </ResizablePanel>
                {terminalOpen && (
                  <>
                    <ResizableHandle withHandle />
                    <ResizablePanel defaultSize={30} minSize={10} maxSize={60}>
                      <SplitTerminal onCreateFile={handleTerminalCreate} onEditFile={handleTerminalEdit} onRunApp={handleRunApp} />
                    </ResizablePanel>
                  </>
                )}
              </ResizablePanelGroup>
            </div>
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>

      <StatusBar
        filename={activeEditorTab?.filename}
        language={activeEditorTab?.language || "Plain Text"}
        line={cursorPos.line} column={cursorPos.col}
        branch="main" problems={{ errors: 0, warnings: 0 }}
        terminalOpen={terminalOpen} onToggleTerminal={() => setTerminalOpen(p => !p)}
      />
    </div>
  );
}

// Tree helpers
function deleteNodeFromTree(nodes: FileNode[], path: string): FileNode[] {
  return nodes.filter((n) => {
    if (n.path === path) return false;
    if (n.children) n.children = deleteNodeFromTree(n.children, path);
    return true;
  });
}
function addNodeToTree(nodes: FileNode[], path: string, content: string): FileNode[] {
  const parts = path.split("/");
  if (parts.length === 1) {
    const existing = nodes.find((n) => n.name === parts[0] && n.type === "file");
    if (existing) return nodes.map((n) => n === existing ? { ...n, content } : n);
    return [...nodes, { name: parts[0], path, type: "file", content }];
  }
  const folderName = parts[0];
  const restPath = parts.slice(1).join("/");
  const folder = nodes.find((n) => n.name === folderName && n.type === "folder");
  if (folder) return nodes.map((n) => n === folder ? { ...n, children: addNodeToTree(n.children || [], restPath, content) } : n);
  return [...nodes, { name: folderName, path: folderName, type: "folder", children: addNodeToTree([], restPath, content) }];
}
function addFolderToTree(nodes: FileNode[], path: string): FileNode[] {
  const parts = path.split("/");
  if (parts.length === 1) {
    const existing = nodes.find((n) => n.name === parts[0] && n.type === "folder");
    if (existing) return nodes;
    return [...nodes, { name: parts[0], path, type: "folder", children: [] }];
  }
  const folderName = parts[0];
  const restPath = parts.slice(1).join("/");
  const folder = nodes.find((n) => n.name === folderName && n.type === "folder");
  if (folder) return nodes.map((n) => n === folder ? { ...n, children: addFolderToTree(n.children || [], restPath) } : n);
  return [...nodes, { name: folderName, path: folderName, type: "folder", children: addFolderToTree([], restPath) }];
}
function modifyNodeInTree(nodes: FileNode[], path: string, content: string): FileNode[] {
  return nodes.map((n) => {
    if (n.path === path && n.type === "file") return { ...n, content };
    if (n.children) return { ...n, children: modifyNodeInTree(n.children, path, content) };
    return n;
  });
}
function guessLanguage(filename: string): string {
  const ext = filename.split(".").pop()?.toLowerCase() || "";
  const map: Record<string, string> = { js: "javascript", ts: "typescript", jsx: "jsx", tsx: "tsx", py: "python", html: "html", css: "css", json: "json", md: "markdown", yml: "yaml", yaml: "yaml", sh: "bash", sql: "sql", xml: "xml", svg: "xml" };
  return map[ext] || "text";
}
