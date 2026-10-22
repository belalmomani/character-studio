// Supabase Edge Function: sign-upload
// يعطي المسؤول فقط توقيع رفع Cloudinary قصير الأمد. سر Cloudinary يبقى في أسرار الدالة ولا يصل للمتصفح.
// الأسرار المطلوبة (Edge Functions → Secrets): CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

async function sha1Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  try {
    const auth = req.headers.get("Authorization") ?? "";
    const userClient = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
      global: { headers: { Authorization: auth } },
    });
    const { data: u } = await userClient.auth.getUser();
    if (!u?.user) return json(401, { error: "unauthenticated" });

    // الصلاحية تُقرأ من قاعدة البيانات (دور administrator) وليس من أي ادعاء من العميل
    const { data: isAdmin, error } = await userClient.rpc("is_admin");
    if (error || isAdmin !== true) return json(403, { error: "forbidden" });

    const cloud = Deno.env.get("CLOUDINARY_CLOUD_NAME");
    const key = Deno.env.get("CLOUDINARY_API_KEY");
    const secret = Deno.env.get("CLOUDINARY_API_SECRET");
    if (!cloud || !key || !secret) return json(500, { error: "cloudinary_not_configured" });

    const timestamp = Math.floor(Date.now() / 1000);
    const public_id = `characterstudio/assets/${crypto.randomUUID()}`; // يُولَّد في الخادم، لا يختاره العميل
    const allowed_formats = "png,webp";
    const signature = await sha1Hex(`allowed_formats=${allowed_formats}&public_id=${public_id}&timestamp=${timestamp}${secret}`);
    return json(200, { cloud_name: cloud, api_key: key, timestamp, public_id, allowed_formats, signature });
  } catch (_e) {
    return json(500, { error: "internal_error" });
  }
});
