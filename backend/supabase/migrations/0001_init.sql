-- ترازینو: پایگاه داده ابری (Supabase / PostgreSQL) با Row Level Security
-- توجه: این مهاجرت فقط کد است؛ روی پروژهٔ شما اجرا نشده و با اپ هنوز یکپارچه نشده (نیازمند صفحهٔ ورود + همگام‌سازی در اپ).

create table public.accounts (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (length(name) between 1 and 80),
  opening_balance bigint not null default 0 check (opening_balance >= 0),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.transactions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  idempotency_key text not null,
  type text not null check (type in ('INCOME','EXPENSE','TRANSFER','LOAN_PAYMENT')),
  amount bigint not null check (amount > 0 and amount <= 1000000000000000),
  account_id uuid not null references public.accounts(id) on delete cascade,
  to_account_id uuid references public.accounts(id) on delete cascade,
  epoch_day bigint not null,
  category text,
  note text not null default '',
  loan_id text,
  installment_no int,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, idempotency_key),
  unique (user_id, loan_id, installment_no)
);

alter table public.accounts enable row level security;
alter table public.transactions enable row level security;

create policy accounts_owner on public.accounts for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy transactions_owner on public.transactions for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- تنظیمات AI: فقط service_role (Edge Function) می‌خواند؛ هیچ policy برای کاربران عادی نیست.
create table public.ai_config (
  id int primary key default 1 check (id = 1),
  enabled boolean not null default false,
  active_provider text not null default 'deepseek',
  fallback_provider text,                       -- فقط با پیکربندی صریح و وجود کلید در سرور
  providers jsonb not null default '{
    "deepseek": {"base_url":"https://api.deepseek.com","model":"deepseek-flash","protocol":"openai"},
    "openai":   {"base_url":"https://api.openai.com/v1","model":"","protocol":"openai"},
    "claude":   {"base_url":"https://api.anthropic.com/v1","model":"","protocol":"anthropic"},
    "grok":     {"base_url":"https://api.x.ai/v1","model":"","protocol":"openai"}
  }'::jsonb,
  max_input_chars int not null default 8000 check (max_input_chars between 100 and 50000),
  max_output_tokens int not null default 700 check (max_output_tokens between 16 and 4000),
  timeout_ms int not null default 30000 check (timeout_ms between 3000 and 120000),
  daily_request_limit int not null default 50,
  monthly_request_limit int not null default 600,
  updated_at timestamptz not null default now()
);
alter table public.ai_config enable row level security;   -- بدون policy = فقط service_role
insert into public.ai_config (id) values (1) on conflict do nothing;

-- مصرف AI: فقط شمارنده؛ متن پرسش‌ها ذخیره نمی‌شود.
create table public.ai_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  requests int not null default 0,
  input_chars bigint not null default 0,
  primary key (user_id, day)
);
alter table public.ai_usage enable row level security;
create policy ai_usage_read_own on public.ai_usage for select to authenticated using (user_id = auth.uid());

-- مصرف را اتمی بررسی و ثبت می‌کند؛ false یعنی سقف روزانه/ماهانه پر است.
create or replace function public.ai_try_consume(p_user uuid, p_day date, p_daily int, p_monthly int, p_chars int)
returns boolean language plpgsql security definer set search_path = public as $$
declare d int; m int;
begin
  insert into ai_usage(user_id, day) values (p_user, p_day) on conflict do nothing;
  select requests into d from ai_usage where user_id = p_user and day = p_day for update;
  select coalesce(sum(requests),0) into m from ai_usage where user_id = p_user and day >= date_trunc('month', p_day)::date;
  if d >= p_daily or m >= p_monthly then return false; end if;
  update ai_usage set requests = requests + 1, input_chars = input_chars + p_chars where user_id = p_user and day = p_day;
  return true;
end $$;
revoke all on function public.ai_try_consume(uuid, date, int, int, int) from public, anon, authenticated;
grant execute on function public.ai_try_consume(uuid, date, int, int, int) to service_role;
