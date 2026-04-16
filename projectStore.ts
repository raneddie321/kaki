import type { Chat } from "./chatStore";
import type { FileNode } from "@/components/FileExplorer";

export interface Project {
  id: string;
  name: string;
  description?: string;
  icon?: string;
  color: string;
  chats: Chat[];
  files: FileNode[];
  createdAt: number;
  updatedAt: number;
}

const STORAGE_KEY = "raneddie_projects";

const PROJECT_COLORS = [
  "270 60% 60%",  // purple
  "200 80% 55%",  // blue
  "160 60% 45%",  // green
  "30 90% 55%",   // orange
  "340 70% 55%",  // pink
  "50 80% 50%",   // yellow
  "180 60% 45%",  // teal
  "0 70% 55%",    // red
];

export function loadProjects(): Project[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function saveProjects(projects: Project[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(projects));
  } catch (e) {
    console.warn("Failed to save projects - storage quota exceeded. Trimming old chat messages.");
    // Trim chat messages to fit
    const trimmed = projects.map(p => ({
      ...p,
      chats: p.chats.map(c => ({
        ...c,
        messages: c.messages.slice(-50), // keep last 50 messages per chat
      })),
    }));
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(trimmed));
    } catch {
      console.error("Still cannot save after trimming.");
    }
  }
}

export function createProject(name: string, description?: string): Project {
  const colorIndex = Math.floor(Math.random() * PROJECT_COLORS.length);
  return {
    id: crypto.randomUUID(),
    name,
    description,
    color: PROJECT_COLORS[colorIndex],
    chats: [],
    files: [],
    createdAt: Date.now(),
    updatedAt: Date.now(),
  };
}
