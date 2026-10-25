/**
 * Servidor intermedio de PlanIzaje para el servicio de IA OPCIONAL (Cloudflare Workers).
 *
 * - La clave de la API de Anthropic se guarda como SECRETO del Worker (ANTHROPIC_API_KEY), nunca en la app.
 * - La app se identifica con un código de acceso (CODIGO_APP), también secreto.
 * - Solo expone 3 rutas: /ping, /extraer (texto dictado → campos) y /tabla (foto → tabla).
 * - La IA solo organiza información; los cálculos los hace la app con lógica determinista.
 *
 * Ver README.md de esta carpeta para desplegarlo.
 */

const CLAVES_VALIDAS = new Set([
  "NOMBRE", "UBICACION", "CLIENTE", "EMPRESA", "DESCRIPCION_TRABAJO",
  "CARGA_DESCRIPCION", "CARGA_PESO", "CARGA_LARGO", "CARGA_ANCHO", "CARGA_ALTO", "CARGA_CDG", "CARGA_PUNTOS",
  "CARGA_SUJECION", "CARGA_POS_INICIAL", "CARGA_POS_FINAL", "ALTURA_RECOGIDA", "ALTURA_DESCARGA", "RADIO",
  "DISTANCIA_REFERENCIA", "POSICION_GRUA", "OBSTACULOS", "GRUA", "CONFIG_EXTENSIONES", "ESTABILIZADORES",
  "APAREJO_RAMALES", "APAREJO_ENGANCHE", "APAREJO_DESCRIPCION", "OPERADOR", "SENALERO", "COMUNICACION",
  "OBSERVACIONES", "PENDIENTES", "GRUA_FABRICANTE", "GRUA_MODELO", "GRUA_VARIANTE", "GRUA_SERIE", "GRUA_PLACA",
  "GRUA_VEHICULO", "GRUA_EXTENSIONES", "GRUA_EXT_MANUALES",
]);

const PROMPT_EXTRAER = `Eres un asistente que organiza la explicación de un operador de grúa articulada sobre camión en los campos de un plan de izaje.
Reglas estrictas:
- Usa solo información que aparezca en el texto. Nunca inventes valores.
- estado = "CONFIRMADO" si el dato se dijo de forma explícita y segura; "APROXIMADO" si se dijo con duda ("unos", "creo", "más o menos") o si lo deduces.
- Una distancia "al camión" o "del camión" NO es el radio de trabajo: ponla en DISTANCIA_REFERENCIA y, si podría ser el radio, añade una pregunta.
- Si hay dos interpretaciones posibles, no elijas: añade una pregunta con opciones.
- Pesos en kg con unidad (p. ej. "850 kg"). Longitudes en metros, solo el número (p. ej. "12,5").
- Para GRUA usa el nombre de una de las grúas de la lista si coincide; si no coincide, no la incluyas y añade un aviso.
- No apruebes ni evalúes la seguridad de la maniobra.
Responde SOLO con JSON válido, sin texto adicional, con esta forma:
{"campos":[{"clave":"CLAVE","valor":"...","estado":"CONFIRMADO|APROXIMADO","nota":"..."}],
 "preguntas":[{"texto":"...","opciones":[{"etiqueta":"...","campos":[{"clave":"...","valor":"...","estado":"..."}]}]}],
 "avisos":["..."]}`;

const PROMPT_TABLA = `Lee el diagrama o tabla de capacidad de carga de una grúa en la imagen.
Reglas estrictas:
- Transcribe solo lo que se lee. NO completes celdas ilegibles ni inventes valores; si no estás seguro de una cifra, pon "legible": false.
- Si la cifra es totalmente ilegible, pon su valor como null.
- Radios en metros y capacidades en kg. Ignora libras y pies.
- Si hay varias configuraciones (filas por extensiones, con/sin plumín, ADC…), devuelve una tabla por configuración con su nombre tal como aparece.
- Indica fabricante, modelo, referencia/código del documento y unidades si se leen; si no, deja la cadena vacía.
- Añade advertencias sobre notas, restricciones o dudas de interpretación.
Responde SOLO con JSON válido:
{"fabricante":"","modelo":"","referencia":"","unidades":"",
 "tablas":[{"nombre":"","puntos":[{"radio":4.6,"capacidad":4520,"legible":true}]}],
 "advertencias":[""]}`;

function respuesta(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json; charset=utf-8" } });
}

async function llamarModelo(env, contenido, sistema) {
  const r = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      model: env.MODELO || "claude-sonnet-5-5",
      max_tokens: 4000,
      system: sistema,
      messages: [{ role: "user", content: contenido }],
    }),
  });
  if (!r.ok) throw new Error(`Proveedor de IA: ${r.status}`);
  const d = await r.json();
  const texto = (d.content || []).filter((c) => c.type === "text").map((c) => c.text).join("");
  const inicio = texto.indexOf("{");
  const fin = texto.lastIndexOf("}");
  if (inicio < 0 || fin < inicio) throw new Error("La IA no devolvió JSON");
  return JSON.parse(texto.slice(inicio, fin + 1));
}

function limpiarCampos(lista) {
  return (Array.isArray(lista) ? lista : [])
    .filter((c) => c && CLAVES_VALIDAS.has(c.clave) && typeof c.valor === "string" && c.valor.trim())
    .map((c) => ({ clave: c.clave, valor: c.valor.slice(0, 600), estado: c.estado === "CONFIRMADO" ? "CONFIRMADO" : "APROXIMADO", nota: (c.nota || "").slice(0, 300) }));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST") return respuesta({ error: "Método no permitido" }, 405);
    const auth = request.headers.get("authorization") || "";
    if (!env.CODIGO_APP || auth !== `Bearer ${env.CODIGO_APP}`) return respuesta({ error: "No autorizado" }, 401);
    if (!env.ANTHROPIC_API_KEY) return respuesta({ error: "Falta configurar ANTHROPIC_API_KEY" }, 500);

    try {
      if (url.pathname.endsWith("/ping")) return respuesta({ ok: true });

      if (url.pathname.endsWith("/extraer")) {
        const b = await request.json();
        const texto = String(b.texto || "").slice(0, 8000);
        if (!texto.trim()) return respuesta({ campos: [], preguntas: [], avisos: [] });
        const contexto = `Campos permitidos (CLAVE: descripción):\n${(b.claves || []).join("\n")}\n\nGrúas registradas por el usuario:\n${(b.gruas || []).join("\n") || "(ninguna)"}\n\nTexto del operador:\n"""${texto}"""`;
        const d = await llamarModelo(env, [{ type: "text", text: contexto }], PROMPT_EXTRAER);
        return respuesta({
          campos: limpiarCampos(d.campos),
          preguntas: (Array.isArray(d.preguntas) ? d.preguntas : []).slice(0, 6).map((q) => ({
            texto: String(q.texto || "").slice(0, 300),
            opciones: (Array.isArray(q.opciones) ? q.opciones : []).slice(0, 5).map((o) => ({ etiqueta: String(o.etiqueta || "").slice(0, 200), campos: limpiarCampos(o.campos) })),
          })),
          avisos: (Array.isArray(d.avisos) ? d.avisos : []).map((a) => String(a).slice(0, 300)).slice(0, 10),
        });
      }

      if (url.pathname.endsWith("/tabla")) {
        const b = await request.json();
        const imagen = String(b.imagen || "");
        if (!imagen || imagen.length > 9_000_000) return respuesta({ error: "Imagen ausente o demasiado grande" }, 400);
        const d = await llamarModelo(env, [
          { type: "image", source: { type: "base64", media_type: b.mime === "image/png" ? "image/png" : "image/jpeg", data: imagen } },
          { type: "text", text: `Grúa a la que el usuario quiere asociar la tabla: ${b.grua || "sin indicar"}. Si el modelo de la imagen no coincide, avísalo en advertencias.` },
        ], PROMPT_TABLA);
        const num = (v) => (typeof v === "number" && isFinite(v) ? v : null);
        return respuesta({
          fabricante: String(d.fabricante || ""), modelo: String(d.modelo || ""), referencia: String(d.referencia || ""), unidades: String(d.unidades || ""),
          tablas: (Array.isArray(d.tablas) ? d.tablas : []).slice(0, 12).map((t) => ({
            nombre: String(t.nombre || "").slice(0, 80),
            puntos: (Array.isArray(t.puntos) ? t.puntos : []).slice(0, 60).map((p) => ({ radio: num(p.radio), capacidad: num(p.capacidad), legible: p.legible === true })),
          })),
          advertencias: (Array.isArray(d.advertencias) ? d.advertencias : []).map((a) => String(a).slice(0, 300)).slice(0, 12),
        });
      }
      return respuesta({ error: "Ruta no encontrada" }, 404);
    } catch (e) {
      return respuesta({ error: String(e.message || e) }, 502);
    }
  },
};
