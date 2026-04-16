
-- Add username to profiles
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS username TEXT UNIQUE;

-- Add week_start to user_credits for weekly reset tracking
ALTER TABLE public.user_credits ADD COLUMN IF NOT EXISTS week_start TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT date_trunc('week', now());

-- Create teams table
CREATE TABLE public.teams (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  name TEXT NOT NULL,
  owner_id UUID NOT NULL,
  plan TEXT NOT NULL DEFAULT 'free',
  max_members INTEGER NOT NULL DEFAULT 5,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE public.teams ENABLE ROW LEVEL SECURITY;

-- Create team_members table
CREATE TABLE public.team_members (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
  user_id UUID NOT NULL,
  role TEXT NOT NULL DEFAULT 'member',
  joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE(team_id, user_id)
);

ALTER TABLE public.team_members ENABLE ROW LEVEL SECURITY;

-- Create team_chat_messages table
CREATE TABLE public.team_chat_messages (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  team_id UUID NOT NULL REFERENCES public.teams(id) ON DELETE CASCADE,
  user_id UUID,
  role TEXT NOT NULL DEFAULT 'user',
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE public.team_chat_messages ENABLE ROW LEVEL SECURITY;

-- Enable realtime for team chat messages
ALTER PUBLICATION supabase_realtime ADD TABLE public.team_chat_messages;

-- RLS: Teams
CREATE POLICY "Team members can view their teams"
ON public.teams FOR SELECT
USING (
  EXISTS (SELECT 1 FROM public.team_members WHERE team_members.team_id = teams.id AND team_members.user_id = auth.uid())
);

CREATE POLICY "Authenticated users can create teams"
ON public.teams FOR INSERT
WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Team owners can update their teams"
ON public.teams FOR UPDATE
USING (auth.uid() = owner_id);

CREATE POLICY "Team owners can delete their teams"
ON public.teams FOR DELETE
USING (auth.uid() = owner_id);

-- RLS: Team Members
CREATE POLICY "Team members can view members of their teams"
ON public.team_members FOR SELECT
USING (
  EXISTS (SELECT 1 FROM public.team_members AS tm WHERE tm.team_id = team_members.team_id AND tm.user_id = auth.uid())
);

CREATE POLICY "Team owners can add members"
ON public.team_members FOR INSERT
WITH CHECK (
  EXISTS (SELECT 1 FROM public.teams WHERE teams.id = team_members.team_id AND teams.owner_id = auth.uid())
);

CREATE POLICY "Team owners can remove members"
ON public.team_members FOR DELETE
USING (
  EXISTS (SELECT 1 FROM public.teams WHERE teams.id = team_members.team_id AND teams.owner_id = auth.uid())
);

-- RLS: Team Chat Messages
CREATE POLICY "Team members can view chat messages"
ON public.team_chat_messages FOR SELECT
USING (
  EXISTS (SELECT 1 FROM public.team_members WHERE team_members.team_id = team_chat_messages.team_id AND team_members.user_id = auth.uid())
);

CREATE POLICY "Team members can send chat messages"
ON public.team_chat_messages FOR INSERT
WITH CHECK (
  auth.uid() = user_id AND
  EXISTS (SELECT 1 FROM public.team_members WHERE team_members.team_id = team_chat_messages.team_id AND team_members.user_id = auth.uid())
);

-- Function to check and reset weekly credits
CREATE OR REPLACE FUNCTION public.check_weekly_credits(p_user_id UUID)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  current_week_start TIMESTAMP WITH TIME ZONE;
  stored_week_start TIMESTAMP WITH TIME ZONE;
  remaining INTEGER;
BEGIN
  current_week_start := date_trunc('week', now());
  
  SELECT week_start, credits_remaining INTO stored_week_start, remaining
  FROM public.user_credits WHERE user_id = p_user_id FOR UPDATE;
  
  IF stored_week_start IS NULL OR stored_week_start < current_week_start THEN
    UPDATE public.user_credits 
    SET credits_remaining = 50, week_start = current_week_start, updated_at = now()
    WHERE user_id = p_user_id;
    RETURN 50;
  END IF;
  
  RETURN remaining;
END;
$$;

-- Update handle_new_user to set username
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (user_id, display_name, username)
  VALUES (
    NEW.id, 
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.email),
    COALESCE(NEW.raw_user_meta_data->>'username', NULL)
  );
  
  INSERT INTO public.user_credits (user_id, credits_remaining, credits_total, plan, week_start)
  VALUES (NEW.id, 50, 50, 'free', date_trunc('week', now()));
  
  RETURN NEW;
END;
$$;

-- Trigger for teams updated_at
CREATE TRIGGER update_teams_updated_at
BEFORE UPDATE ON public.teams
FOR EACH ROW
EXECUTE FUNCTION public.update_updated_at_column();
