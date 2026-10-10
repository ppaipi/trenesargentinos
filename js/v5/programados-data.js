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
    frecuenciaMin: null, // Metrovías no publica frecuencia por día
    fuente: {
      url: "https://metrovias.com.ar/index.php/horarios-del-servicio-linea-urquiza/",
      nombre: "Metrovías",
    },
    vigencia: null,
  },
  {
    id: "belgrano-norte",
    linea: "Belgrano Norte",
    operador: "Ferrovías",
    color: "var(--linea-belgranonorte)",
    cabeceras: ["Retiro", "Villa Rosa"],
    trayectoMin: null,
    // Ferrovías publica el detalle solo en PDF (buscador); cabeceras no confiables
    // entre fuentes periodísticas → no se cargan horas de primer/último.
    horarios: null,
    // Frecuencia aproximada del cronograma vigente desde 19/05/2026 (prensa, no oficial):
    frecuenciaMin: { habiles: "18-20 (pico)", sabados: "~20", domingos: "~45" },
    fuente: {
      url: "https://ferrovias.com.ar/horarios/",
      nombre: "Ferrovías",
    },
    vigencia: "18/05/2026",
  },
];
