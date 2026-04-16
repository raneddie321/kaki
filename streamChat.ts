type Msg = { role: "user" | "assistant"; content: string };

const CHAT_URL = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/chat`;
const BYOK_URL = `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/byok-chat`;

const REQUESTY_KEY_STORAGE = "raneddie_requesty_api_key";

function getRequestyApiKey(): string | null {
  try {
    return localStorage.getItem(REQUESTY_KEY_STORAGE) || null;
  } catch {
    return null;
  }
}

export async function streamChat({
  messages,
  mode,
  model,
  thinking,
  innerMode,
  onDelta,
  onDone,
  onError,
}: {
  messages: Msg[];
  mode: "raneddie" | "primecode" | "byok" | "local";
  model?: string;
  thinking?: boolean;
  innerMode?: string;
  onDelta: (text: string) => void;
  onDone: () => void;
  onError: (error: string) => void;
}) {
  // For BYOK, use the byok edge function
  const url = mode === "byok" ? BYOK_URL : CHAT_URL;

  const body: Record<string, unknown> = { messages, mode };
  if (model && model !== "auto") body.model = model;
  if (thinking) body.thinking = true;
  if (innerMode) body.innerMode = innerMode;

  // For BYOK, include provider config
  if (mode === "byok") {
    try {
      const raw = localStorage.getItem("raneddie_byok_config");
      if (raw) {
        const config = JSON.parse(raw);
        body.providerBaseUrl = config.customUrl || 
          (await import("./byokProviders")).BYOK_PROVIDERS.find(p => p.id === config.providerId)?.baseUrl;
        body.apiKey = config.apiKey;
        body.model = config.selectedModel;
      }
    } catch { /* ignore */ }
  } else {
    // Check for Requesty AI key (applies to raneddie/primecode modes)
    const requestyKey = getRequestyApiKey();
    if (requestyKey) {
      body.requestyApiKey = requestyKey;
    }
  }

  const resp = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY}`,
    },
    body: JSON.stringify(body),
  });

  if (!resp.ok) {
    const data = await resp.json().catch(() => ({ error: "Request failed" }));
    onError(data.error || `Error ${resp.status}`);
    return;
  }

  if (!resp.body) {
    onError("No response stream");
    return;
  }

  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let idx: number;
    while ((idx = buffer.indexOf("\n")) !== -1) {
      let line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 1);
      if (line.endsWith("\r")) line = line.slice(0, -1);
      if (!line.startsWith("data: ")) continue;
      const json = line.slice(6).trim();
      if (json === "[DONE]") { onDone(); return; }
      try {
        const parsed = JSON.parse(json);
        const content = parsed.choices?.[0]?.delta?.content;
        if (content) onDelta(content);
      } catch {
        buffer = line + "\n" + buffer;
        break;
      }
    }
  }
  onDone();
}
