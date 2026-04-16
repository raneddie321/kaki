import { useRef, useEffect } from "react";
import { ChatMessage } from "./ChatMessage";
import { ChatInput } from "./ChatInput";
import { BYOKSetup } from "./BYOKSetup";
import { Zap, Code2, Key, Monitor } from "lucide-react";
import type { Chat, ChatMode } from "@/lib/chatStore";
import type { FileNode } from "./FileExplorer";
import { loadBYOKConfig } from "@/lib/byokProviders";

export type ChatModeType = ChatMode;

interface ChatViewProps {
  chat: Chat | null;
  isStreaming: boolean;
  onSend: (message: string, options?: { model?: string; mode?: string; thinking?: boolean }) => void;
  onOpenPreview?: (code: string, language: string, filename?: string) => void;
  projectFiles?: FileNode[];
  onFileReference?: (file: FileNode) => void;
  chatMode: ChatModeType;
  onChatModeChange: (mode: ChatModeType) => void;
  onOpenModelManager?: () => void;
  creditsRemaining?: number | null;
}

const CHAT_MODES: { id: ChatModeType; label: string; icon: React.ElementType; description: string }[] = [
  { id: "raneddie", label: "RanEddie", icon: Code2, description: "Default AI assistant" },
  { id: "primecode", label: "PrimeCODE", icon: Zap, description: "Project-aware coding agent" },
  { id: "byok", label: "BYOK", icon: Key, description: "Bring your own API key" },
  { id: "local", label: "Local", icon: Monitor, description: "Run models locally" },
];

function ModeBar({ chatMode, onChatModeChange }: { chatMode: ChatModeType; onChatModeChange: (m: ChatModeType) => void }) {
  return (
    <div className="px-3 py-1.5 border-b border-border flex items-center gap-0.5 bg-card/50">
      {CHAT_MODES.map(mode => {
        const Icon = mode.icon;
        return (
          <button
            key={mode.id}
            onClick={() => onChatModeChange(mode.id)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-medium transition-all ${
              chatMode === mode.id
                ? "bg-primary/15 text-primary shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-secondary/60"
            }`}
            title={mode.description}
          >
            <Icon className="w-3.5 h-3.5" />
            {mode.label}
          </button>
        );
      })}
    </div>
  );
}

export function ChatView({ chat, isStreaming, onSend, onOpenPreview, projectFiles, onFileReference, chatMode, onChatModeChange, onOpenModelManager, creditsRemaining }: ChatViewProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chat?.messages]);

  const isPrime = chatMode === "primecode";
  const currentMode = CHAT_MODES.find(m => m.id === chatMode) || CHAT_MODES[0];

  // Show BYOK setup if no config
  const byokConfigured = chatMode === "byok" ? !!loadBYOKConfig() : true;

  // Empty state (no chat selected)
  if (!chat) {
    return (
      <div className="flex-1 flex flex-col h-full">
        <ModeBar chatMode={chatMode} onChatModeChange={onChatModeChange} />

        {chatMode === "byok" && !byokConfigured ? (
          <BYOKSetup onConfigured={() => {}} />
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center gap-4">
            <div className="w-16 h-16 rounded-2xl bg-primary/20 flex items-center justify-center">
              {(() => { const Icon = currentMode.icon; return <Icon className="w-8 h-8 text-primary" />; })()}
            </div>
            <h2 className="text-xl font-semibold text-foreground">{currentMode.label}</h2>
            <p className="text-muted-foreground text-sm text-center max-w-md">{currentMode.description}</p>
          </div>
        )}

        <ChatInput
          onSend={onSend}
          disabled={isStreaming}
          placeholder="What do you want to build today"
          isPrimeCode={isPrime}
          projectFiles={projectFiles}
          onFileReference={onFileReference}
          onOpenModelManager={onOpenModelManager}
        />
      </div>
    );
  }

  const outOfCredits = creditsRemaining !== null && creditsRemaining !== undefined && creditsRemaining <= 0;

  return (
    <div className="flex-1 flex flex-col min-h-0 h-full">
      <ModeBar chatMode={chatMode} onChatModeChange={onChatModeChange} />

      {outOfCredits && (
        <div className="px-4 py-3 bg-destructive/10 border-b border-destructive/30 text-center">
          <p className="text-sm font-medium text-destructive">
            ⚠️ You have reached the message limit. Upgrade to Pro to get more.
          </p>
        </div>
      )}

      <div className="flex-1 overflow-y-auto">
        {chat.messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-center px-4">
            <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
              {(() => { const Icon = currentMode.icon; return <Icon className="w-6 h-6 text-primary" />; })()}
            </div>
            <h3 className="text-lg font-medium text-foreground">{currentMode.label} Ready</h3>
            <p className="text-sm text-muted-foreground max-w-sm">
              {isPrime
                ? "Open a folder, type / to reference files, then describe your changes."
                : chatMode === "byok"
                ? "Connected to your provider. Start chatting."
                : chatMode === "local"
                ? "Run AI models locally on your machine."
                : "Describe what you want to create and I'll generate the code for you."}
            </p>
          </div>
        )}
        {chat.messages.map((msg, i) => (
          <ChatMessage
            key={i}
            role={msg.role}
            content={msg.content}
            isStreaming={isStreaming && i === chat.messages.length - 1 && msg.role === "assistant"}
            mode={chat.mode}
            onOpenPreview={onOpenPreview}
          />
        ))}
        <div ref={bottomRef} />
      </div>

      <ChatInput
        onSend={onSend}
        disabled={isStreaming}
        placeholder={isPrime ? "Type / to reference a file, then describe changes..." : "What do you want to build today"}
        isPrimeCode={isPrime}
        projectFiles={projectFiles}
        onFileReference={onFileReference}
        onOpenModelManager={onOpenModelManager}
      />
    </div>
  );
}
