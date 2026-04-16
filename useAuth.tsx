import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { supabase } from "@/integrations/supabase/client";
import type { User, Session } from "@supabase/supabase-js";

interface AuthContextType {
  user: User | null;
  session: Session | null;
  loading: boolean;
  credits: number | null;
  plan: string | null;
  username: string | null;
  refreshCredits: () => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  session: null,
  loading: true,
  credits: null,
  plan: null,
  username: null,
  refreshCredits: async () => {},
  signOut: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [credits, setCredits] = useState<number | null>(null);
  const [plan, setPlan] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);

  const refreshCredits = async () => {
    if (!user) return;
    // Call weekly reset check
    const { data: remaining } = await supabase.rpc("check_weekly_credits", { p_user_id: user.id });
    if (typeof remaining === "number") {
      setCredits(remaining);
    }
    // Get plan
    const { data } = await supabase
      .from("user_credits")
      .select("plan")
      .eq("user_id", user.id)
      .single();
    if (data) setPlan(data.plan);
  };

  const fetchProfile = async (userId: string) => {
    const { data } = await supabase
      .from("profiles")
      .select("username")
      .eq("user_id", userId)
      .single();
    if (data) setUsername(data.username);
  };

  useEffect(() => {
    const { data: { subscription } } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      setUser(session?.user ?? null);
      setLoading(false);
    });

    supabase.auth.getSession().then(({ data: { session } }) => {
      setSession(session);
      setUser(session?.user ?? null);
      setLoading(false);
    });

    return () => subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (user) {
      refreshCredits();
      fetchProfile(user.id);
    } else {
      setCredits(null);
      setPlan(null);
      setUsername(null);
    }
  }, [user]);

  const signOut = async () => {
    await supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider value={{ user, session, loading, credits, plan, username, refreshCredits, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
