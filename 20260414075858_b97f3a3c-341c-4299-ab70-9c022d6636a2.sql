
CREATE TABLE public.registered_ips (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  ip_address TEXT NOT NULL,
  user_id UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_registered_ips_ip ON public.registered_ips (ip_address);

ALTER TABLE public.registered_ips ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Anyone can check IPs" ON public.registered_ips FOR SELECT USING (true);
CREATE POLICY "Authenticated users can insert own IP" ON public.registered_ips FOR INSERT WITH CHECK (auth.uid() = user_id);
