import { ChevronRight } from "lucide-react";

interface BreadcrumbsProps {
  path?: string;
}

export function Breadcrumbs({ path }: BreadcrumbsProps) {
  if (!path) return null;
  const parts = path.split("/");

  return (
    <div className="flex items-center gap-0.5 px-3 py-1 bg-[hsl(220,15%,11%)] border-b border-border text-[11px] text-muted-foreground overflow-x-auto scrollbar-none">
      {parts.map((part, i) => (
        <span key={i} className="flex items-center gap-0.5 whitespace-nowrap">
          {i > 0 && <ChevronRight className="w-3 h-3 text-muted-foreground/50" />}
          <span className={`hover:text-foreground cursor-pointer transition-colors ${i === parts.length - 1 ? "text-foreground" : ""}`}>
            {part}
          </span>
        </span>
      ))}
    </div>
  );
}
