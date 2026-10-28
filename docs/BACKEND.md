# راه‌اندازی بک‌اند (اختیاری — اپ بدون آن هم کار می‌کند)

> **وضعیت صادقانه:** کد بک‌اند نوشته شده ولی **مستقر و آزمایش نشده** و **به اپ وصل نشده** (اپ هنوز صفحهٔ ورود و کلاینت Supabase/همگام‌سازی ندارد). حالت فعلی اپ «کلید خودم» است: دستگاه مستقیم به سرویس AI می‌رود.

## مراحل
1. در supabase.com یک پروژه بساز. (Supabase CLI: `npm i -g supabase`، سپس `supabase login` و `supabase link --project-ref <REF>`.)
2. جداول و RLS: `supabase db push` (فایل `backend/supabase/migrations/0001_init.sql`).
3. کلیدها فقط روی سرور:
   `supabase secrets set DEEPSEEK_API_KEY=... SUPABASE_SERVICE_ROLE_KEY=...` (برای OpenAI/Claude/Grok هم `OPENAI_API_KEY`، `ANTHROPIC_API_KEY`، `XAI_API_KEY`). `SUPABASE_URL` و `SUPABASE_ANON_KEY` معمولاً خودکار در Edge Function موجودند.
4. استقرار: `supabase functions deploy ai-chat`.
5. روشن کردن AI (فقط با SQL Editor داشبورد، نه از اپ): `update ai_config set enabled = true where id = 1;`
6. تغییر ارائه‌دهنده بدون انتشار APK: `update ai_config set active_provider='openai', providers = jsonb_set(providers,'{openai,model}','"MODEL-NAME"') where id=1;` (نام مدل را از مستندات همان سرویس بگیر و کلیدش را در secrets بگذار). `fallback_provider` فقط وقتی کار می‌کند که کلیدش در سرور باشد.
7. آزمون: با یک JWT کاربر، `POST /functions/v1/ai-chat` با `{"messages":[{"role":"user","content":"سلام"}]}`.
8. چرخش کلید: کلید جدید بساز، `supabase secrets set ...`، کلید قدیمی را در پنل ارائه‌دهنده باطل کن. اگر کلیدی در Git یا لاگ لو رفت، فوراً باطلش کن و تاریخچهٔ Git را پاک/مخزن را بازسازی کن.

## امنیت پیاده‌شده در کد
احراز هویت JWT، RLS روی جداول، `ai_config` بدون دسترسی کاربران، سقف روزانه/ماهانه (اتمی)، سقف اندازهٔ ورودی و توکن خروجی، timeout، حداکثر دو تلاش، allowlist میزبان‌ها (ضد SSRF)، عدم ذخیرهٔ متن پرسش‌ها، پیام خطای عمومی. **آزمون خودکار برای این‌ها نوشته نشده.**
