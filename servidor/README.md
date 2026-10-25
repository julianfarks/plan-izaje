# Servidor intermedio para la IA opcional de PlanIzaje

La app **no necesita esto** para funcionar: voz y lectura de tablas trabajan en el teléfono sin internet.
Este servidor solo hace falta si quieres que un modelo de IA (Anthropic) interprete explicaciones más libres
o lea tablas y gráficos difíciles.

## Por qué un servidor intermedio

Una clave de API dentro del APK o en un repositorio público la puede copiar cualquiera. Aquí la clave vive
solo como **secreto** del Worker. El teléfono guarda únicamente la dirección del Worker y un **código de acceso**
que tú inventas (`CODIGO_APP`). Si el código se filtra, lo cambias en el Worker y en la app.

## Desplegar en Cloudflare Workers (gratis para uso bajo)

1. Crea una cuenta en cloudflare.com y otra en console.anthropic.com (con una clave de API).
2. En un PC con Node.js:
   ```bash
   cd servidor
   npx wrangler login
   npx wrangler secret put ANTHROPIC_API_KEY   # pega tu clave de Anthropic
   npx wrangler secret put CODIGO_APP          # inventa un código largo, p. ej. 32 caracteres
   npx wrangler deploy
   ```
3. Wrangler muestra la dirección, p. ej. `https://planizaje-ia.TU-USUARIO.workers.dev`.
4. En la app: menú ⋮ de inicio → **Servicio de IA (opcional)** → pega la dirección y el código → **Probar conexión**.

## Qué hace

- `POST /extraer` — recibe el texto dictado y devuelve campos propuestos (`CONFIRMADO`/`APROXIMADO`), preguntas y avisos.
- `POST /tabla` — recibe la foto recortada de la tabla y devuelve radios/capacidades, marcando lo ilegible.
- `POST /ping` — prueba de conexión.

El Worker filtra las claves permitidas y limita tamaños. La app vuelve a validar todo, obliga a revisar cada
dato y nunca marca como verificada una tabla leída por IA. Cada uso consume créditos de tu cuenta de Anthropic.
