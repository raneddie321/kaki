import { Folder, File, ChevronRight, ChevronDown, FolderOpen, Upload, FilePlus, FolderPlus, Trash2, Pencil, Check, X } from "lucide-react";
import { FileIcon } from "./FileIcon";
import { useState, useRef } from "react";

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "folder";
  content?: string;
  children?: FileNode[];
}

interface FileExplorerProps {
  files: FileNode[];
  onFilesLoaded: (files: FileNode[]) => void;
  onFileSelect: (file: FileNode) => void;
  onCreateFile?: (path: string, content: string) => void;
  onCreateFolder?: (path: string) => void;
  onDeleteFile?: (path: string) => void;
  selectedPath?: string;
}

export function getAllFiles(nodes: FileNode[]): FileNode[] {
  const result: FileNode[] = [];
  for (const node of nodes) {
    if (node.type === "file") result.push(node);
    if (node.children) result.push(...getAllFiles(node.children));
  }
  return result;
}

export function FileExplorer({ files, onFilesLoaded, onFileSelect, onCreateFile, onCreateFolder, onDeleteFile, selectedPath }: FileExplorerProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [creating, setCreating] = useState<{ type: "file" | "folder"; parentPath: string } | null>(null);
  const [newName, setNewName] = useState("");

  const handleFolderOpen = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files;
    if (!fileList) return;
    const tree: Record<string, FileNode> = {};
    const root: FileNode[] = [];
    if (fileList.length === 0) { onFilesLoaded([]); return; }
    for (let i = 0; i < fileList.length; i++) {
      const file = fileList[i];
      const relativePath = (file as any).webkitRelativePath || file.name;
      const parts = relativePath.split("/");
      let content: string | undefined;
      if (isTextFile(file.name)) { try { content = await file.text(); } catch { content = undefined; } }
      let currentLevel = root;
      let currentPath = "";
      for (let j = 0; j < parts.length; j++) {
        const part = parts[j];
        currentPath = currentPath ? `${currentPath}/${part}` : part;
        const isLast = j === parts.length - 1;
        if (isLast) {
          currentLevel.push({ name: part, path: currentPath, type: "file", content });
        } else {
          if (!tree[currentPath]) {
            const folder: FileNode = { name: part, path: currentPath, type: "folder", children: [] };
            tree[currentPath] = folder;
            currentLevel.push(folder);
          }
          currentLevel = tree[currentPath].children!;
        }
      }
    }
    onFilesLoaded(root);
  };

  const handleCreateConfirm = () => {
    if (!newName.trim() || !creating) return;
    const path = creating.parentPath ? `${creating.parentPath}/${newName.trim()}` : newName.trim();
    if (creating.type === "file") {
      onCreateFile?.(path, "");
    } else {
      onCreateFolder?.(path);
    }
    setCreating(null);
    setNewName("");
  };

  return (
    <div className="flex flex-col h-full">
      <div className="p-2 border-b border-border flex items-center gap-1">
        <button onClick={() => inputRef.current?.click()}
          className="flex items-center gap-1 px-2 py-1 rounded text-xs text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors" title="Open Folder">
          <Upload className="w-3.5 h-3.5" />
        </button>
        <button onClick={() => { setCreating({ type: "file", parentPath: "" }); setNewName(""); }}
          className="flex items-center gap-1 px-2 py-1 rounded text-xs text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors" title="New File">
          <FilePlus className="w-3.5 h-3.5" />
        </button>
        <button onClick={() => { setCreating({ type: "folder", parentPath: "" }); setNewName(""); }}
          className="flex items-center gap-1 px-2 py-1 rounded text-xs text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors" title="New Folder">
          <FolderPlus className="w-3.5 h-3.5" />
        </button>
        <input ref={inputRef} type="file"
          // @ts-ignore
          webkitdirectory="" directory="" multiple onChange={handleFolderOpen} className="hidden" />
      </div>

      {creating && creating.parentPath === "" && (
        <div className="flex items-center gap-1 px-3 py-1 bg-secondary/50">
          {creating.type === "folder" ? <Folder className="w-3 h-3 text-primary" /> : <File className="w-3 h-3 text-muted-foreground" />}
          <input value={newName} onChange={e => setNewName(e.target.value)} onKeyDown={e => { if (e.key === "Enter") handleCreateConfirm(); if (e.key === "Escape") setCreating(null); }}
            placeholder={creating.type === "file" ? "filename.ext" : "folder-name"} autoFocus
            className="flex-1 bg-transparent text-xs text-foreground outline-none placeholder:text-muted-foreground" />
          <button onClick={handleCreateConfirm} className="p-0.5 text-primary"><Check className="w-3 h-3" /></button>
          <button onClick={() => setCreating(null)} className="p-0.5 text-muted-foreground"><X className="w-3 h-3" /></button>
        </div>
      )}

      <div className="flex-1 overflow-y-auto p-1">
        {files.length === 0 && !creating ? (
          <p className="text-xs text-muted-foreground px-3 py-4 text-center">No files yet. Create one or open a folder.</p>
        ) : (
          <TreeNodes nodes={files} onFileSelect={onFileSelect} onDeleteFile={onDeleteFile} selectedPath={selectedPath} depth={0}
            creating={creating} setCreating={setCreating} newName={newName} setNewName={setNewName} onCreateConfirm={handleCreateConfirm}
            onCreateFile={onCreateFile} onCreateFolder={onCreateFolder} />
        )}
      </div>
    </div>
  );
}

function TreeNodes({ nodes, onFileSelect, onDeleteFile, selectedPath, depth, creating, setCreating, newName, setNewName, onCreateConfirm, onCreateFile, onCreateFolder }: {
  nodes: FileNode[]; onFileSelect: (f: FileNode) => void; onDeleteFile?: (path: string) => void; selectedPath?: string; depth: number;
  creating: { type: "file" | "folder"; parentPath: string } | null;
  setCreating: (v: { type: "file" | "folder"; parentPath: string } | null) => void;
  newName: string; setNewName: (v: string) => void; onCreateConfirm: () => void;
  onCreateFile?: (path: string, content: string) => void; onCreateFolder?: (path: string) => void;
}) {
  const sorted = [...nodes].sort((a, b) => {
    if (a.type !== b.type) return a.type === "folder" ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
  return (
    <>
      {sorted.map((node) => (
        <TreeNode key={node.path} node={node} onFileSelect={onFileSelect} onDeleteFile={onDeleteFile} selectedPath={selectedPath} depth={depth}
          creating={creating} setCreating={setCreating} newName={newName} setNewName={setNewName} onCreateConfirm={onCreateConfirm}
          onCreateFile={onCreateFile} onCreateFolder={onCreateFolder} />
      ))}
    </>
  );
}

function TreeNode({ node, onFileSelect, onDeleteFile, selectedPath, depth, creating, setCreating, newName, setNewName, onCreateConfirm, onCreateFile, onCreateFolder }: {
  node: FileNode; onFileSelect: (f: FileNode) => void; onDeleteFile?: (path: string) => void; selectedPath?: string; depth: number;
  creating: { type: "file" | "folder"; parentPath: string } | null;
  setCreating: (v: { type: "file" | "folder"; parentPath: string } | null) => void;
  newName: string; setNewName: (v: string) => void; onCreateConfirm: () => void;
  onCreateFile?: (path: string, content: string) => void; onCreateFolder?: (path: string) => void;
}) {
  const [expanded, setExpanded] = useState(depth === 0);
  const isSelected = node.path === selectedPath;

  if (node.type === "folder") {
    const isCreatingHere = creating?.parentPath === node.path;
    return (
      <div>
        <div className="group flex items-center">
          <button onClick={() => setExpanded(!expanded)}
            className="flex-1 flex items-center gap-1 px-1 py-0.5 rounded text-xs hover:bg-sidebar-hover transition-colors text-sidebar-foreground"
            style={{ paddingLeft: depth * 12 + 4 }}>
            {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
            {expanded ? <FolderOpen className="w-3.5 h-3.5 text-primary" /> : <Folder className="w-3.5 h-3.5 text-primary" />}
            <span className="truncate">{node.name}</span>
          </button>
          <div className="hidden group-hover:flex items-center gap-0.5 pr-1">
            <button onClick={() => { setCreating({ type: "file", parentPath: node.path }); setNewName(""); setExpanded(true); }}
              className="p-0.5 text-muted-foreground hover:text-foreground"><FilePlus className="w-3 h-3" /></button>
            <button onClick={() => { setCreating({ type: "folder", parentPath: node.path }); setNewName(""); setExpanded(true); }}
              className="p-0.5 text-muted-foreground hover:text-foreground"><FolderPlus className="w-3 h-3" /></button>
            <button onClick={() => onDeleteFile?.(node.path)}
              className="p-0.5 text-muted-foreground hover:text-destructive"><Trash2 className="w-3 h-3" /></button>
          </div>
        </div>
        {expanded && (
          <>
            {isCreatingHere && (
              <div className="flex items-center gap-1 py-0.5" style={{ paddingLeft: (depth + 1) * 12 + 16 }}>
                {creating!.type === "folder" ? <Folder className="w-3 h-3 text-primary" /> : <File className="w-3 h-3 text-muted-foreground" />}
                <input value={newName} onChange={e => setNewName(e.target.value)}
                  onKeyDown={e => { if (e.key === "Enter") onCreateConfirm(); if (e.key === "Escape") setCreating(null); }}
                  placeholder={creating!.type === "file" ? "filename" : "folder"} autoFocus
                  className="flex-1 bg-transparent text-xs text-foreground outline-none placeholder:text-muted-foreground" />
              </div>
            )}
            {node.children && (
              <TreeNodes nodes={node.children} onFileSelect={onFileSelect} onDeleteFile={onDeleteFile} selectedPath={selectedPath} depth={depth + 1}
                creating={creating} setCreating={setCreating} newName={newName} setNewName={setNewName} onCreateConfirm={onCreateConfirm}
                onCreateFile={onCreateFile} onCreateFolder={onCreateFolder} />
            )}
          </>
        )}
      </div>
    );
  }

  return (
    <div className="group flex items-center">
      <button onClick={() => onFileSelect(node)}
        className={`flex-1 flex items-center gap-1 px-1 py-0.5 rounded text-xs transition-colors ${
          isSelected ? "bg-sidebar-accent text-foreground" : "text-sidebar-foreground hover:bg-sidebar-hover"
        }`}
        style={{ paddingLeft: depth * 12 + 16 }}>
        <FileIcon filename={node.name} className="w-4 h-4" />
        <span className="truncate">{node.name}</span>
      </button>
      <div className="hidden group-hover:flex items-center pr-1">
        <button onClick={() => onDeleteFile?.(node.path)}
          className="p-0.5 text-muted-foreground hover:text-destructive"><Trash2 className="w-3 h-3" /></button>
      </div>
    </div>
  );
}

function isTextFile(name: string): boolean {
  const ext = name.split(".").pop()?.toLowerCase() || "";
  return ["js","ts","jsx","tsx","html","css","json","md","txt","py","rb","java","c","cpp","h","go","rs","sh","bash","yaml","yml","toml","xml","svg","sql","env","gitignore","dockerfile","makefile","csv"].includes(ext);
}
