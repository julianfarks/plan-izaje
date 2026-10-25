package co.planizaje.core.model

/**
 * Tablas transcritas de FOTOS de diagramas de carga aportadas por el usuario.
 * No son datos de catálogo: cada plantilla se crea SIN VERIFICAR y con la foto adjunta como fuente.
 * El usuario debe completar el nº de serie y confirmar contra la placa de su grúa antes de usarla.
 */
data class PlantillaGrua(
    val clave: String,
    val fabricante: String,
    val modelo: String,
    val variante: String,
    val extensionesHidraulicas: Int?,
    val asset: String,
    val referenciaPlaca: String,
    val tablas: List<Triple<String, String, List<Pair<Double, Double>>>>,
    val notas: String,
)

object PlantillasGruas {
    const val DOC = "Foto del diagrama de carga aportada por el usuario"

    val todas = listOf(
        PlantillaGrua(
            "hiab288", "Hiab", "288 E-8", "HIPRO", 8, "diagramas/hiab_288_e8_hipro.png", "Placa 377 7120",
            listOf(
                Triple("Fila ADC", "Pluma completa, fila «ADC kg»", listOf(2.5 to 9200.0, 4.7 to 5100.0, 6.3 to 3550.0, 8.1 to 2540.0, 10.0 to 1880.0, 12.0 to 1480.0, 14.1 to 1160.0, 16.3 to 940.0, 16.6 to 800.0, 20.8 to 700.0, 22.8 to 600.0)),
                Triple("Fila kg (sin ADC)", "Pluma completa, fila «kg»", listOf(2.5 to 8800.0, 4.7 to 4750.0, 6.3 to 3250.0, 8.1 to 2320.0, 10.0 to 1700.0, 12.0 to 1700.0, 14.1 to 1120.0, 16.3 to 840.0, 16.6 to 700.0, 20.8 to 620.0)),
            ),
            "Ángulo de pluma indicado 11°. La fila «kg» muestra 1700 kg tanto en 10,0 m como en 12,0 m (mayor que la fila ADC en 12,0 m): confirmar con el manual. Confirmar qué significa ADC en el manual de su equipo y cuándo aplica cada fila.",
        ),
        PlantillaGrua(
            "pm85028", "PM", "85028 SP PT", "Serie 85 SP PT", null, "diagramas/pm_85028_sp_pt.jpg", "Diagrama ref. 171.014",
            listOf(
                Triple("Pluma hidráulica", "Pluma principal (todas las extensiones hidráulicas)", listOf(2.80 to 23000.0, 4.70 to 14270.0, 6.40 to 10200.0, 8.10 to 7670.0, 10.00 to 6000.0, 11.90 to 4840.0, 14.00 to 4000.0, 16.15 to 3365.0, 18.40 to 2910.0, 20.65 to 2570.0)),
                Triple("Extensiones opcionales", "Accesorio opcional: extensiones adicionales", listOf(22.50 to 1700.0, 24.35 to 1410.0)),
                Triple("Plumín J 903.20", "Con plumín J 903.20", listOf(23.60 to 1540.0, 25.35 to 1400.0, 27.10 to 1200.0, 28.85 to 910.0, 30.60 to 750.0, 31.90 to 500.0, 33.60 to 400.0)),
                Triple("Plumín J 904.20", "Con plumín J 904.20", listOf(23.60 to 1475.0, 25.35 to 1340.0, 27.10 to 1150.0, 28.85 to 860.0, 30.60 to 700.0, 31.90 to 500.0, 33.60 to 400.0)),
            ),
            "Ángulo de pluma indicado 10°. Las tablas de accesorios solo aplican si el equipo tiene montado ese accesorio.",
        ),
        PlantillaGrua(
            "pk13000c", "Palfinger", "PK 13000", "C", null, "diagramas/palfinger_pk13000c.jpg", "Placa EB 1492",
            listOf(
                Triple("Extensiones hidráulicas", "Pluma hidráulica completa", listOf(4.2 to 2730.0, 6.2 to 1710.0, 8.2 to 1210.0, 10.4 to 920.0, 12.5 to 740.0)),
                Triple("Extensiones manuales", "Con extensiones manuales (45, 35 y 30 kg)", listOf(14.8 to 590.0, 16.8 to 490.0, 18.8 to 370.0)),
            ),
            "Ángulo 20°. La capacidad «max. 4190 kg» no indica radio en la placa y no se incluye: obtener su radio del manual. Las extensiones manuales pesan 45, 35 y 30 kg según la placa.",
        ),
        PlantillaGrua(
            "hiab111", "Hiab", "111 B-3", "CLX", 3, "diagramas/hiab_111_b3_clx.jpg", "Placa 381 1760",
            listOf(
                Triple("Pluma completa", "Pluma completa (3 extensiones)", listOf(3.1 to 3150.0, 4.3 to 2200.0, 5.9 to 1560.0, 7.8 to 1140.0, 9.8 to 900.0, 11.8 to 680.0)),
            ),
            "Ángulo de pluma indicado 28°.",
        ),
        PlantillaGrua(
            "pk23500", "Palfinger", "PK 23500", "", null, "diagramas/palfinger_pk23500.png", "Placa EB 4217",
            listOf(
                Triple("Fila E (superior)", "Fila E superior", listOf(4.6 to 4520.0, 6.1 to 3180.0, 8.0 to 2280.0, 10.1 to 1700.0, 12.3 to 1340.0, 14.4 to 1110.0, 16.5 to 960.0)),
                Triple("Fila D", "Fila D", listOf(4.6 to 4590.0, 6.1 to 3260.0, 8.0 to 2370.0, 10.1 to 1800.0, 12.3 to 1450.0, 14.4 to 1210.0)),
                Triple("Fila C", "Fila C", listOf(4.6 to 4590.0, 6.1 to 3260.0, 8.0 to 2480.0, 10.1 to 1920.0, 12.3 to 1920.0)),
                Triple("Fila A", "Fila A", listOf(4.6 to 4520.0, 6.1 to 3470.0, 8.0 to 2600.0, 10.1 to 2040.0)),
                Triple("Fila E (inferior)", "Fila E inferior", listOf(4.6 to 4800.0, 6.1 to 3230.0, 8.0 to 2730.0)),
            ),
            "ATENCIÓN: la imagen recibida presenta inconsistencias (la fila «E» aparece dos veces, la fila C repite 1920 kg en 10,1 y 12,3 m). Puede no ser la placa real. Sustituir por una foto de la placa de su propia grúa antes de verificar. Los valores «max» (5600–6100 kg) no indican radio y no se incluyen.",
        ),
    )

    fun crear(pl: PlantillaGrua, nuevoId: () -> String, adjunto: Adjunto?): Grua {
        val fuenteBase = FuenteTecnica(DOC, pl.referenciaPlaca, adjuntoId = adjunto?.id)
        return Grua(
            id = nuevoId(),
            fabricante = pl.fabricante, modelo = pl.modelo, variante = pl.variante,
            tipo = TipoGrua.ARTICULADA,
            extensionesHidraulicas = pl.extensionesHidraulicas,
            documentos = listOfNotNull(adjunto),
            notas = pl.notas,
            tablas = pl.tablas.map { (nombre, config, pts) ->
                TablaCarga(
                    id = nuevoId(), nombre = nombre, configuracionPluma = config,
                    puntos = pts.map { PuntoTabla(it.first, it.second) },
                    fuente = fuenteBase,
                    verificacion = Verificacion(),
                    notas = pl.notas,
                )
            },
        )
    }
}
