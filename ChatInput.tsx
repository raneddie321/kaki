import { Send, FileCode, Plus, Brain, Image, Video, ChevronDown } from "lucide-react";
import { useState, useRef, useEffect, useCallback } from "react";
import { createPortal } from "react-dom";
import { getAllModels, loadFavoriteModels } from "@/lib/modelStore";
import type { FileNode } from "./FileExplorer";

type ChatMode = "build" | "plan";

interface ChatInputProps {
  onSend: (message: string, options?: { model?: string; mode?: string; thinking?: boolean }) => void;
  disabled?: boolean;
  placeholder?: string;
  isPrimeCode?: boolean;
  projectFiles?: FileNode[];
  onFileReference?: (file: FileNode) => void;
  onOpenModelManager?: () => void;
}

function useDropdownPosition(triggerRef: React.RefObject<HTMLElement | null>, open: boolean) {
  const [pos, setPos] = useState({ top: 0, left: 0, right: 0 });
  useEffect(() => {
    if (!open || !triggerRef.current) return;
    const rect = triggerRef.current.getBoundingClientRect();
    setPos({ top: rect.top, left: rect.left, right: window.innerWidth - rect.right });
  }, [open, triggerRef]);
  return pos;
}

export function ChatInput({ onSend, disabled, placeholder, isPrimeCode, projectFiles, onFileReference, onOpenModelManager }: ChatInputProps) {
  const [value, setValue] = useState("");
  const [showFilePicker, setShowFilePicker] = useState(false);
  const [fileFilter, setFileFilter] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [selectedModel, setSelectedModel] = useState("auto");
  const [chatMode, setChatMode] = useState<ChatMode>("build");
  const [thinking, setThinking] = useState(false);
  const [showModelPicker, setShowModelPicker] = useState(false);
  const [showModePicker, setShowModePicker] = useState(false);
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const pickerRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const modelBtnRef = useRef<HTMLButtonElement>(null);
  const modeBtnRef = useRef<HTMLButtonElement>(null);
  const attachBtnRef = useRef<HTMLButtonElement>(null);

  const modelPos = useDropdownPosition(modelBtnRef, showModelPicker);
  const modePos = useDropdownPosition(modeBtnRef, showModePicker);
  const attachPos = useDropdownPosition(attachBtnRef, showAttachMenu);

  const allModels = getAllModels();
  const favoriteIds = loadFavoriteModels();
  // Only show favorited models in the dropdown
  const favoriteModels = favoriteIds.map(id => allModels.find(m => m.id === id)).filter(Boolean) as typeof allModels;

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + "px";
    }
  }, [value]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      const t = e.target as Node;
      if (showModelPicker && modelBtnRef.current && !modelBtnRef.current.contains(t)) {
        const portal = document.getElementById("model-picker-portal");
        if (!portal || !portal.contains(t)) setShowModelPicker(false);
      }
      if (showModePicker && modeBtnRef.current && !modeBtnRef.current.contains(t)) {
        const portal = document.getElementById("mode-picker-portal");
        if (!portal || !portal.contains(t)) setShowModePicker(false);
      }
      if (showAttachMenu && attachBtnRef.current && !attachBtnRef.current.contains(t)) {
        const portal = document.getElementById("attach-picker-portal");
        if (!portal || !portal.contains(t)) setShowAttachMenu(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [showModelPicker, showModePicker, showAttachMenu]);

  const filteredFiles = (projectFiles || []).filter((f) =>
    f.name.toLowerCase().includes(fileFilter.toLowerCase()) || f.path.toLowerCase().includes(fileFilter.toLowerCase())
  );

  useEffect(() => { setSelectedIndex(0); }, [fileFilter]);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newVal = e.target.value;
    setValue(newVal);
    if (isPrimeCode && projectFiles && projectFiles.length > 0) {
      const cursorPos = e.target.selectionStart;
      const textBefore = newVal.slice(0, cursorPos);
      const lastSlash = textBefore.lastIndexOf("/");
      if (lastSlash !== -1 && (lastSlash === 0 || newVal[lastSlash - 1] === " " || newVal[lastSlash - 1] === "\n")) {
        setFileFilter(textBefore.slice(lastSlash + 1));
        setShowFilePicker(true);
      } else {
        setShowFilePicker(false);
      }
    }
  };

  const insertFileReference = (file: FileNode) => {
    const cursorPos = textareaRef.current?.selectionStart || value.length;
    const textBefore = value.slice(0, cursorPos);
    const lastSlash = textBefore.lastIndexOf("/");
    const before = value.slice(0, lastSlash);
    const after = value.slice(cursorPos);
    setValue(`${before}@${file.path} ${after}`);
    setShowFilePicker(false);
    onFileReference?.(file);
    textareaRef.current?.focus();
  };

  const handleSubmit = () => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed, { model: selectedModel, mode: chatMode, thinking });
    setValue("");
    setShowFilePicker(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (showFilePicker && filteredFiles.length > 0) {
      if (e.key === "ArrowDown") { e.preventDefault(); setSelectedIndex((i) => Math.min(i + 1, filteredFiles.length - 1)); return; }
      if (e.key === "ArrowUp") { e.preventDefault(); setSelectedIndex((i) => Math.max(i - 1, 0)); return; }
      if (e.key === "Enter" || e.key === "Tab") { e.preventDefault(); insertFileReference(filteredFiles[selectedIndex]); return; }
      if (e.key === "Escape") { setShowFilePicker(false); return; }
    }
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSubmit(); }
  };

  const currentModel = favoriteModels.find(m => m.id === selectedModel) || allModels.find(m => m.id === selectedModel) || allModels[0];

  return (
    <div className="p-3 relative">
      {showFilePicker && filteredFiles.length > 0 && (
        <div ref={pickerRef} className="absolute bottom-full left-3 right-3 mb-2 max-h-48 overflow-y-auto bg-popover border border-border rounded-xl shadow-2xl z-50">
          <div className="p-2 border-b border-border flex items-center gap-2 text-xs text-muted-foreground">
            <FileCode className="w-3 h-3" />Select a file to reference
          </div>
          {filteredFiles.slice(0, 20).map((file, i) => (
            <button key={file.path} onClick={() => insertFileReference(file)}
              className={`w-full text-left px-3 py-1.5 text-xs flex items-center gap-2 transition-colors ${i === selectedIndex ? "bg-primary/20 text-primary" : "text-foreground hover:bg-secondary"}`}>
              <FileCode className="w-3 h-3 flex-shrink-0 text-muted-foreground" />
              <span className="truncate">{file.path}</span>
            </button>
          ))}
        </div>
      )}

      <input type="file" ref={fileInputRef} className="hidden" accept="image/*,video/*" multiple />

      <div className="max-w-3xl mx-auto bg-card border border-border rounded-2xl overflow-hidden shadow-xl">
        <div className="px-4 pt-3 pb-1">
          <textarea
            ref={textareaRef} value={value} onChange={handleChange} onKeyDown={handleKeyDown}
            placeholder={placeholder || "What do you want to build today"}
            disabled={disabled} rows={1}
            className="w-full bg-transparent resize-none border-none outline-none text-sm text-foreground placeholder:text-muted-foreground max-h-[200px]"
          />
        </div>

        <div className="px-2 pb-2 flex items-center justify-between">
          <button ref={attachBtnRef} onClick={() => { setShowAttachMenu(!showAttachMenu); setShowModelPicker(false); setShowModePicker(false); }}
            className="p-2 rounded-lg text-muted-foreground hover:text-foreground hover:bg-secondary/80 transition-colors" title="Attach files">
            <Plus className="w-4 h-4" />
          </button>

          <div className="flex items-center gap-1">
            <button ref={modelBtnRef} onClick={() => { setShowModelPicker(!showModelPicker); setShowModePicker(false); setShowAttachMenu(false); }}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs text-muted-foreground hover:text-foreground hover:bg-secondary/80 transition-colors font-medium border border-transparent hover:border-border">
              {currentModel?.label || "Auto"}
              <ChevronDown className="w-3 h-3" />
            </button>

            <button ref={modeBtnRef} onClick={() => { setShowModePicker(!showModePicker); setShowModelPicker(false); setShowAttachMenu(false); }}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors border border-transparent hover:border-border hover:bg-secondary/80 ${chatMode === "build" ? "text-foreground" : "text-accent"}`}>
              {chatMode === "build" ? "Build" : "Plan"}
              <ChevronDown className="w-3 h-3" />
            </button>

            <button onClick={() => setThinking(!thinking)}
              className={`p-1.5 rounded-lg transition-colors ${thinking ? "text-primary bg-primary/15" : "text-muted-foreground hover:text-foreground hover:bg-secondary/80"}`}
              title={thinking ? "Thinking mode on" : "Thinking mode off"}>
              <Brain className="w-3.5 h-3.5" />
            </button>

            <button onClick={handleSubmit} disabled={disabled || !value.trim()}
              className="p-2 rounded-full bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-30 disabled:cursor-not-allowed transition-all ml-0.5">
              <Send className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* PORTAL: Model Picker - only favorites */}
      {showModelPicker && createPortal(
        <div id="model-picker-portal"
          className="fixed z-[9999] w-[340px] bg-popover border border-border rounded-xl shadow-2xl overflow-hidden"
          style={{ bottom: window.innerHeight - modelPos.top + 8, right: modelPos.right }}>
          <div className="px-4 py-3 border-b border-border flex items-center justify-between">
            <span className="text-xs font-semibold text-foreground">Select Model</span>
          </div>
          <div className="max-h-[60vh] overflow-y-auto p-1.5">
            <button onClick={() => { setSelectedModel("auto"); setShowModelPicker(false); }}
              className={`w-full text-left px-3 py-2.5 rounded-lg flex flex-col transition-colors mb-0.5 ${selectedModel === "auto" ? "bg-primary/15 text-primary" : "text-foreground hover:bg-secondary"}`}>
              <span className="text-xs font-medium">Auto</span>
              <span className="text-[10px] text-muted-foreground mt-0.5">Automatically select the best model</span>
            </button>
            {favoriteModels.length > 0 && (
              <>
                <div className="my-1 h-px bg-border mx-2" />
                {favoriteModels.map((model) => (
                  <button key={model.id} onClick={() => { setSelectedModel(model.id); setShowModelPicker(false); }}
                    className={`w-full text-left px-3 py-2.5 rounded-lg flex flex-col transition-colors mb-0.5 ${selectedModel === model.id ? "bg-primary/15 text-primary" : "text-foreground hover:bg-secondary"}`}>
                    <span className="text-xs font-medium">{model.label}</span>
                    <span className="text-[10px] text-muted-foreground mt-0.5 line-clamp-1">{model.description}</span>
                  </button>
                ))}
              </>
            )}
            {favoriteModels.length === 0 && (
              <div className="px-3 py-4 text-center text-xs text-muted-foreground">
                No favorite models yet. Open Model Manager to add favorites.
              </div>
            )}
          </div>
          <div className="border-t border-border">
            <button onClick={() => { setShowModelPicker(false); onOpenModelManager?.(); }}
              className="w-full text-left px-4 py-3 text-xs text-primary hover:bg-secondary transition-colors font-medium">
              Manage Models →
            </button>
          </div>
        </div>,
        document.body
      )}

      {/* PORTAL: Mode Picker */}
      {showModePicker && createPortal(
        <div id="mode-picker-portal"
          className="fixed z-[9999] w-72 bg-popover border border-border rounded-xl shadow-2xl overflow-hidden"
          style={{ bottom: window.innerHeight - modePos.top + 8, right: modePos.right }}>
          <div className="px-4 py-3 border-b border-border">
            <span className="text-xs font-semibold text-foreground">Select Mode</span>
          </div>
          <div className="p-1.5">
            <button onClick={() => { setChatMode("build"); setShowModePicker(false); }}
              className={`w-full text-left px-4 py-3.5 rounded-lg flex flex-col transition-colors mb-1 ${chatMode === "build" ? "bg-primary/15 border border-primary/30" : "text-foreground hover:bg-secondary border border-transparent"}`}>
              <span className="text-sm font-semibold">🔨 Build</span>
              <span className="text-[11px] text-muted-foreground mt-1">Generate code, create files, and build features</span>
            </button>
            <button onClick={() => { setChatMode("plan"); setShowModePicker(false); }}
              className={`w-full text-left px-4 py-3.5 rounded-lg flex flex-col transition-colors ${chatMode === "plan" ? "bg-primary/15 border border-primary/30" : "text-foreground hover:bg-secondary border border-transparent"}`}>
              <span className="text-sm font-semibold">📋 Plan</span>
              <span className="text-[11px] text-muted-foreground mt-1">Discuss architecture, plan before coding</span>
            </button>
          </div>
        </div>,
        document.body
      )}

      {/* PORTAL: Attach Menu */}
      {showAttachMenu && createPortal(
        <div id="attach-picker-portal"
          className="fixed z-[9999] w-48 bg-popover border border-border rounded-xl shadow-xl py-1.5"
          style={{ bottom: window.innerHeight - attachPos.top + 8, left: attachPos.left }}>
          <button onClick={() => { fileInputRef.current?.click(); setShowAttachMenu(false); }}
            className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-foreground hover:bg-secondary transition-colors">
            <Image className="w-4 h-4 text-muted-foreground" />Image
          </button>
          <button onClick={() => { fileInputRef.current?.click(); setShowAttachMenu(false); }}
            className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-foreground hover:bg-secondary transition-colors">
            <Video className="w-4 h-4 text-muted-foreground" />Video
          </button>
        </div>,
        document.body
      )}
    </div>
  );
}
