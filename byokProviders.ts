export interface BYOKProvider {
  id: string;
  name: string;
  baseUrl: string;
  description: string;
  models: { id: string; label: string }[];
  keyPlaceholder: string;
}

export const BYOK_PROVIDERS: BYOKProvider[] = [
  {
    id: "openrouter",
    name: "OpenRouter",
    baseUrl: "https://openrouter.ai/api/v1",
    description: "Access 200+ models from one API",
    keyPlaceholder: "sk-or-...",
    models: [
      { id: "anthropic/claude-sonnet-4", label: "Claude Sonnet 4" },
      { id: "openai/gpt-4o", label: "GPT-4o" },
      { id: "google/gemini-2.5-pro", label: "Gemini 2.5 Pro" },
      { id: "meta-llama/llama-4-maverick", label: "Llama 4 Maverick" },
      { id: "deepseek/deepseek-r1", label: "DeepSeek R1" },
    ],
  },
  {
    id: "siliconflow",
    name: "SiliconFlow",
    baseUrl: "https://api.siliconflow.cn/v1",
    description: "High-performance AI inference",
    keyPlaceholder: "sf-...",
    models: [
      { id: "deepseek-ai/DeepSeek-V3", label: "DeepSeek V3" },
      { id: "Qwen/Qwen3-235B-A22B", label: "Qwen 3 235B" },
      { id: "deepseek-ai/DeepSeek-R1", label: "DeepSeek R1" },
    ],
  },
  {
    id: "groq",
    name: "Groq",
    baseUrl: "https://api.groq.com/openai/v1",
    description: "Ultra-fast LPU inference",
    keyPlaceholder: "gsk_...",
    models: [
      { id: "llama-3.3-70b-versatile", label: "Llama 3.3 70B" },
      { id: "gemma2-9b-it", label: "Gemma 2 9B" },
      { id: "mixtral-8x7b-32768", label: "Mixtral 8x7B" },
    ],
  },
  {
    id: "google",
    name: "Google AI Studio",
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai",
    description: "Google's Gemini models directly",
    keyPlaceholder: "AIza...",
    models: [
      { id: "gemini-2.5-pro", label: "Gemini 2.5 Pro" },
      { id: "gemini-2.5-flash", label: "Gemini 2.5 Flash" },
      { id: "gemini-2.0-flash", label: "Gemini 2.0 Flash" },
    ],
  },
  {
    id: "together",
    name: "Together AI",
    baseUrl: "https://api.together.xyz/v1",
    description: "Open-source model hosting",
    keyPlaceholder: "tog-...",
    models: [
      { id: "meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8", label: "Llama 4 Maverick" },
      { id: "Qwen/Qwen3-235B-A22B", label: "Qwen 3 235B" },
      { id: "deepseek-ai/DeepSeek-R1", label: "DeepSeek R1" },
    ],
  },
  {
    id: "fireworks",
    name: "Fireworks AI",
    baseUrl: "https://api.fireworks.ai/inference/v1",
    description: "Fast & reliable model serving",
    keyPlaceholder: "fw_...",
    models: [
      { id: "accounts/fireworks/models/llama4-maverick-instruct-basic", label: "Llama 4 Maverick" },
      { id: "accounts/fireworks/models/deepseek-r1", label: "DeepSeek R1" },
    ],
  },
  {
    id: "mistral",
    name: "Mistral AI",
    baseUrl: "https://api.mistral.ai/v1",
    description: "European AI lab models",
    keyPlaceholder: "mist-...",
    models: [
      { id: "mistral-large-latest", label: "Mistral Large" },
      { id: "mistral-medium-latest", label: "Mistral Medium" },
      { id: "codestral-latest", label: "Codestral" },
    ],
  },
  {
    id: "perplexity",
    name: "Perplexity",
    baseUrl: "https://api.perplexity.ai",
    description: "AI search + reasoning",
    keyPlaceholder: "pplx-...",
    models: [
      { id: "sonar-pro", label: "Sonar Pro" },
      { id: "sonar", label: "Sonar" },
    ],
  },
  {
    id: "custom",
    name: "Custom Endpoint",
    baseUrl: "",
    description: "Any OpenAI-compatible API",
    keyPlaceholder: "your-api-key",
    models: [],
  },
];

const BYOK_STORAGE_KEY = "raneddie_byok_config";

export interface BYOKConfig {
  providerId: string;
  apiKey: string;
  customUrl?: string;
  selectedModel: string;
}

export function loadBYOKConfig(): BYOKConfig | null {
  try {
    const raw = localStorage.getItem(BYOK_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveBYOKConfig(config: BYOKConfig) {
  localStorage.setItem(BYOK_STORAGE_KEY, JSON.stringify(config));
}
