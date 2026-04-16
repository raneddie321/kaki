import { Play, Pause, SkipForward, StopCircle, RotateCcw, ChevronRight, Bug } from "lucide-react";
import { useState } from "react";

export function DebugPanel() {
  const [running, setRunning] = useState(false);
  const [breakpoints] = useState<string[]>([]);

  return (
    <div className="flex flex-col h-full">
      <div className="p-3 border-b border-border">
        <div className="flex items-center gap-2 mb-3">
          <Bug className="w-4 h-4 text-muted-foreground" />
          <span className="text-xs font-medium text-foreground">Run and Debug</span>
        </div>
        <div className="flex items-center gap-1 mb-3">
          <button onClick={() => setRunning(!running)}
            className="p-1.5 rounded bg-green-600 hover:bg-green-700 text-white transition-colors" title="Start Debugging">
            {running ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
          </button>
          <button className="p-1.5 rounded hover:bg-secondary text-muted-foreground" title="Step Over" disabled={!running}>
            <SkipForward className="w-3.5 h-3.5" />
          </button>
          <button className="p-1.5 rounded hover:bg-secondary text-muted-foreground" title="Restart" disabled={!running}>
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
          <button onClick={() => setRunning(false)} className="p-1.5 rounded hover:bg-secondary text-muted-foreground" title="Stop" disabled={!running}>
            <StopCircle className="w-3.5 h-3.5" />
          </button>
        </div>
        <select className="w-full bg-secondary border border-border rounded px-2 py-1.5 text-xs text-foreground outline-none">
          <option>Launch Program</option>
          <option>Attach to Process</option>
          <option>Launch Chrome</option>
        </select>
      </div>

      <div className="flex-1 overflow-y-auto">
        <Section title="VARIABLES">
          <VarItem name="count" value="42" type="number" />
          <VarItem name="name" value='"PrimeCODE"' type="string" />
          <VarItem name="isActive" value="true" type="boolean" />
          <VarItem name="items" value="Array(3)" type="object" />
        </Section>
        <Section title="WATCH">
          <p className="text-[10px] text-muted-foreground px-3 py-1">Add expressions to watch...</p>
        </Section>
        <Section title="CALL STACK">
          {running ? (
            <>
              <StackItem fn="handleClick" file="App.tsx" line={42} active />
              <StackItem fn="processEvent" file="events.ts" line={18} />
              <StackItem fn="main" file="index.ts" line={5} />
            </>
          ) : (
            <p className="text-[10px] text-muted-foreground px-3 py-1">Not running</p>
          )}
        </Section>
        <Section title="BREAKPOINTS">
          {breakpoints.length === 0 ? (
            <p className="text-[10px] text-muted-foreground px-3 py-1">No breakpoints set</p>
          ) : null}
        </Section>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(true);
  return (
    <div className="border-b border-border">
      <button onClick={() => setOpen(!open)} className="w-full flex items-center gap-1 px-3 py-1.5 text-[10px] font-bold text-muted-foreground uppercase tracking-wider hover:bg-secondary/30">
        <ChevronRight className={`w-3 h-3 transition-transform ${open ? "rotate-90" : ""}`} />
        {title}
      </button>
      {open && <div className="pb-1">{children}</div>}
    </div>
  );
}

function VarItem({ name, value, type }: { name: string; value: string; type: string }) {
  const colors = { number: "text-green-400", string: "text-yellow-400", boolean: "text-blue-400", object: "text-purple-400" };
  return (
    <div className="flex items-center gap-2 px-6 py-0.5 text-[11px] hover:bg-secondary/30">
      <span className="text-foreground">{name}</span>
      <span className="text-muted-foreground">=</span>
      <span className={colors[type as keyof typeof colors] || "text-foreground"}>{value}</span>
    </div>
  );
}

function StackItem({ fn, file, line, active }: { fn: string; file: string; line: number; active?: boolean }) {
  return (
    <div className={`flex items-center gap-2 px-6 py-0.5 text-[11px] ${active ? "bg-yellow-500/10 text-yellow-300" : "text-muted-foreground"}`}>
      <span className="text-foreground">{fn}</span>
      <span className="text-muted-foreground/60">{file}:{line}</span>
    </div>
  );
}
