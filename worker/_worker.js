/**
 * Mis Recordatorios — Cloudflare Worker
 * Recibe mensajes de WhatsApp (Meta Cloud API test number) y los guarda en
 * Firestore (colección "recordatorios") con la estructura:
 *   { titulo, fecha, origen: "whatsapp", completado: false }
 *
 * Sin dependencias externas (un solo archivo, listo para pegar en el dashboard).
 */

// ---------------------------------------------------------------------------
// Parser de fechas en español (sin librerías)
// ---------------------------------------------------------------------------

const DIAS = {
  domingo: 0, lunes: 1, martes: 2, miercoles: 3,
  jueves: 4, viernes: 5, sabado: 6,
};

function normalize(s) {
  return s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
}

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Devuelve { titulo, fecha } o { error } */
export function parseReminder(text) {
  const lower = normalize(text);
  if (!lower) return { error: "Mensaje vacío" };

  const now = new Date();
  const out = {
    title: "",
    date: null,
    matchedPhrases: [],
  };

  // Hora: "a las 9", "a las 9:30", "9:30", "a las 9 pm", "en 2 horas", "en 30 minutos"
  let hour = null;
  let minute = 0;

  // "en N minutos/horas" → tiempo relativo; excluye interpretar N como hora
  const enMatch = lower.match(/en\s+(\d+)\s+(horas|hora|minutos|minuto)/);
  if (enMatch) {
    const qty = parseInt(enMatch[1], 10);
    out.date = new Date(now);
    if (enMatch[2].startsWith("hora")) out.date.setHours(now.getHours() + qty);
    else out.date.setMinutes(now.getMinutes() + qty);
    out.matchedPhrases.push(enMatch[0]);
  } else {
    const timeMatch = lower.match(/(?:a\s+las\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?/);
    if (timeMatch) {
      let h = parseInt(timeMatch[1], 10);
      const m = timeMatch[2] ? parseInt(timeMatch[2], 10) : 0;
      const pm = timeMatch[3];
      if (pm === "pm" && h < 12) h += 12;
      if (pm === "am" && h === 12) h = 0;
      if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
        hour = h;
        minute = m;
        out.matchedPhrases.push(timeMatch[0].trim());
      }
    }
  }

  // Día: hoy / mañana / pasado mañana / nombre de día / en N horas/min
  const diasNum = [0, 1, 2, 3, 4, 5, 6]; // domingo..sabado
  let dayOffset = null;

  if (lower.includes("pasado mañana") || lower.includes("pasado manana")) {
    dayOffset = 2;
    out.matchedPhrases.push("pasado mañana");
  } else if (lower.includes("mañana") || lower.includes("manana")) {
    dayOffset = 1;
    out.matchedPhrases.push("mañana");
  } else if (lower.includes("hoy")) {
    dayOffset = 0;
    out.matchedPhrases.push("hoy");
  } else {
    for (let i = 0; i < 7; i++) {
      const name = Object.keys(DIAS).find((k) => DIAS[k] === i);
      const regexp = new RegExp(`\\b${name}\\b`);
      if (regexp.test(lower)) {
        const target = (i - now.getDay() + 7) % 7;
        dayOffset = target === 0 ? 7 : target; // "lunes" = el próximo lunes (no hoy)
        out.matchedPhrases.push(name);
        break;
      }
    }
  }

  const base = out.date ?? new Date(now);
  if (dayOffset !== null) {
    const d = new Date(base);
    d.setDate(d.getDate() + dayOffset);
    out.date = d;
  }

  if (out.date && (hour !== null || dayOffset !== null)) {
    if (hour !== null) {
      const d = new Date(out.date);
      d.setHours(hour, minute, 0, 0);
      out.date = d;
    }
  } else if (!out.date && hour !== null) {
    const d = new Date(now);
    d.setHours(hour, minute, 0, 0);
    if (d.getTime() <= now.getTime()) d.setDate(d.getDate() + 1); // hora ya pasó → mañana
    out.date = d;
  }

  // Si el usuario dio una hora que ya pasó ("hoy 21:30" a las 22:00) → próximo día
  if (hour !== null && out.date && out.date.getTime() <= now.getTime()) {
    const d = new Date(out.date);
    d.setDate(d.getDate() + 1);
    out.date = d;
  }

  // Alternativa: si solo hay hora y ya pasó, va a mañana (caso cubierto arriba).

  if (!out.date) {
    return {
      error:
        "No entendí la fecha/hora. Escribí el título y cuándo, por ejemplo: Llamar a mamá mañana a las 9",
    };
  }

  // Título = mensaje original sin las frases de fecha/hora detectadas
  let title = text.trim();
  const uniquePhrases = [...new Set(out.matchedPhrases)].sort((a, b) => b.length - a.length);
  for (const phrase of uniquePhrases) {
    if (!phrase) continue;
    title = title.replace(new RegExp(escapeRegExp(phrase), "gi"), " ");
  }
  // Quitar prefijos comunes
  title = title
    .replace(/^[^\w\dÁÉÍÓÚáéíóúñÑ]+/, "")
    .replace(/\s{2,}/g, " ")
    .trim();
  // Limpiar "a las" suelto que haya quedado
  title = title.replace(/\s+a\s+las\s*$/i, "").trim();
  // Quitar "recordame/recordá/recuerda/avísame" iniciales
  title = title
    .replace(/^(recordame|recordá|recuerda|recrea|avísame|avisa)\s+/i, "")
    .replace(/^[^\w\dÁÉÍÓÚáéíóúñÑ]+/, "")
    .trim();
  // Si se mencionó un día de la semana, quitar artículos/conectores sueltos
  if (dayOffset !== null) {
    title = title
      .replace(/^(el|la|los|las|a|al|para)\s+/i, "")
      .replace(/\s+(el|la|los|las|a|al|para)$/i, "")
      .trim();
  }
  // Primera letra en mayúscula (para que el título se vea prolijo)
  title = title.replace(/^(\S)/, (m) => m.toUpperCase());

  if (!title) {
    return { error: "Escribí también el título, por ejemplo: Llamar a mamá mañana a las 9" };
  }

  return { title, date: out.date };
}

// ---------------------------------------------------------------------------
// Firestore REST (con service account)
// ---------------------------------------------------------------------------

async function getAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const b64 = (obj) => btoa(JSON.stringify(obj)).replace(/=+$/, "");
  const signingInput = `${b64(header)}.${b64(claims)}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBuf(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput)
  );
  const signature = btoa(String.fromCharCode(...new Uint8Array(sig))).replace(/=+$/, "");
  const jwt = `${signingInput}.${signature}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${encodeURIComponent(jwt)}`,
  });
  const data = await res.json();
  if (!data.access_token) throw new Error("sin access_token de Google: " + JSON.stringify(data));
  return data.access_token;
}

function pemToBuf(pem) {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const bin = atob(body);
  const buf = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
  return buf.buffer;
}

async function writeRecordatorio(serviceAccount, titulo, fechaIso) {
  const token = await getAccessToken(serviceAccount);
  const projectId = serviceAccount.project_id;
  const url =
    `https://firestore.googleapis.com/v1/projects/${projectId}` +
    `/databases/(default)/documents/recordatorios`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      fields: {
        titulo: { stringValue: titulo },
        fecha: { timestampValue: fechaIso },
        origen: { stringValue: "whatsapp" },
        completado: { booleanValue: false },
      },
    }),
  });
  if (!res.ok) throw new Error("Firestore " + res.status + ": " + (await res.text()));
  return res.json();
}

async function sendWhatsAppReply(from, text, phoneNumberId, token) {
  const url = `https://graph.facebook.com/v21.0/${phoneNumberId}/messages`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      messaging_product: "whatsapp",
      to: from,
      type: "text",
      text: { body: text },
    }),
  });
  if (!res.ok) throw new Error("WhatsApp " + res.status + ": " + (await res.text()));
}

// ---------------------------------------------------------------------------
// Handler (Workers)
// ---------------------------------------------------------------------------

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    // --- Verificación del webhook (Meta hace GET al configurar) ---
    if (request.method === "GET" && path === "/webhook") {
      const mode = url.searchParams.get("hub.mode");
      const verifyToken = url.searchParams.get("hub.verify_token");
      const challenge = url.searchParams.get("hub.challenge");
      if (mode === "subscribe" && verifyToken === env.VERIFY_TOKEN) {
        return new Response(challenge, { status: 200 });
      }
      return new Response("Verificación fallida", { status: 403 });
    }

    // --- Mensaje entrante (Meta hace POST) ---
    if (request.method === "POST" && path === "/webhook") {
      ctx.waitUntil(handleMessage(request, env).catch((e) => console.error(e)));
      return new Response("OK", { status: 200 });
    }

    return new Response("Not Found", { status: 404 });
  },
};

async function handleMessage(request, env) {
  const payload = await request.json();
  const change = payload?.entry?.[0]?.changes?.[0]?.value;
  const msg = change?.messages?.[0];
  if (!msg || msg.type !== "text" || !msg.text?.body) return; // ignore delivery, status, etc.

  const from = msg.from;
  const text = msg.text.body;

  const parsed = parseReminder(text);
  const replyPrefix = (ok) => ok ? "✅" : "⚠️";

  if (!parsed.title) {
    await sendWhatsAppReply(from, parsed.error, env.PHONE_NUMBER_ID, env.WHATSAPP_TOKEN);
    return;
  }

  try {
    const sa = JSON.parse(env.SERVICE_ACCOUNT_JSON);
    const fechaIso = parsed.date.toISOString();
    await writeRecordatorio(sa, parsed.title, fechaIso);
    const fechaLocal = parsed.date.toLocaleString("es-AR", {
      weekday: "long", day: "numeric", month: "long", hour: "2-digit", minute: "2-digit",
    });
    await sendWhatsAppReply(
      from,
      `${replyPrefix(true)} Recordatorio creado:\n"${parsed.title}"\n📅 ${fechaLocal}`,
      env.PHONE_NUMBER_ID,
      env.WHATSAPP_TOKEN
    );
  } catch (e) {
    console.error("Fallo al guardar:", e);
    await sendWhatsAppReply(
      from,
      `${replyPrefix(false)} No pude guardar el recordatorio. Probá de nuevo en unos minutos.`,
      env.PHONE_NUMBER_ID,
      env.WHATSAPP_TOKEN
    );
  }
}