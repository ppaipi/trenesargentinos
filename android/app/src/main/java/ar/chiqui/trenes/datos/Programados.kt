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
    /** Clave en recorridos.json (polilínea sobre vías OSM). */
    val recorrido: String,
    /** Estaciones en orden, coords de nodos OSM. */
    val estaciones: List<EstacionProgramada>,
)

data class EstacionProgramada(val nombre: String, val lat: Double, val lon: Double)

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
        recorrido = "Urquiza|||Urquiza",
        estaciones = listOf(
            EstacionProgramada("Federico Lacroze", -34.58675, -58.45549),
            EstacionProgramada("José Artigas", -34.5901, -58.4663),
            EstacionProgramada("Pedro Arata", -34.59248, -58.47628),
            EstacionProgramada("Doctor Francisco Beiró", -34.59321, -58.49466),
            EstacionProgramada("El Libertador", -34.59447, -58.50312),
            EstacionProgramada("Antonio Devoto", -34.5956, -58.51055),
            EstacionProgramada("Coronel Lynch", -34.59718, -58.52439),
            EstacionProgramada("Fernández Moreno", -34.59554, -58.53532),
            EstacionProgramada("Lourdes", -34.59378, -58.54698),
            EstacionProgramada("Tropezón", -34.5919, -58.55935),
            EstacionProgramada("José María Bosch", -34.59001, -58.57185),
            EstacionProgramada("Martín Coronado", -34.58897, -58.59067),
            EstacionProgramada("Pablo Podestá", -34.58835, -58.60799),
            EstacionProgramada("Jorge Newbery", -34.58786, -58.62103),
            EstacionProgramada("Rubén Darío", -34.58754, -58.62985),
            EstacionProgramada("Ejército de los Andes", -34.58022, -58.64035),
            EstacionProgramada("Juan B. de La Salle", -34.5733, -58.64558),
            EstacionProgramada("Sargento Barrufaldi", -34.56243, -58.66024),
            EstacionProgramada("Capitán Lozano", -34.55531, -58.66986),
            EstacionProgramada("Teniente Agneta", -34.54886, -58.6788),
            EstacionProgramada("Campo de Mayo", -34.54379, -58.68575),
            EstacionProgramada("Sargento Cabral", -34.5372, -58.69492),
            EstacionProgramada("General Lemos", -34.53286, -58.70092),
        ),
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
        recorrido = "Belgrano Norte|||Belgrano Norte",
        estaciones = listOf(
            EstacionProgramada("Retiro", -34.59016, -58.37392),
            EstacionProgramada("Saldías", -34.57246, -58.40266),
            EstacionProgramada("Ciudad Universitaria", -34.54307, -58.44777),
            EstacionProgramada("Aristóbulo del Valle", -34.53755, -58.47794),
            EstacionProgramada("Padilla", -34.54363, -58.50018),
            EstacionProgramada("Florida", -34.53714, -58.51306),
            EstacionProgramada("Munro", -34.53166, -58.52384),
            EstacionProgramada("Carapachay", -34.5288, -58.53739),
            EstacionProgramada("Villa Adelina", -34.52369, -58.54628),
            EstacionProgramada("Boulogne Sur Mer", -34.50903, -58.56489),
            EstacionProgramada("Vicealmirante Montes", -34.50416, -58.61948),
            EstacionProgramada("Don Torcuato", -34.50263, -58.64249),
            EstacionProgramada("Adolfo Sourdeaux", -34.50298, -58.66605),
            EstacionProgramada("Villa de Mayo", -34.50337, -58.68071),
            EstacionProgramada("Los Polvorines", -34.49878, -58.69504),
            EstacionProgramada("Pablo Nogués", -34.49371, -58.70637),
            EstacionProgramada("Grand Bourg", -34.48529, -58.72533),
            EstacionProgramada("Tierras Altas", -34.47897, -58.73947),
            EstacionProgramada("Tortuguitas", -34.47066, -58.758),
            EstacionProgramada("Manuel Alberti", -34.46283, -58.77562),
            EstacionProgramada("Del Viso", -34.45346, -58.79656),
            EstacionProgramada("Cecilia Grierson", -34.43921, -58.82414),
            EstacionProgramada("Villa Rosa", -34.41626, -58.87213),
        ),
    ),
)
