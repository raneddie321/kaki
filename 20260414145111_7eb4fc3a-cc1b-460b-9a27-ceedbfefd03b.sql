
-- Drop the existing SELECT policy and replace with one that also allows owners
DROP POLICY "Team members can view their teams" ON public.teams;

CREATE POLICY "Team members or owners can view their teams"
ON public.teams
FOR SELECT
USING (
  auth.uid() = owner_id
  OR EXISTS (
    SELECT 1 FROM team_members
    WHERE team_members.team_id = teams.id
    AND team_members.user_id = auth.uid()
  )
);
