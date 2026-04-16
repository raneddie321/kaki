import { useState } from "react";
import { getAllModels, loadFavoriteModels, saveFavoriteModels, type AIModel } from "@/lib/modelStore";
import { Star, Check, ArrowLeft, Zap, Scale, Brain, Sparkles } from "lucide-react";

interface ModelManagerProps {
  onClose: () => void;
}

const CATEGORY_INFO: Record<string, { icon: React.ElementType; label: string; color: string }> = {
  fast: { icon: Zap, label: "Fast", color: "text-green-400" },
  balanced: { icon: Scale, label: "Balanced", color: "text-blue-400" },
  powerful: { icon: Sparkles, label: "Powerful", color: "text-purple-400" },
  reasoning: { icon: Brain, label: "Reasoning", color: "text-orange-400" },
};

export function ModelManager({ onClose }: ModelManagerProps) {
  const [favorites, setFavorites] = useState<string[]>(loadFavoriteModels());
  const allModels = getAllModels();

  const toggleFavorite = (id: string) => {
    let next: string[];
    if (favorites.includes(id)) {
      next = favorites.filter(f => f !== id);
    } else {
      if (favorites.length >= 8) {
        next = [...favorites.slice(1), id];
      } else {
        next = [...favorites, id];
      }
    }
    setFavorites(next);
    saveFavoriteModels(next);
  };

  const groupedByProvider = allModels.reduce<Record<string, AIModel[]>>((acc, m) => {
    (acc[m.provider] = acc[m.provider] || []).push(m);
    return acc;
  }, {});

  return (
    <div className="flex-1 flex flex-col h-full">
      <div className="px-4 py-3 border-b border-border flex items-center gap-3">
        <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <h2 className="text-sm font-semibold text-foreground">Model Library</h2>
          <p className="text-xs text-muted-foreground">Select up to 8 models for quick access</p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* Favorites bar */}
        <div>
          <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
            Quick Access ({favorites.length}/8)
          </h3>
          <div className="flex flex-wrap gap-2">
            {favorites.map(id => {
              const m = allModels.find(x => x.id === id);
              if (!m) return null;
              return (
                <span key={id} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/15 text-primary text-xs font-medium">
                  <Star className="w-3 h-3 fill-current" />
                  {m.label}
                </span>
              );
            })}
          </div>
        </div>

        {/* All models by provider */}
        {Object.entries(groupedByProvider).map(([provider, models]) => (
          <div key={provider}>
            <h3 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">{provider}</h3>
            <div className="space-y-1">
              {models.map(model => {
                const isFav = favorites.includes(model.id);
                const catInfo = CATEGORY_INFO[model.category];
                const CatIcon = catInfo?.icon || Zap;
                return (
                  <button
                    key={model.id}
                    onClick={() => toggleFavorite(model.id)}
                    className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-left transition-colors ${
                      isFav ? "bg-primary/10 border border-primary/30" : "bg-card border border-border hover:border-primary/20"
                    }`}
                  >
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${isFav ? "bg-primary/20" : "bg-secondary"}`}>
                      <CatIcon className={`w-4 h-4 ${catInfo?.color || "text-muted-foreground"}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-foreground">{model.label}</div>
                      <div className="text-xs text-muted-foreground">{model.description}</div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={`text-[10px] px-2 py-0.5 rounded-full ${catInfo?.color || ""} bg-secondary`}>
                        {catInfo?.label}
                      </span>
                      {isFav ? (
                        <Star className="w-4 h-4 text-primary fill-primary" />
                      ) : (
                        <Star className="w-4 h-4 text-muted-foreground" />
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
