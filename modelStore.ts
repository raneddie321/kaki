export interface AIModel {
  id: string;
  label: string;
  provider: string;
  description: string;
  category: "fast" | "balanced" | "powerful" | "reasoning";
}

const BUILT_IN_MODELS: AIModel[] = [
  { id: "auto", label: "Auto", provider: "System", description: "Automatically picks the best model", category: "balanced" },
  { id: "google/gemini-2.5-pro", label: "Gemini 2.5 Pro", provider: "Google", description: "Top-tier reasoning + multimodal", category: "powerful" },
  { id: "google/gemini-3.1-pro-preview", label: "Gemini 3.1 Pro", provider: "Google", description: "Next-gen reasoning model", category: "powerful" },
  { id: "google/gemini-3-flash-preview", label: "Gemini 3 Flash", provider: "Google", description: "Fast & capable next-gen", category: "fast" },
  { id: "google/gemini-2.5-flash", label: "Gemini 2.5 Flash", provider: "Google", description: "Balanced speed & quality", category: "balanced" },
  { id: "google/gemini-2.5-flash-lite", label: "Gemini 2.5 Lite", provider: "Google", description: "Fastest & cheapest", category: "fast" },
  { id: "openai/gpt-5", label: "GPT-5", provider: "OpenAI", description: "Powerful all-rounder", category: "powerful" },
  { id: "openai/gpt-5.2", label: "GPT-5.2", provider: "OpenAI", description: "Latest with enhanced reasoning", category: "reasoning" },
  { id: "openai/gpt-5-mini", label: "GPT-5 Mini", provider: "OpenAI", description: "Cost-effective performance", category: "balanced" },
  { id: "openai/gpt-5-nano", label: "GPT-5 Nano", provider: "OpenAI", description: "Ultra fast & cheap", category: "fast" },
  { id: "google/gemini-2.5-flash-image", label: "Gemini 2.5 Flash Image", provider: "Google", description: "Image generation from text prompts", category: "balanced" },
  { id: "google/gemini-3-pro-image-preview", label: "Gemini 3 Pro Image", provider: "Google", description: "Next-gen image generation", category: "powerful" },
  { id: "google/gemini-3.1-flash-image-preview", label: "Gemini 3.1 Flash Image", provider: "Google", description: "Fast image generation with pro quality", category: "fast" },
];

// Requesty AI models - all available through Requesty AI router
const REQUESTY_MODELS: AIModel[] = [
  { id: "auto", label: "Auto", provider: "Requesty", description: "Automatically picks the best model", category: "balanced" },
  // OpenAI
  { id: "openai/gpt-4o", label: "GPT-4o", provider: "OpenAI", description: "Fast multimodal model", category: "balanced" },
  { id: "openai/gpt-4o-mini", label: "GPT-4o Mini", provider: "OpenAI", description: "Affordable & capable", category: "fast" },
  { id: "openai/gpt-4-turbo", label: "GPT-4 Turbo", provider: "OpenAI", description: "Latest GPT-4 with vision", category: "powerful" },
  { id: "openai/o1", label: "o1", provider: "OpenAI", description: "Advanced reasoning model", category: "reasoning" },
  { id: "openai/o1-mini", label: "o1 Mini", provider: "OpenAI", description: "Fast reasoning model", category: "reasoning" },
  { id: "openai/o3-mini", label: "o3 Mini", provider: "OpenAI", description: "Latest compact reasoning", category: "reasoning" },
  // Anthropic
  { id: "anthropic/claude-sonnet-4", label: "Claude Sonnet 4", provider: "Anthropic", description: "Best balance of speed & intelligence", category: "balanced" },
  { id: "anthropic/claude-opus-4", label: "Claude Opus 4", provider: "Anthropic", description: "Most powerful Claude model", category: "powerful" },
  { id: "anthropic/claude-3.5-sonnet", label: "Claude 3.5 Sonnet", provider: "Anthropic", description: "Fast & highly capable", category: "balanced" },
  { id: "anthropic/claude-3.5-haiku", label: "Claude 3.5 Haiku", provider: "Anthropic", description: "Fastest Claude model", category: "fast" },
  // Google
  { id: "google/gemini-2.5-pro", label: "Gemini 2.5 Pro", provider: "Google", description: "Top-tier reasoning + multimodal", category: "powerful" },
  { id: "google/gemini-2.5-flash", label: "Gemini 2.5 Flash", provider: "Google", description: "Fast & balanced", category: "balanced" },
  { id: "google/gemini-2.0-flash", label: "Gemini 2.0 Flash", provider: "Google", description: "Speed optimized", category: "fast" },
  // Meta
  { id: "meta-llama/llama-4-maverick", label: "Llama 4 Maverick", provider: "Meta", description: "Latest open-source model", category: "powerful" },
  { id: "meta-llama/llama-3.3-70b", label: "Llama 3.3 70B", provider: "Meta", description: "Strong open-source model", category: "balanced" },
  // DeepSeek
  { id: "deepseek/deepseek-r1", label: "DeepSeek R1", provider: "DeepSeek", description: "Advanced reasoning model", category: "reasoning" },
  { id: "deepseek/deepseek-v3", label: "DeepSeek V3", provider: "DeepSeek", description: "General purpose model", category: "balanced" },
  // Mistral
  { id: "mistralai/mistral-large", label: "Mistral Large", provider: "Mistral", description: "Most capable Mistral model", category: "powerful" },
  { id: "mistralai/codestral", label: "Codestral", provider: "Mistral", description: "Optimized for code", category: "balanced" },
  // Qwen
  { id: "qwen/qwen3-235b", label: "Qwen 3 235B", provider: "Qwen", description: "Large open-source model", category: "powerful" },
  { id: "qwen/qwen3-32b", label: "Qwen 3 32B", provider: "Qwen", description: "Efficient open-source", category: "balanced" },
];

function isRequestyEnabled(): boolean {
  try {
    return !!localStorage.getItem("raneddie_requesty_api_key");
  } catch {
    return false;
  }
}

function loadAdminCustomModels(): AIModel[] {
  try {
    const raw = localStorage.getItem("raneddie_admin_custom_models");
    if (!raw) return [];
    const models = JSON.parse(raw) as Array<{ modelId: string; label: string; provider: string; category: string; enabled?: boolean }>;
    return models
      .filter(m => m.enabled !== false)
      .map(m => ({
        id: m.modelId,
        label: m.label,
        provider: m.provider,
        description: `Custom ${m.provider} model`,
        category: (m.category as AIModel["category"]) || "balanced",
      }));
  } catch {
    return [];
  }
}

/** Always call this function instead of using a cached constant - ensures admin models are always fresh */
export function getAllModels(): AIModel[] {
  const custom = loadAdminCustomModels();
  const customIds = new Set(custom.map(m => m.id));
  
  // Use Requesty models if API key is configured, otherwise use built-in
  const baseModels = isRequestyEnabled() ? REQUESTY_MODELS : BUILT_IN_MODELS;
  
  return [...baseModels.filter(m => !customIds.has(m.id)), ...custom];
}

const FAVORITES_KEY = "raneddie_favorite_models";
const MAX_FAVORITES = 8;

export function loadFavoriteModels(): string[] {
  try {
    const raw = localStorage.getItem(FAVORITES_KEY);
    const ids = raw ? JSON.parse(raw) : ["auto", "google/gemini-3-flash-preview", "openai/gpt-5", "google/gemini-2.5-flash", "openai/gpt-5-mini"];
    return ids.slice(0, MAX_FAVORITES);
  } catch {
    return ["auto", "google/gemini-3-flash-preview", "openai/gpt-5", "google/gemini-2.5-flash", "openai/gpt-5-mini"];
  }
}

export function saveFavoriteModels(ids: string[]) {
  localStorage.setItem(FAVORITES_KEY, JSON.stringify(ids.slice(0, MAX_FAVORITES)));
}
