// Patrocinio en joda de "Sancord Seguros". Se engancha a los eventos que
// emite app.js (trenes:listo, trenes:busqueda, trenes:resultados) sin tocar
// la logica de la app.

const SLOGANS = [
  "Asegurá tu llegada. La del tren no, eso ni Dios.",
  "Cubrimos todo menos la demora del Roca.",
  "Si el tren no llega, al menos vos llegás a fin de mes. Bah, tampoco.",
  "Sancord Seguros: más puntuales que el Sarmiento (no es difícil).",
  "Póliza “Andén Total”: te cubre de pie, sentado y apretado.",
  "Ahora con cobertura contra el “se demora por causas ajenas”.",
  "Lo único que llega a horario es la cuota.",
  "¿Te pisaron en el Mitre? Llamá al 0800-SANCORD. No atiende nadie, pero llamá.",
  "Seguro de vida, auto, hogar y paciencia ferroviaria.",
  "Sancord Seguros. Porque “ya viene” no es un plan.",
];

const MENSAJES_DEMORA = [
  (m) => `¿+${m} min de demora? Sancord te cubre la paciencia hasta $0,00.`,
  (m) => `${m} minutos tarde. Con Sancord Seguros los perdés igual, pero asegurado.`,
  (m) => `Demora de ${m} min detectada. Sancord ya está preparando el formulario 47-B.`,
];

const ESCUDO =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2 4 5v6c0 5 3.4 9.4 8 11 4.6-1.6 8-6 8-11V5z" fill="currentColor"/><path d="m8.5 12 2.5 2.5 4.5-5" fill="none" stroke="#ffd23f" stroke-width="2.2"/></svg>';

const azar = (lista) => lista[Math.floor(Math.random() * lista.length)];

function el(tag, clase, texto) {
  const e = document.createElement(tag);
  if (clase) e.className = clase;
  if (texto != null) e.textContent = texto;
  return e;
}

// Si existe img/patrocinador.png se usa esa imagen; si no, el logo de texto.
function logo() {
  const l = el("span", "sancord-logo");
  const img = new Image();
  img.className = "sancord-img";
  img.alt = "Patrocinador";
  img.onload = () => l.replaceChildren(img);
  img.src = "img/patrocinador.png";
  const escudo = el("span", "sancord-escudo");
  escudo.innerHTML = ESCUDO;
  l.append(escudo, el("span", null, "SANCORD"), el("small", null, "SEGUROS"));
  return l;
}

function banner() {
  const b = el("aside", "sancord-banner");
  b.setAttribute("aria-label", "Patrocinio");
  const texto = el("div", "sancord-banner-texto");
  texto.append(el("span", "sancord-presenta", "Este horario te lo trae"), logo());
  const slogan = el("p", "sancord-slogan", azar(SLOGANS));
  b.append(texto, slogan);
  // Rota el slogan cada tanto, como buen anuncio molesto.
  setInterval(() => {
    slogan.classList.remove("entra");
    void slogan.offsetWidth;
    slogan.textContent = azar(SLOGANS);
    slogan.classList.add("entra");
  }, 9000);
  return b;
}

function tarjetaAnuncio(texto) {
  const a = el("aside", "sancord-anuncio");
  a.append(el("span", "sancord-tag", "Publicidad"), logo(), el("p", null, texto));
  const cta = el("button", "sancord-cta", "Cotizar ahora (no)");
  cta.type = "button";
  cta.addEventListener("click", () => {
    cta.textContent = azar(["Cotización: un ojo de la cara", "Un asesor te llama en 3 a 5 siglos", "Gracias por nada"]);
  });
  a.append(cta);
  return a;
}

function mascota() {
  const m = el("button", "sancord-mascota");
  m.innerHTML = ESCUDO;
  m.type = "button";
  m.title = "Sancord Seguros";
  m.setAttribute("aria-label", "Mensaje de Sancord Seguros");
  const globo = el("div", "sancord-globo ocultar");
  m.addEventListener("click", () => {
    globo.textContent = azar(SLOGANS);
    globo.classList.remove("ocultar");
    clearTimeout(m.timer);
    m.timer = setTimeout(() => globo.classList.add("ocultar"), 4500);
  });
  document.body.append(m, globo);
}

let anuncio = null;

document.addEventListener("trenes:listo", () => {
  document.querySelector(".cabecera").after(banner());
  const status = document.getElementById("loginStatus");
  if (status && !status.classList.contains("ocultar")) {
    status.append(el("small", "sancord-mini", " · asegurado por Sancord"));
  }
  const pie = document.querySelector(".pie");
  pie.prepend(
    el(
      "p",
      "sancord-disclaimer",
      "Patrocinio de mentira, es un meme. Sancord Seguros no nos pagó ni un cospel."
    )
  );
  mascota();
});

document.addEventListener("trenes:busqueda", ({ detail }) => {
  anuncio?.remove();
  anuncio = null;
  document.getElementById("estadoActualizacion").dataset.sancord =
    detail.modo === "vivo" ? "Monitoreado por Sancord Seguros™" : "";
});

document.addEventListener("trenes:resultados", ({ detail }) => {
  const result = document.getElementById("result");
  const demorado = detail.trenes.find((t) => t.demoraMin >= 5);

  if (!anuncio) {
    anuncio = tarjetaAnuncio(demorado ? azar(MENSAJES_DEMORA)(demorado.demoraMin) : azar(SLOGANS));
  }
  // app.js reordena las tarjetas en cada sondeo: reubicamos el anuncio
  // despues del segundo tren (o al final si hay menos).
  const tarjetas = result.querySelectorAll(".horario");
  const ancla = tarjetas[1] || tarjetas[0];
  if (ancla) ancla.nextElementSibling !== anuncio && ancla.after(anuncio);
  else result.append(anuncio);

  if (detail.trenes.length === 0) {
    anuncio.querySelector("p").textContent =
      "¿No hay trenes? Con Sancord Seguros tampoco, pero te sentís acompañado.";
  }
});
