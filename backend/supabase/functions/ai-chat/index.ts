// ترازینو — دروازهٔ AI (Supabase Edge Function / Deno).
// کلیدها فقط از متغیرهای محیطی سرور خوانده می‌شوند؛ هرگز در پاسخ یا لاگ نمی‌آیند. متن پرسش‌ها ذخیره/لاگ نمی‌شود.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const KEY_ENV: Record<string, string> = { deepseek: "DEEPSEEK_API_KEY", openai: "OPENAI_API_KEY", claude: "ANTHROPIC_API_KEY", grok: "XAI_API_KEY" };
const ALLOWED_HOSTS = new Set(["api.deepseek.com", "api.openai.com", "api.anthropic.com", "api.x.ai"]); // جلوگیری از SSRF
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type Msg = { role: "system" | "user" | "assistant"; content: string };
type Prov = { base_url: string; model: string; protocol: "openai" | "anthropic" };

function validProvider(p: Prov | undefined): p is Prov {
  if (!p || !p.model) return false;
  try { const u = new URL(p.base_url); return u.protocol === "https:" && ALLOWED_HOSTS.has(u.host); } catch { return false; }
}

async function callProvider(name: string, p: Prov, msgs: Msg[], maxTokens: number, timeoutMs: number): Promise<string> {
  const key = Deno.env.get(KEY_ENV[name] ?? "");
  if (!key) throw new Error("no_key");
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    let res: Response;
    if (p.protocol === "anthropic") {
      const system = msgs.filter((m) => m.role === "system").map((m) => m.content).join("\n");
      res = await fetch(p.base_url.replace(/\/$/, "") + "/messages", {
        method: "POST", signal: ctl.signal,
        headers: { "Content-Type": "application/json", "x-api-key": key, "anthropic-version": "2023-06-01" },
        body: JSON.stringify({ model: p.model, max_tokens: maxTokens, ...(system ? { system } : {}), messages: msgs.filter((m) => m.role !== "system") }),
      });
      if (!res.ok) throw new Error("upstream_" + res.status);
      const data = await res.json();
      return (data.content ?? []).filter((c: { type: string }) => c.type === "text").map((c: { text: string }) => c.text).join("");
    }
    res = await fetch(p.base_url.replace(/\/$/, "") + "/chat/completions", {
      method: "POST", signal: ctl.signal,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` },
      body: JSON.stringify({ model: p.model, max_tokens: maxTokens, messages: msgs }),
    });
    if (!res.ok) throw new Error("upstream_" + res.status);
    const data = await res.json();
    return data.choices?.[0]?.message?.content ?? "";
  } finally { clearTimeout(timer); }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  const auth = req.headers.get("Authorization") ?? "";
  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: auth } } });
  const { data: u, error: ue } = await userClient.auth.getUser();
  if (ue || !u?.user) return json(401, { error: "unauthorized" });

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: cfg } = await admin.from("ai_config").select("*").eq("id", 1).single();
  if (!cfg || !cfg.enabled) return json(503, { error: "ai_disabled" });          // تنظیم نامعتبر/غیرفعال ⇒ AI خاموش، اپ سالم می‌ماند
  const primary = cfg.providers?.[cfg.active_provider];
  if (!validProvider(primary)) return json(503, { error: "ai_unavailable" });

  let body: { messages?: Msg[] };
  try { body = await req.json(); } catch { return json(400, { error: "bad_request" }); }
  const msgs = body.messages;
  if (!Array.isArray(msgs) || msgs.length === 0 || msgs.length > 20 ||
      msgs.some((m) => !["system", "user", "assistant"].includes(m.role) || typeof m.content !== "string")) return json(400, { error: "bad_request" });
  const chars = msgs.reduce((s, m) => s + m.content.length, 0);
  if (chars > cfg.max_input_chars) return json(413, { error: "too_large" });

  const today = new Date().toISOString().slice(0, 10);
  const { data: ok } = await admin.rpc("ai_try_consume", { p_user: u.user.id, p_day: today, p_daily: cfg.daily_request_limit, p_monthly: cfg.monthly_request_limit, p_chars: chars });
  if (!ok) return json(429, { error: "quota_exceeded" });

  const order = [cfg.active_provider, cfg.fallback_provider].filter((x): x is string => !!x);
  for (const name of order) {                       // حداکثر دو تلاش؛ بدون retry بی‌نهایت
    const p = cfg.providers?.[name];
    if (!validProvider(p)) continue;
    try {
      const text = await callProvider(name, p, msgs, cfg.max_output_tokens, cfg.timeout_ms);
      return json(200, { text, provider: name });
    } catch (_e) { /* جزئیات داخلی عمداً لاگ/برگردانده نمی‌شود */ }
  }
  return json(502, { error: "upstream_failed" });
});
