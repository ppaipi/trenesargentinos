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
  },
];
