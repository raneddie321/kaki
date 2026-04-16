export type FileOperationType = "create" | "modify" | "delete";

export interface ParsedFileOperation {
  type: FileOperationType;
  path: string;
  content?: string;
  index: number;
  end: number;
  source: "marker" | "filename-code";
}

export function normalizeFilePath(path: string): string {
  return path
    .trim()
    .replace(/^`+|`+$/g, "")
    .replace(/\\/g, "/")
    .replace(/^\.\//, "")
    .replace(/^\/+/, "");
}

function stripOuterCodeFence(content: string): string {
  const trimmed = content.trim();
  const wrappedFence = trimmed.match(/^```[\w+-]*\n([\s\S]*?)\n```$/);
  return (wrappedFence ? wrappedFence[1] : content).replace(/\s+$/, "");
}

function parseFilenameComment(line: string): string | null {
  const patterns = [
    /^\/\/\s*(?:filename|file|path):\s*(.+)$/i,
    /^#\s*(?:filename|file|path):\s*(.+)$/i,
    /^<!--\s*(?:filename|file|path):\s*(.+?)\s*-->$/i,
    /^\/\*\s*(?:filename|file|path):\s*(.+?)\s*\*\/$/i,
  ];

  for (const pattern of patterns) {
    const match = line.match(pattern);
    if (match) return normalizeFilePath(match[1]);
  }

  return null;
}

const FILE_EXT_RE = /\.(js|jsx|ts|tsx|html|css|json|md|txt|py|rb|java|c|cpp|h|go|rs|sh|bash|yaml|yml|toml|xml|svg|sql|vue|svelte|php|swift|kt|dart|lua|r|pl|ex|exs|zig|nim|scss|sass|less|graphql|gql|proto|dockerfile|makefile|csv|env|gitignore|conf|ini|cfg|properties)$/i;

function extractFilenameFromContext(content: string, precedingText: string): string | null {
  // Check inside code block for first-line filename comment
  const fromComment = parseFilenameComment((content.split("\n").find(l => l.trim()) || "").trim());
  if (fromComment) return fromComment;

  // Look at text just before the code block for a filename
  const lines = precedingText.trimEnd().split("\n");
  for (let i = lines.length - 1; i >= Math.max(0, lines.length - 3); i--) {
    const line = lines[i].trim();
    if (!line) continue;

    // Match patterns like: `src/App.tsx`, **src/App.tsx**, src/App.tsx:, "src/App.tsx"
    const filenamePatterns = [
      /^[`*]*([a-zA-Z0-9_/.\\-]+\.[a-z]{1,10})[`*:]*$/i,
      /[`*]+([a-zA-Z0-9_/.\\-]+\.[a-z]{1,10})[`*:]+/i,
      /(?:create|modify|file|update|edit|write|save)\s+[`*"']*([a-zA-Z0-9_/.\\-]+\.[a-z]{1,10})[`*"':]*$/i,
    ];
    for (const pat of filenamePatterns) {
      const m = line.match(pat);
      if (m && FILE_EXT_RE.test(m[1])) return normalizeFilePath(m[1]);
    }
    break; // only check the closest non-empty line
  }

  return null;
}

function extractFilenameCodeBlock(content: string): { path: string; content: string } | null {
  const lines = content.replace(/\r\n?/g, "\n").split("\n");
  const filenameLineIndex = lines.findIndex((line) => line.trim().length > 0);

  if (filenameLineIndex === -1) return null;

  const path = parseFilenameComment(lines[filenameLineIndex].trim());
  if (!path) return null;

  const nextLines = [...lines];
  nextLines.splice(filenameLineIndex, 1);

  return {
    path,
    content: stripOuterCodeFence(nextLines.join("\n")).replace(/^\n+/, ""),
  };
}

function overlaps(start: number, end: number, rangeStart: number, rangeEnd: number) {
  return start < rangeEnd && rangeStart < end;
}

function applyOperationToPathSet(paths: Set<string>, operation: ParsedFileOperation) {
  if (operation.type === "delete") paths.delete(operation.path);
  else paths.add(operation.path);
}

export function extractFileOperations(content: string, existingPaths: Iterable<string> = []): ParsedFileOperation[] {
  const markerOperations: ParsedFileOperation[] = [];
  const inferredOperations: ParsedFileOperation[] = [];
  const knownPaths = new Set(Array.from(existingPaths, normalizeFilePath));

  const createRegex = /===CREATE_FILE:\s*(.+?)===\n([\s\S]*?)===END_FILE===/g;
  const modifyRegex = /===MODIFY_FILE:\s*(.+?)===\n([\s\S]*?)===END_FILE===/g;
  const deleteRegex = /===DELETE_FILE:\s*(.+?)===/g;
  const codeRegex = /```(?:[\w+-]+)?\n([\s\S]*?)```/g;

  let match: RegExpExecArray | null;

  while ((match = createRegex.exec(content)) !== null) {
    markerOperations.push({
      type: "create",
      path: normalizeFilePath(match[1]),
      content: stripOuterCodeFence(match[2]),
      index: match.index,
      end: match.index + match[0].length,
      source: "marker",
    });
  }

  while ((match = modifyRegex.exec(content)) !== null) {
    markerOperations.push({
      type: "modify",
      path: normalizeFilePath(match[1]),
      content: stripOuterCodeFence(match[2]),
      index: match.index,
      end: match.index + match[0].length,
      source: "marker",
    });
  }

  while ((match = deleteRegex.exec(content)) !== null) {
    markerOperations.push({
      type: "delete",
      path: normalizeFilePath(match[1]),
      index: match.index,
      end: match.index + match[0].length,
      source: "marker",
    });
  }

  markerOperations.sort((a, b) => a.index - b.index);

  let markerPointer = 0;

  while ((match = codeRegex.exec(content)) !== null) {
    while (markerPointer < markerOperations.length && markerOperations[markerPointer].index < match.index) {
      applyOperationToPathSet(knownPaths, markerOperations[markerPointer]);
      markerPointer += 1;
    }

    const start = match.index;
    const end = match.index + match[0].length;

    if (markerOperations.some((operation) => overlaps(start, end, operation.index, operation.end))) {
      continue;
    }

    const precedingText = content.slice(0, start);
    const codeContent = match[1];

    const filename = extractFilenameFromContext(codeContent, precedingText);
    if (!filename) continue;

    // Strip the filename comment from code content if it was inside
    const innerLines = codeContent.replace(/\r\n?/g, "\n").split("\n");
    const firstNonEmpty = innerLines.findIndex(l => l.trim().length > 0);
    let cleanContent: string;
    if (firstNonEmpty >= 0 && parseFilenameComment(innerLines[firstNonEmpty].trim())) {
      innerLines.splice(firstNonEmpty, 1);
      cleanContent = stripOuterCodeFence(innerLines.join("\n")).replace(/^\n+/, "");
    } else {
      cleanContent = stripOuterCodeFence(codeContent).replace(/^\n+/, "");
    }

    const path = normalizeFilePath(filename);
    const type: FileOperationType = knownPaths.has(path) ? "modify" : "create";
    knownPaths.add(path);

    inferredOperations.push({
      type,
      path,
      content: cleanContent,
      index: start,
      end,
      source: "filename-code",
    });
  }

  return [...markerOperations, ...inferredOperations].sort((a, b) => a.index - b.index);
}