# Plan de Izaje Los Magníficos SAS (versión simple)

App Android para crear el plan de izaje de una grúa con camión. Solo se piden cinco datos
(operador, cédula, fecha, cliente y sitio) y el resto se **dicta o escribe** en un cuadro grande:
la app entiende el texto, calcula y genera un PDF profesional en A4 con el logo original.

## Uso
1. Abre la app, llena los 5 datos y toca **Dictar maniobra** (o escribe).
2. **Revisar datos** muestra lo que se entendió, solo pide lo indispensable que falte y permite corregir.
3. **Generar PDF** → vista previa, compartir (WhatsApp, correo), abrir o guardar copia.
4. Los planes quedan en *Planes anteriores* (se guardan solos): se pueden reabrir, editar y regenerar el PDF.
5. Menú ⋮: exportar / importar respaldo.

## Qué calcula
- Peso total suspendido (carga + gancho + eslingas + grilletes + balancín + otros; sin duplicar lo «ya incluido»).
- Utilización por radio = peso / capacidad × 100 y margen, con las capacidades que el operador informe.
  **Nunca interpola ni inventa capacidades** de la tabla de cargas.
- Tensión por ramal de eslinga T = W / (n · sin θ) con θ desde la horizontal, en condiciones ideales.
- Si falta algo indispensable o algo se excede, el PDF sale **PRELIMINAR – BORRADOR**. La app no declara
  una maniobra «segura» ni «aprobada».

## Compilar
- Automático: GitHub Actions (`.github/workflows/build-magnificos.yml`) corre las pruebas y publica el APK en *Releases*.
- Local: abrir la carpeta `magnificos/` en Android Studio, o `gradle :core:test :app:assembleRelease` (JDK 17, Android SDK 35).
- El núcleo de cálculo (`core/`) es Kotlin puro con pruebas JUnit (peso total, utilización, conversión de unidades, tensión de eslingas, dictado en español).

## Limitaciones
- El dictado usa el reconocedor de voz del teléfono (puede requerir internet o el paquete de voz en español).
- No incluye tablas de carga oficiales: las capacidades las da el operador y deben confirmarse con la tabla oficial.
- Firma: usa la misma llave del repositorio (`app/planizaje.jks`).
