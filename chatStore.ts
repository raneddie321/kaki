export type Message = {
  role: "user" | "assistant";
  content: string;
};

export type ChatMode = "raneddie" | "primecode" | "byok" | "local";

export type Chat = {
  id: string;
  title: string;
  messages: Message[];
  mode: ChatMode;
  createdAt: number;
  projectPath?: string; // For PrimeCODE: associates chat with a project folder
};

const STORAGE_KEY = "raneddie_chats";

export function loadChats(): Chat[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

export function saveChats(chats: Chat[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(chats));
}

export function createChat(mode: ChatMode = "raneddie", projectPath?: string): Chat {
  const labels: Record<ChatMode, string> = {
    raneddie: "New Chat",
    primecode: "PrimeCODE Session",
    byok: "BYOK Chat",
    local: "Local Chat",
  };
  return {
    id: crypto.randomUUID(),
    title: labels[mode],
    messages: [],
    mode,
    createdAt: Date.now(),
    projectPath,
  };
}
