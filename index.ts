import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-admin-password",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, serviceRoleKey);

  try {
    if (req.method === "GET") {
      // List all users with their credits and profiles
      const { data: credits, error: creditsErr } = await supabase
        .from("user_credits")
        .select("*");
      if (creditsErr) throw creditsErr;

      const { data: profiles, error: profErr } = await supabase
        .from("profiles")
        .select("user_id, username, display_name");
      if (profErr) throw profErr;

      // Get emails from auth.users via admin API
      const { data: authData, error: authErr } = await supabase.auth.admin.listUsers({ perPage: 1000 });
      if (authErr) throw authErr;

      const users = (credits || []).map((c: any) => {
        const profile = (profiles || []).find((p: any) => p.user_id === c.user_id);
        const authUser = (authData?.users || []).find((u: any) => u.id === c.user_id);
        return {
          user_id: c.user_id,
          email: authUser?.email || "unknown",
          username: profile?.username || null,
          display_name: profile?.display_name || null,
          credits_remaining: c.credits_remaining,
          credits_total: c.credits_total,
          plan: c.plan,
          week_start: c.week_start,
          created_at: c.created_at,
        };
      });

      return new Response(JSON.stringify({ users }), {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    if (req.method === "POST") {
      const body = await req.json();
      const { action, user_id, credits_remaining, credits_total, week_start } = body;

      if (action === "update_credits") {
        const updateFields: any = {};
        if (credits_remaining !== undefined) updateFields.credits_remaining = credits_remaining;
        if (credits_total !== undefined) updateFields.credits_total = credits_total;
        if (week_start !== undefined) updateFields.week_start = week_start;
        updateFields.updated_at = new Date().toISOString();

        const { error } = await supabase
          .from("user_credits")
          .update(updateFields)
          .eq("user_id", user_id);
        if (error) throw error;

        return new Response(JSON.stringify({ success: true }), {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
        });
      }

      return new Response(JSON.stringify({ error: "Unknown action" }), {
        status: 400,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
