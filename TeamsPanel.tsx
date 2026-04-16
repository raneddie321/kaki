import { useState, useEffect, useRef } from "react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/hooks/useAuth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Users, UserPlus, Crown, X, Send, MessageSquare, Loader2, Pencil, Check, Mail } from "lucide-react";
import { toast } from "sonner";

interface TeamMember {
  id: string;
  user_id: string;
  role: string;
  username?: string;
  display_name?: string;
}

interface TeamChatMessage {
  id: string;
  user_id: string | null;
  role: string;
  content: string;
  created_at: string;
  sender_name?: string;
}

const PLAN_LIMITS: Record<string, number> = {
  free: 5,
  plus: 10,
  pro: 20,
  enterprise: 999,
};

export function TeamsPanel({ projectId }: { projectId: string }) {
  const { user, plan } = useAuth();
  const [team, setTeam] = useState<any>(null);
  const [members, setMembers] = useState<TeamMember[]>([]);
  const [messages, setMessages] = useState<TeamChatMessage[]>([]);
  const [inviteEmail, setInviteEmail] = useState("");
  const [chatInput, setChatInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [sending, setSending] = useState(false);
  const [tab, setTab] = useState<"members" | "chat">("members");
  const [teamName, setTeamName] = useState("");
  const [editingName, setEditingName] = useState(false);
  const [editNameValue, setEditNameValue] = useState("");
  const chatEndRef = useRef<HTMLDivElement>(null);

  const maxMembers = PLAN_LIMITS[plan || "free"] || 5;

  useEffect(() => {
    if (!user) return;
    loadTeam();
  }, [user, projectId]);

  useEffect(() => {
    if (!team) return;
    const channel = supabase
      .channel(`team-chat-${team.id}`)
      .on("postgres_changes", {
        event: "INSERT",
        schema: "public",
        table: "team_chat_messages",
        filter: `team_id=eq.${team.id}`,
      }, async (payload) => {
        const msg = payload.new as any;
        const { data: profile } = await supabase
          .from("profiles")
          .select("username, display_name")
          .eq("user_id", msg.user_id)
          .single();
        setMessages(prev => [...prev, {
          ...msg,
          sender_name: profile?.username || profile?.display_name || "Unknown",
        }]);
      })
      .subscribe();

    return () => { supabase.removeChannel(channel); };
  }, [team]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const loadTeam = async () => {
    setLoading(true);
    
    // Check teams where user is owner OR member
    const { data: ownedTeams } = await supabase
      .from("teams")
      .select("*")
      .eq("owner_id", user!.id);

    const { data: memberships } = await supabase
      .from("team_members")
      .select("team_id")
      .eq("user_id", user!.id);

    // Combine: owned teams + teams from memberships
    let allTeams: any[] = ownedTeams || [];
    
    if (memberships && memberships.length > 0) {
      const memberTeamIds = memberships.map(m => m.team_id);
      const existingIds = new Set(allTeams.map(t => t.id));
      const missingIds = memberTeamIds.filter(id => !existingIds.has(id));
      
      if (missingIds.length > 0) {
        const { data: memberTeams } = await supabase
          .from("teams")
          .select("*")
          .in("id", missingIds);
        if (memberTeams) allTeams = [...allTeams, ...memberTeams];
      }
    }

    if (allTeams.length > 0) {
      const t = allTeams[0];
      setTeam(t);
      setTeamName(t.name);
      await loadMembers(t.id);
      await loadMessages(t.id);
    }
    setLoading(false);
  };

  const loadMembers = async (teamId: string) => {
    const { data } = await supabase
      .from("team_members")
      .select("id, user_id, role")
      .eq("team_id", teamId);

    if (data) {
      const enriched = await Promise.all(data.map(async (m) => {
        const { data: profile } = await supabase
          .from("profiles")
          .select("username, display_name")
          .eq("user_id", m.user_id)
          .single();
        return { ...m, username: profile?.username, display_name: profile?.display_name };
      }));
      setMembers(enriched);
    }
  };

  const loadMessages = async (teamId: string) => {
    const { data } = await supabase
      .from("team_chat_messages")
      .select("*")
      .eq("team_id", teamId)
      .order("created_at", { ascending: true });

    if (data) {
      const enriched = await Promise.all(data.map(async (msg) => {
        if (!msg.user_id) return { ...msg, sender_name: "AI" };
        const { data: profile } = await supabase
          .from("profiles")
          .select("username, display_name")
          .eq("user_id", msg.user_id)
          .single();
        return { ...msg, sender_name: profile?.username || profile?.display_name || "Unknown" };
      }));
      setMessages(enriched);
    }
  };

  const handleCreateTeam = async () => {
    if (!user) return;
    const name = teamName.trim() || "My Team";
    setCreating(true);
    const { data: newTeam, error } = await supabase
      .from("teams")
      .insert({ name, owner_id: user.id, plan: plan || "free", max_members: maxMembers })
      .select()
      .single();

    if (error || !newTeam) {
      toast.error("Failed to create team");
      setCreating(false);
      return;
    }

    await supabase.from("team_members").insert({ team_id: newTeam.id, user_id: user.id, role: "owner" });
    setTeam(newTeam);
    setTeamName(newTeam.name);
    await loadMembers(newTeam.id);
    setCreating(false);
    toast.success("Team created!");
  };

  const handleRenameTeam = async () => {
    if (!editNameValue.trim() || !team) return;
    const { error } = await supabase.from("teams").update({ name: editNameValue.trim() }).eq("id", team.id);
    if (error) {
      toast.error("Failed to rename team");
      return;
    }
    setTeam({ ...team, name: editNameValue.trim() });
    setTeamName(editNameValue.trim());
    setEditingName(false);
    toast.success("Team renamed!");
  };

  const handleInvite = async () => {
    if (!inviteEmail.trim() || !team) return;
    if (members.length >= maxMembers) {
      toast.error(`Team limit reached (${maxMembers} on ${plan || "free"} plan)`);
      return;
    }

    // Find user by email using secure RPC
    const { data: foundUserId, error: lookupError } = await supabase.rpc("find_user_by_email", {
      p_email: inviteEmail.trim().toLowerCase(),
    });

    if (lookupError || !foundUserId) {
      toast.error("No account found with that email.");
      return;
    }

    if (members.some(m => m.user_id === foundUserId)) {
      toast.error("Already a team member.");
      return;
    }

    const { error } = await supabase
      .from("team_members")
      .insert({ team_id: team.id, user_id: foundUserId, role: "member" });

    if (error) {
      toast.error("Failed to add member");
      return;
    }

    await loadMembers(team.id);
    setInviteEmail("");
    toast.success(`Added ${inviteEmail.trim()} to the team!`);
  };

  const handleRemoveMember = async (memberId: string) => {
    await supabase.from("team_members").delete().eq("id", memberId);
    await loadMembers(team.id);
    toast.success("Member removed");
  };

  const handleSendChat = async () => {
    if (!chatInput.trim() || !team || !user) return;
    setSending(true);
    await supabase.from("team_chat_messages").insert({
      team_id: team.id,
      user_id: user.id,
      role: "user",
      content: chatInput.trim(),
    });
    setChatInput("");
    setSending(false);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!team) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 p-4">
        <Users className="w-10 h-10 text-muted-foreground" />
        <h3 className="text-base font-semibold text-foreground">Teams+</h3>
        <p className="text-xs text-muted-foreground text-center max-w-[220px]">
          Invite teammates, share code, and chat together in real-time.
        </p>
        <Input
          placeholder="Team name..."
          value={teamName}
          onChange={(e) => setTeamName(e.target.value)}
          className="h-8 text-xs bg-secondary max-w-[200px] text-center"
          maxLength={32}
        />
        <p className="text-[10px] text-muted-foreground">
          {plan || "free"} plan: up to {maxMembers} members
        </p>
        <Button size="sm" onClick={handleCreateTeam} disabled={creating}>
          {creating ? <Loader2 className="w-3.5 h-3.5 animate-spin mr-1" /> : <Users className="w-3.5 h-3.5 mr-1" />}
          Create Team
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full">
      {/* Team name header */}
      <div className="px-3 py-2 border-b border-border flex items-center gap-2">
        {editingName ? (
          <>
            <Input
              value={editNameValue}
              onChange={(e) => setEditNameValue(e.target.value)}
              className="h-6 text-xs bg-secondary flex-1"
              maxLength={32}
              autoFocus
              onKeyDown={(e) => e.key === "Enter" && handleRenameTeam()}
            />
            <button onClick={handleRenameTeam} className="text-primary hover:text-primary/80">
              <Check className="w-3.5 h-3.5" />
            </button>
            <button onClick={() => setEditingName(false)} className="text-muted-foreground hover:text-foreground">
              <X className="w-3.5 h-3.5" />
            </button>
          </>
        ) : (
          <>
            <Users className="w-3.5 h-3.5 text-primary flex-shrink-0" />
            <span className="text-xs font-semibold text-foreground truncate flex-1">{teamName}</span>
            {team.owner_id === user?.id && (
              <button onClick={() => { setEditNameValue(teamName); setEditingName(true); }} className="text-muted-foreground hover:text-foreground">
                <Pencil className="w-3 h-3" />
              </button>
            )}
          </>
        )}
      </div>

      {/* Tab bar */}
      <div className="flex border-b border-border">
        <button
          onClick={() => setTab("members")}
          className={`flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
            tab === "members" ? "text-primary border-b-2 border-primary" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <Users className="w-3.5 h-3.5" /> Members ({members.length}/{maxMembers})
        </button>
        <button
          onClick={() => setTab("chat")}
          className={`flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1.5 transition-colors ${
            tab === "chat" ? "text-primary border-b-2 border-primary" : "text-muted-foreground hover:text-foreground"
          }`}
        >
          <MessageSquare className="w-3.5 h-3.5" /> Team Chat
        </button>
      </div>

      {tab === "members" ? (
        <div className="flex-1 overflow-y-auto p-3 space-y-3">
          <div className="flex gap-1.5">
            <Input
              placeholder="Invite by email..."
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              type="email"
              className="h-8 text-xs bg-secondary"
              onKeyDown={(e) => e.key === "Enter" && handleInvite()}
            />
            <Button size="sm" variant="outline" className="h-8 px-2" onClick={handleInvite}>
              <UserPlus className="w-3.5 h-3.5" />
            </Button>
          </div>

          <div className="space-y-1">
            {members.map((m) => (
              <div key={m.id} className="flex items-center justify-between px-2 py-1.5 rounded-lg bg-secondary/50 hover:bg-secondary transition-colors">
                <div className="flex items-center gap-2">
                  {m.role === "owner" && <Crown className="w-3 h-3 text-yellow-500" />}
                  <span className="text-xs font-medium text-foreground">
                    @{m.username || m.display_name || "user"}
                  </span>
                  {m.user_id === user?.id && (
                    <span className="text-[9px] bg-primary/20 text-primary px-1.5 py-0.5 rounded-full">you</span>
                  )}
                </div>
                {team.owner_id === user?.id && m.user_id !== user?.id && (
                  <button onClick={() => handleRemoveMember(m.id)} className="text-muted-foreground hover:text-destructive">
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="flex-1 flex flex-col min-h-0">
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {messages.length === 0 && (
              <p className="text-xs text-muted-foreground text-center py-8">No messages yet. Start the conversation!</p>
            )}
            {messages.map((msg) => (
              <div key={msg.id} className={`flex flex-col ${msg.user_id === user?.id ? "items-end" : "items-start"}`}>
                <span className="text-[9px] text-muted-foreground mb-0.5">
                  {msg.role === "assistant" ? "PrimeCODE" : msg.sender_name || "Unknown"}
                </span>
                <div className={`max-w-[85%] rounded-lg px-3 py-1.5 text-xs ${
                  msg.user_id === user?.id
                    ? "bg-primary text-primary-foreground"
                    : msg.role === "assistant"
                    ? "bg-accent text-accent-foreground"
                    : "bg-secondary text-foreground"
                }`}>
                  {msg.content}
                </div>
              </div>
            ))}
            <div ref={chatEndRef} />
          </div>

          <div className="p-2 border-t border-border flex gap-1.5">
            <Input
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              placeholder="Message your team..."
              className="h-8 text-xs bg-secondary"
              onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && handleSendChat()}
            />
            <Button size="sm" className="h-8 px-2" onClick={handleSendChat} disabled={sending || !chatInput.trim()}>
              <Send className="w-3.5 h-3.5" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
