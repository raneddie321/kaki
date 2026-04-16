interface FileIconProps {
  filename: string;
  className?: string;
}

const ICON_MAP: Record<string, { label: string; color: string }> = {
  js: { label: "JS", color: "#f7df1e" },
  jsx: { label: "JSX", color: "#61dafb" },
  ts: { label: "TS", color: "#3178c6" },
  tsx: { label: "TSX", color: "#3178c6" },
  html: { label: "HTML", color: "#e34c26" },
  css: { label: "CSS", color: "#264de4" },
  scss: { label: "SCSS", color: "#cd6799" },
  json: { label: "{ }", color: "#a8b686" },
  md: { label: "MD", color: "#83b4c4" },
  py: { label: "PY", color: "#3776ab" },
  rb: { label: "RB", color: "#cc342d" },
  go: { label: "GO", color: "#00add8" },
  rs: { label: "RS", color: "#dea584" },
  java: { label: "JAVA", color: "#f89820" },
  sql: { label: "SQL", color: "#e38c00" },
  yml: { label: "YML", color: "#cb171e" },
  yaml: { label: "YML", color: "#cb171e" },
  sh: { label: "SH", color: "#89e051" },
  svg: { label: "SVG", color: "#ffb13b" },
  xml: { label: "XML", color: "#f68220" },
  toml: { label: "TML", color: "#9c4221" },
  env: { label: "ENV", color: "#ecd53f" },
  txt: { label: "TXT", color: "#888888" },
  csv: { label: "CSV", color: "#2ea44f" },
  vue: { label: "VUE", color: "#42b883" },
  svelte: { label: "SVT", color: "#ff3e00" },
  php: { label: "PHP", color: "#777bb4" },
  c: { label: "C", color: "#555555" },
  cpp: { label: "C++", color: "#00599c" },
  h: { label: "H", color: "#a074c4" },
  dockerfile: { label: "🐳", color: "#2496ed" },
  gitignore: { label: "GIT", color: "#f05032" },
  lock: { label: "🔒", color: "#888888" },
  png: { label: "IMG", color: "#a855f7" },
  jpg: { label: "IMG", color: "#a855f7" },
  jpeg: { label: "IMG", color: "#a855f7" },
  gif: { label: "GIF", color: "#a855f7" },
  webp: { label: "IMG", color: "#a855f7" },
  mp4: { label: "VID", color: "#ef4444" },
  mp3: { label: "AUD", color: "#22c55e" },
};

export function FileIcon({ filename, className = "w-4 h-4" }: FileIconProps) {
  const name = filename.toLowerCase();
  const ext = name.includes(".") ? name.split(".").pop() || "" : "";
  
  // Check for special filenames
  let entry = ICON_MAP[ext];
  if (name === "dockerfile") entry = ICON_MAP.dockerfile;
  if (name === ".gitignore") entry = ICON_MAP.gitignore;
  if (name.endsWith(".lock")) entry = ICON_MAP.lock;

  if (!entry) {
    return (
      <div className={`${className} rounded flex items-center justify-center text-[7px] font-bold bg-muted text-muted-foreground flex-shrink-0`}>
        F
      </div>
    );
  }

  return (
    <div
      className={`${className} rounded flex items-center justify-center text-[7px] font-bold flex-shrink-0`}
      style={{ backgroundColor: entry.color + "22", color: entry.color }}
    >
      {entry.label}
    </div>
  );
}
