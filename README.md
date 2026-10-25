# PlanIzaje

App Android (Kotlin + Jetpack Compose) para planificar izajes con grúas —pensada para camiones grúa de brazo articulado— y generar el plan de izaje en PDF.

> PlanIzaje es una herramienta de planificación y documentación. **No sustituye** el manual del fabricante, el diagrama de carga de la grúa ni la revisión de una persona competente. La app nunca declara una maniobra «segura».

## Descargar e instalar

1. Abre la última versión en **[Releases](https://github.com/julianfarks/plan-izaje/releases/latest)** desde el teléfono.
2. Descarga el archivo `PlanIzaje-1.0.N.apk`.
3. Ábrelo. Si Android lo pide, permite «Instalar apps desconocidas» para el navegador o el gestor de archivos.
4. Las versiones nuevas se instalan **encima** de la anterior y conservan los datos (todas se firman con la misma llave).

Requisitos: Android 8.0 (API 26) o superior. Funciona sin internet.

## Qué hace

**Inicio** con seis accesos: crear plan, continuar proyecto, mis proyectos, mis grúas, mis accesorios y planes PDF.

**Asistente en 7 pasos** con barra de progreso y estado de cada paso (✓ completo, ! atención, número = sin ver). Se puede saltar pasos y volver; todo se guarda solo.

1. **Proyecto** – datos, responsables, adjuntos, advertencias, revisiones e historial de cambios.
2. **Grúa** – elegir equipo de la biblioteca, tabla de carga y configuración de estabilizadores.
3. **Carga** – peso en kg o t (con origen del dato y documento de respaldo), dimensiones, centro de gravedad, puntos de izaje, fotos.
4. **Croquis** – editor táctil en planta (ver abajo).
5. **Maniobra** – fases (recogida → descarga, radio verificado, altura de gancho, recorrido), aparejos con candidatos compatibles, criterios de la empresa, placa de apoyo y terreno, procedimiento, peligros y lista de comprobación.
6. **Verificación** – cada cálculo con datos, unidades, fórmula, hipótesis y fuente; estado por símbolo y texto (no solo color); revisión humana registrada.
7. **PDF** – genera el plan; si falta algo sale como BORRADOR con marca de agua.

**Editor de croquis**: biblioteca de 27 objetos (edificios, muros, calles, camiones, grúa, cargas, árboles, postes, líneas eléctricas con franja de distancia mínima, zonas de exclusión, puntos de recogida/descarga, eslingas, grilletes, ganchos, balancines, textos, cotas, líneas, flechas, polígonos). Mover, girar (manija ↻), redimensionar, editar vértices, duplicar, copiar/pegar, eliminar, bloquear, capas (ver/bloquear), cuadrícula y ajuste opcional, zoom con dos dedos, deshacer/rehacer, medir, cotas, leyenda, barra de escala, ajustar a pantalla, centrar en la grúa y exportar PNG de alta resolución. Las medidas introducidas como número pueden marcarse «verificadas en campo»; lo arrastrado con el dedo queda como aproximado (≈). Gestos sin conflicto: un dedo sobre el objeto seleccionado lo mueve, un dedo en otro sitio desplaza el plano, dos dedos hacen zoom.

**Maniobra en el croquis**: pluma (línea continua), trayectoria de la carga (discontinua con flecha y puntos de paso), radios por fase y vista lateral esquemática por fase con obstáculos cruzados.

**Cálculos** (módulo `core`, Kotlin puro):
- Peso de carga, accesorios y gancho (si la tabla no lo descuenta) → masa suspendida.
- Capacidad nominal desde la tabla: sin interpolar se toma el menor valor entre los radios tabulados que encierran el radio (conservador); interpolación lineal solo si se configura. Fuera de tabla = no cumple.
- Utilización frente al 100 % del fabricante y, opcionalmente, al criterio de la empresa.
- Eslingas: T = M / (n · cos θ); con 3–4 ramales se consideran 2 portantes salvo reparto justificado; reparto asimétrico por centro de gravedad; ángulo por medidas o directo; aviso > 60°; canasta y ahorcado con su WLL de etiqueta.
- Grilletes, ganchos, balancines y puntos de izaje contra su WLL (nunca la carga de rotura); inspección vencida o «no apto» = no cumple.
- Presión sobre el terreno con la fuerza máxima por apoyo del fabricante y la placa.
- Interferencias en planta con líneas eléctricas, zonas de exclusión y obstáculos (orientativo).

Estados de cada resultado: **✓ documentado · ! requiere revisión · … pendiente · ✕ no verificable**, y por separado **dentro del límite / excede el límite**.

**Estado del plan**: borrador (faltan datos o algo no cumple) → listo para revisión → revisado → aprobación registrada. La revisión humana guarda una huella de los datos: si después cambia algo relevante, la revisión caduca y el PDF anterior se marca como desactualizado.

**PDF** (A4, nativo de Android, croquis vectorial): portada con estado y casillas de firma a mano, pendientes, carga, grúa, aparejos, croquis con leyenda y escala, vistas laterales por fase, interferencias, procedimiento, peligros, meteorología, criterios de suspensión, lista de comprobación, anexos (fuente y valores de la tabla, memoria de cálculo completa, certificados, documentos, fotos, revisión registrada, historial). Encabezado, «Página x de N», fecha de generación, huella de datos y control de versiones (revisión + nº de versión).

**Bibliotecas**: grúas (identificación, vehículo, extensiones, plumín, estabilizadores con fuerza por apoyo, varias tablas de carga con fuente y verificación, documentos) y accesorios (WLL por enganche, peso, certificado, inspección, fotos).

**Plantillas desde fotos**: Hiab 288 E-8 HIPRO, PM 85028 SP PT (con extensiones y plumines J903.20/J904.20), Palfinger PK 13000 C, Hiab 111 B-3 CLX y Palfinger PK 23500 (6 extensiones, fila E superior). Se crean **sin verificar**, con la foto del diagrama adjunta como fuente; hay que poner el nº de serie y confirmar contra la placa de la grúa propia.

**Datos y copias**: almacenamiento local en JSON versionado con escritura atómica y copia `.bak`; migración de esquema al leer; los datos de una versión más nueva no se sobrescriben. Copia de seguridad exportable/restaurable (.zip) desde el menú ⋮ de inicio, con copia automática previa a cada restauración. Compatible con la copia de seguridad de Android.

## Pruebas

- `core`: 50 pruebas JUnit de unidades, tabla de carga (exacto, entre radios, interpolación, fuera de rango, duplicados), eslingas (ángulos, ramales, CdG, canasta), geometría, motor de verificación (estados, sobrecarga, criterio de empresa, sin tabla, peso estimado, inspección vencida, WLL vs rotura, terreno, líneas eléctricas, caducidad de la revisión), candidatos de aparejos y serialización/migración.
- `app` (emulador en GitHub Actions): recorrido completo de usuario (crear → datos → grúa → carga → croquis con edificio → fase → verificación → PDF → reabrir y comprobar datos y croquis), generación de PDF de muestra con todos los objetos (≥ 6 páginas), PNG del croquis, persistencia en disco y copia de seguridad ida y vuelta.

## Compilar

Cada `push` a `main` ejecuta `.github/workflows/build.yml`: pruebas del núcleo, compilación del APK firmado, publicación en Releases (`build-N`) y pruebas en emulador.

En local (Android SDK + JDK 17 + Gradle 8.11):

```bash
gradle :core:test
gradle :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
gradle :app:connectedDebugAndroidTest   # con un emulador o teléfono conectado
```

## Firma

El APK se firma con `app/planizaje.jks` (contraseña por defecto en `app/build.gradle.kts`) para que las actualizaciones se instalen encima. Como el repositorio es público, cualquiera podría firmar con esa llave. Para uso profesional, genera una llave nueva, guárdala como secreto del repositorio y pasa las contraseñas con `PLANIZAJE_STORE_PASSWORD` y `PLANIZAJE_KEY_PASSWORD` (al cambiar de llave hay que desinstalar la versión anterior: exporta antes una copia de seguridad).

## Limitaciones conocidas

- No hay tablas de fábrica: las capacidades vienen solo de los documentos que registres.
- El croquis es esquemático en planta; la vista lateral dibuja la pluma recta y no modela la geometría real del brazo articulado. No hay vista 3D.
- El análisis de interferencias es solo en planta y depende de lo dibujado.
- La presión sobre el terreno usa la fuerza máxima por apoyo del fabricante (envolvente), no la reacción real de la maniobra; no calcula estabilidad ni momento de vuelco.
- Ángulos de eslinga iguales en todos los ramales; el reparto asimétrico solo para 2 ramales.
- Sin firmas digitales ni sincronización en la nube.
