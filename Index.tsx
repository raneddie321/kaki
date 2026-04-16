import { useState } from "react";
import ProjectsHome from "./ProjectsHome";
import Workspace from "./Workspace";
import type { Project } from "@/lib/projectStore";

export default function Index() {
  const [activeProject, setActiveProject] = useState<Project | null>(null);

  if (activeProject) {
    return <Workspace project={activeProject} onBack={() => setActiveProject(null)} />;
  }

  return <ProjectsHome onOpenProject={setActiveProject} />;
}
