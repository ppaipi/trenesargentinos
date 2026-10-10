package ar.chiqui.trenes.datos

// Líneas que NO opera Trenes Argentinos (SOFSE): no hay API de arribos en vivo.
// Mismos datos que js/v5/programados-data.js (horarios oficiales en PDF).
// Al cambiar un horario, actualizar los dos lados.

data class LineaProgramada(
    val linea: String,
    val operador: String,
    val color: Long,
    val cabeceras: List<String>,
    val trayectoMin: Int,
    /** día → cabecera → (primero, último) */
    val horarios: Map<String, Map<String, Pair<String, String>>>,
    val frecuenciaMin: Map<String, String>,
    val fuenteUrl: String,
    val fuenteNombre: String,
    val vigencia: String,
)

val DIAS = listOf("habiles" to "Días hábiles", "sabados" to "Sábados", "domingos" to "Domingos y feriados")

val LINEAS_PROGRAMADAS = listOf(
    LineaProgramada(
        linea = "Urquiza",
        operador = "Metrovías",
        color = 0xFFE6007E,
        cabeceras = listOf("General Lemos", "Federico Lacroze"),
        trayectoMin = 49,
        horarios = mapOf(
            "habiles" to mapOf("General Lemos" to ("01:00" to "23:59"), "Federico Lacroze" to ("01:00" to "23:50")),
            "sabados" to mapOf("General Lemos" to ("01:00" to "23:48"), "Federico Lacroze" to ("01:00" to "23:50")),
            "domingos" to mapOf("General Lemos" to ("03:12" to "23:35"), "Federico Lacroze" to ("01:00" to "23:50")),
        ),
        frecuenciaMin = mapOf("habiles" to "10-15", "sabados" to "15-20", "domingos" to "30"),
        fuenteUrl = "https://metrovias.com.ar/wp-content/uploads/2026/04/Horarios-Urquiza-invierno-2026-WEB-2.pdf",
        fuenteNombre = "Metrovías",
        vigencia = "02/03/2026",
    ),
    LineaProgramada(
        linea = "Belgrano Norte",
        operador = "Ferrovías",
        color = 0xFF6B8F00,
        cabeceras = listOf("Retiro", "Villa Rosa"),
        trayectoMin = 90,
        horarios = mapOf(
            "habiles" to mapOf("Retiro" to ("04:37" to "23:45"), "Villa Rosa" to ("03:00" to "22:37")),
            "sabados" to mapOf("Retiro" to ("04:50" to "23:45"), "Villa Rosa" to ("03:10" to "22:37")),
            "domingos" to mapOf("Retiro" to ("04:55" to "23:45"), "Villa Rosa" to ("03:45" to "22:37")),
        ),
        frecuenciaMin = mapOf("habiles" to "14-18", "sabados" to "20-22", "domingos" to "35"),
        fuenteUrl = "https://ferrovias.com.ar/wp-content/uploads/2026/06/Mural-H21-web-completo.pdf",
        fuenteNombre = "Ferrovías",
        vigencia = "19/05/2026",
    ),
)
