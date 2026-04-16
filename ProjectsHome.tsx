import { useState, useRef } from "react";
import { Plus, FolderOpen, Trash2, Clock, MessageSquare, FileText, Search, Sparkles, CreditCard, Upload } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { loadProjects, saveProjects, createProject, type Project } from "@/lib/projectStore";
import type { FileNode } from "@/components/FileExplorer";

interface ProjectsHomeProps {
  onOpenProject: (project: Project) => void;
}

export default function ProjectsHome({ onOpenProject }: ProjectsHomeProps) {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>(() => loadProjects());
  const [showNewDialog, setShowNewDialog] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDesc, setNewDesc] = useState("");
  const [search, setSearch] = useState("");
  const folderInputRef = useRef<HTMLInputElement>(null);

  const handleCreate = () => {
    if (!newName.trim()) return;
    const p = createProject(newName.trim(), newDesc.trim() || undefined);
    const updated = [p, ...projects];
    setProjects(updated);
    saveProjects(updated);
    setNewName("");
    setNewDesc("");
    setShowNewDialog(false);
    onOpenProject(p);
  };

  const handleDelete = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const updated = projects.filter(p => p.id !== id);
    setProjects(updated);
    saveProjects(updated);
  };

  const handleOpenFolder = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files;
    if (!fileList || fileList.length === 0) return;
    // Get folder name from first file's path
    const firstPath = (fileList[0] as any).webkitRelativePath || fileList[0].name;
    const folderName = firstPath.split("/")[0] || "Imported Folder";

    const tree: Record<string, FileNode> = {};
    const root: FileNode[] = [];

    for (let i = 0; i < fileList.length; i++) {
      const file = fileList[i];
      const relativePath = (file as any).webkitRelativePath || file.name;
      const parts = relativePath.split("/");
      // skip root folder name
      const partsWithoutRoot = parts.slice(1);
      if (partsWithoutRoot.length === 0) continue;

      let content: string | undefined;
      const ext = file.name.split(".").pop()?.toLowerCase() || "";
      const textExts = ["js","ts","jsx","tsx","html","css","json","md","txt","py","rb","java","c","cpp","h","go","rs","sh","yaml","yml","toml","xml","svg","sql","env","csv"];
      if (textExts.includes(ext)) { try { content = await file.text(); } catch { content = undefined; } }

      let currentLevel = root;
      let currentPath = "";
      for (let j = 0; j < partsWithoutRoot.length; j++) {
        const part = partsWithoutRoot[j];
        currentPath = currentPath ? `${currentPath}/${part}` : part;
        const isLast = j === partsWithoutRoot.length - 1;
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

    const p = createProject(folderName);
    p.files = root;
    const updated = [p, ...projects];
    setProjects(updated);
    saveProjects(updated);
    onOpenProject(p);
  };

  const filtered = projects.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.description?.toLowerCase().includes(search.toLowerCase())
  );

  const formatDate = (ts: number) => {
    const diff = Date.now() - ts;
    if (diff < 60000) return "Just now";
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
    return new Date(ts).toLocaleDateString("en-US", { month: "short", day: "numeric" });
  };

  return (
    <div className="h-screen w-full bg-background flex flex-col overflow-hidden">
      <header className="border-b border-border bg-card/50 backdrop-blur-sm">
        <div className="max-w-6xl mx-auto px-6 py-5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center shadow-lg shadow-primary/20">
              <Sparkles className="w-5 h-5 text-primary-foreground" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-foreground tracking-tight">PrimeCODE</h1>
              <p className="text-xs text-muted-foreground">by RanEddie</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => navigate("/pricing")}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-border text-muted-foreground hover:text-foreground hover:bg-secondary transition-all text-sm">
              <CreditCard className="w-4 h-4" /> Pricing
            </button>
            <button onClick={() => folderInputRef.current?.click()}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl border border-border text-foreground hover:bg-secondary transition-all text-sm font-medium">
              <FolderOpen className="w-4 h-4" /> Open Folder
            </button>
            <input ref={folderInputRef} type="file"
              // @ts-ignore
              webkitdirectory="" directory="" multiple onChange={handleOpenFolder} className="hidden" />
            <button onClick={() => setShowNewDialog(true)}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 transition-all font-medium text-sm shadow-lg shadow-primary/25 hover:shadow-primary/40 hover:scale-[1.02] active:scale-[0.98]">
              <Plus className="w-4 h-4" /> New Project
            </button>
          </div>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto">
        <div className="max-w-6xl mx-auto px-6 py-8">
          {projects.length > 0 && (
            <div className="relative mb-8">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search projects..."
                className="w-full max-w-md bg-card border border-border rounded-xl pl-11 pr-4 py-3 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50 focus:ring-2 focus:ring-primary/20 transition-all" />
            </div>
          )}

          {projects.length === 0 && (
            <div className="flex flex-col items-center justify-center py-32 gap-6">
              <div className="w-24 h-24 rounded-3xl bg-primary/10 flex items-center justify-center">
                <FolderOpen className="w-12 h-12 text-primary/60" />
              </div>
              <div className="text-center">
                <h2 className="text-2xl font-bold text-foreground mb-2">No projects yet</h2>
                <p className="text-muted-foreground max-w-sm">Create a new project or open an existing folder to get started.</p>
              </div>
              <div className="flex gap-3">
                <button onClick={() => folderInputRef.current?.click()}
                  className="flex items-center gap-2 px-6 py-3 rounded-xl border border-border text-foreground hover:bg-secondary transition-all font-medium">
                  <FolderOpen className="w-4 h-4" /> Open Folder
                </button>
                <button onClick={() => setShowNewDialog(true)}
                  className="flex items-center gap-2 px-6 py-3 rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 transition-all font-medium shadow-lg shadow-primary/25">
                  <Plus className="w-4 h-4" /> Create Project
                </button>
              </div>
            </div>
          )}

          {filtered.length > 0 && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {filtered.map(project => (
                <div key={project.id} onClick={() => onOpenProject(project)}
                  className="group relative bg-card border border-border rounded-2xl p-5 cursor-pointer hover:border-primary/40 hover:shadow-xl hover:shadow-primary/5 transition-all duration-200 hover:-translate-y-0.5">
                  <div className="absolute top-0 left-6 right-6 h-1 rounded-b-full opacity-60 group-hover:opacity-100 transition-opacity"
                    style={{ background: `hsl(${project.color})` }} />
                  <div className="flex items-start justify-between mb-3 mt-1">
                    <div className="w-10 h-10 rounded-xl flex items-center justify-center text-white font-bold text-sm shadow-md"
                      style={{ background: `hsl(${project.color})` }}>
                      {project.name.charAt(0).toUpperCase()}
                    </div>
                    <button onClick={(e) => handleDelete(project.id, e)}
                      className="p-1.5 rounded-lg text-muted-foreground opacity-0 group-hover:opacity-100 hover:text-destructive hover:bg-destructive/10 transition-all">
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <h3 className="font-semibold text-foreground mb-1 truncate">{project.name}</h3>
                  {project.description && <p className="text-xs text-muted-foreground mb-4 line-clamp-2">{project.description}</p>}
                  <div className="flex items-center gap-4 text-[11px] text-muted-foreground">
                    <span className="flex items-center gap-1"><MessageSquare className="w-3 h-3" />{project.chats.length}</span>
                    <span className="flex items-center gap-1"><FileText className="w-3 h-3" />{countFiles(project.files)}</span>
                    <span className="flex items-center gap-1 ml-auto"><Clock className="w-3 h-3" />{formatDate(project.updatedAt)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>

      {showNewDialog && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4" onClick={() => setShowNewDialog(false)}>
          <div className="bg-card border border-border rounded-2xl w-full max-w-md p-6 shadow-2xl" onClick={e => e.stopPropagation()}>
            <h2 className="text-lg font-bold text-foreground mb-4">New Project</h2>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-medium text-muted-foreground mb-1 block">Project Name</label>
                <input value={newName} onChange={e => setNewName(e.target.value)} placeholder="My Awesome App" autoFocus
                  onKeyDown={e => e.key === "Enter" && handleCreate()}
                  className="w-full bg-secondary border border-border rounded-xl px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50 transition-colors" />
              </div>
              <div>
                <label className="text-xs font-medium text-muted-foreground mb-1 block">Description (optional)</label>
                <input value={newDesc} onChange={e => setNewDesc(e.target.value)} placeholder="A brief description..."
                  onKeyDown={e => e.key === "Enter" && handleCreate()}
                  className="w-full bg-secondary border border-border rounded-xl px-4 py-2.5 text-sm text-foreground placeholder:text-muted-foreground outline-none focus:border-primary/50 transition-colors" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-6">
              <button onClick={() => setShowNewDialog(false)} className="px-4 py-2 rounded-xl text-sm text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors">Cancel</button>
              <button onClick={handleCreate} disabled={!newName.trim()}
                className="px-5 py-2 rounded-xl bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-40 disabled:cursor-not-allowed transition-all">Create</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function countFiles(files: any[]): number {
  let count = 0;
  for (const f of files) {
    if (f.type === "file") count++;
    if (f.children) count += countFiles(f.children);
  }
  return count;
}
