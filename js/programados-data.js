// Líneas que NO opera Trenes Argentinos (SOFSE): no hay API de arribos en vivo.
// Solo publican horarios oficiales → datos "programados", no circulación real.
// Modelo inspirado en anden.app (docs/DATOS-EN-VIVO.md): procedencia = scheduled.
// Donde la fuente oficial no publica el dato de forma confiable, va null + link.

export const LINEAS_PROGRAMADAS = [
  {
    id: "urquiza",
    linea: "Urquiza",
    operador: "Metrovías",
    color: "var(--linea-urquiza)",
    cabeceras: ["General Lemos", "Federico Lacroze"],
    trayectoMin: 49, // "tiempo entre cabeceras" que informa Metrovías (duración, no intervalo)
    horarios: {
      habiles: { "General Lemos": ["01:00", "23:59"], "Federico Lacroze": ["01:00", "23:50"] },
      sabados: { "General Lemos": ["01:00", "23:48"], "Federico Lacroze": ["01:00", "23:50"] },
      domingos: { "General Lemos": ["03:12", "23:35"], "Federico Lacroze": ["01:00", "23:50"] },
    },
    // Intervalo entre salidas desde Lacroze (mediana por franja, PDF "invierno 2026"):
    frecuenciaMin: { habiles: "10-15", sabados: "15-20", domingos: "30" },
    fuente: {
      url: "https://metrovias.com.ar/wp-content/uploads/2026/04/Horarios-Urquiza-invierno-2026-WEB-2.pdf",
      nombre: "Metrovías",
    },
    vigencia: "02/03/2026",
    // Mapa: clave en recorridos.json + estaciones en orden (nodos OSM).
    recorrido: "Urquiza|||Urquiza",
    estaciones: [
      ["Federico Lacroze", -34.58675, -58.45549],
      ["José Artigas", -34.5901, -58.4663],
      ["Pedro Arata", -34.59248, -58.47628],
      ["Doctor Francisco Beiró", -34.59321, -58.49466],
      ["El Libertador", -34.59447, -58.50312],
      ["Antonio Devoto", -34.5956, -58.51055],
      ["Coronel Lynch", -34.59718, -58.52439],
      ["Fernández Moreno", -34.59554, -58.53532],
      ["Lourdes", -34.59378, -58.54698],
      ["Tropezón", -34.5919, -58.55935],
      ["José María Bosch", -34.59001, -58.57185],
      ["Martín Coronado", -34.58897, -58.59067],
      ["Pablo Podestá", -34.58835, -58.60799],
      ["Jorge Newbery", -34.58786, -58.62103],
      ["Rubén Darío", -34.58754, -58.62985],
      ["Ejército de los Andes", -34.58022, -58.64035],
      ["Juan B. de La Salle", -34.5733, -58.64558],
      ["Sargento Barrufaldi", -34.56243, -58.66024],
      ["Capitán Lozano", -34.55531, -58.66986],
      ["Teniente Agneta", -34.54886, -58.6788],
      ["Campo de Mayo", -34.54379, -58.68575],
      ["Sargento Cabral", -34.5372, -58.69492],
      ["General Lemos", -34.53286, -58.70092],
    ],
  },
  {
    id: "belgrano-norte",
    linea: "Belgrano Norte",
    operador: "Ferrovías",
    color: "var(--linea-belgranonorte)",
    cabeceras: ["Retiro", "Villa Rosa"],
    trayectoMin: 90, // trenes de punta a punta, según Horario N° 21
    // Horario N° 21 (PDF oficial, vigente desde 19/05/2026). Solo trenes que hacen
    // el recorrido completo; hay servicios parciales y nocturnos que no figuran acá.
    horarios: {
      habiles: { Retiro: ["04:37", "23:45"], "Villa Rosa": ["03:00", "22:37"] },
      sabados: { Retiro: ["04:50", "23:45"], "Villa Rosa": ["03:10", "22:37"] },
      domingos: { Retiro: ["04:55", "23:45"], "Villa Rosa": ["03:45", "22:37"] },
    },
    // Intervalo entre salidas desde Retiro (mediana por franja, mismo PDF):
    frecuenciaMin: { habiles: "14-18", sabados: "20-22", domingos: "35" },
    fuente: {
      url: "https://ferrovias.com.ar/wp-content/uploads/2026/06/Mural-H21-web-completo.pdf",
      nombre: "Ferrovías",
    },
    vigencia: "19/05/2026",
    // Mapa: clave en recorridos.json + estaciones en orden (nodos OSM).
    recorrido: "Belgrano Norte|||Belgrano Norte",
    estaciones: [
      ["Retiro", -34.59016, -58.37392],
      ["Saldías", -34.57246, -58.40266],
      ["Ciudad Universitaria", -34.54307, -58.44777],
      ["Aristóbulo del Valle", -34.53755, -58.47794],
      ["Padilla", -34.54363, -58.50018],
      ["Florida", -34.53714, -58.51306],
      ["Munro", -34.53166, -58.52384],
      ["Carapachay", -34.5288, -58.53739],
      ["Villa Adelina", -34.52369, -58.54628],
      ["Boulogne Sur Mer", -34.50903, -58.56489],
      ["Vicealmirante Montes", -34.50416, -58.61948],
      ["Don Torcuato", -34.50263, -58.64249],
      ["Adolfo Sourdeaux", -34.50298, -58.66605],
      ["Villa de Mayo", -34.50337, -58.68071],
      ["Los Polvorines", -34.49878, -58.69504],
      ["Pablo Nogués", -34.49371, -58.70637],
      ["Grand Bourg", -34.48529, -58.72533],
      ["Tierras Altas", -34.47897, -58.73947],
      ["Tortuguitas", -34.47066, -58.758],
      ["Manuel Alberti", -34.46283, -58.77562],
      ["Del Viso", -34.45346, -58.79656],
      ["Cecilia Grierson", -34.43921, -58.82414],
      ["Villa Rosa", -34.41626, -58.87213],
    ],
  },
];
