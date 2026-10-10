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
  const decir = (texto) => {
    globo.textContent = texto;
    globo.classList.remove("ocultar");
    clearTimeout(m.timer);
    m.timer = setTimeout(() => globo.classList.add("ocultar"), 4500);
  };
  // Se escapa del mouse y cada tanto opina sin que nadie le pregunte.
  let saltos = 0;
  m.addEventListener("mouseenter", () => {
    if (++saltos % 3 === 0) return;
    m.style.right = `${16 + Math.random() * (innerWidth - 100)}px`;
    m.style.bottom = `${90 + Math.random() * (innerHeight * 0.5)}px`;
    decir(azar(["¡No me agarrás!", "Primero cotizá", "Jajaja casi"]));
  });
  m.addEventListener("click", () => decir(azar(SLOGANS)));
  setInterval(() => !document.hidden && decir(azar(SLOGANS)), 25000);
  document.body.append(m, globo);
}

// Cartel de entrada a pantalla completa: el boton de cerrar tarda en habilitarse.
function interstitial(segundos) {
  const fondo = el("div", "sancord-inter");
  const caja = el("div", "sancord-inter-caja");
  caja.append(el("span", "sancord-tag", "Publicidad"), logo(), el("p", "sancord-inter-texto", azar(SLOGANS)));
  const tapia = el("div", "tapia-inter");
  tapia.append(caraTapia(), el("span", null, `“${FRASE_TAPIA}”`));
  caja.append(tapia);
  const cerrar = el("button", "sancord-inter-cerrar", `Saltar anuncio en ${segundos}`);
  cerrar.type = "button";
  cerrar.disabled = true;
  const cta = el("button", "sancord-cta", "¡Quiero mi póliza!");
  cta.type = "button";
  cta.addEventListener("click", () => (cta.textContent = azar(["Un asesor te llama en 3 a 5 siglos", "Póliza aprobada: cubre nada"])));
  caja.append(cta, cerrar);
  fondo.append(caja);
  document.body.append(fondo);
  let n = segundos;
  const t = setInterval(() => {
    n--;
    if (n > 0) cerrar.textContent = `Saltar anuncio en ${n}`;
    else {
      clearInterval(t);
      cerrar.disabled = false;
      cerrar.textContent = "Cerrar ✕";
    }
  }, 1000);
  cerrar.addEventListener("click", () => fondo.remove());
}

// Banner fijo abajo con una "X" que no siempre cierra.
function bannerFijo() {
  const b = el("aside", "sancord-fijo");
  const texto = el("span", "sancord-fijo-texto", azar(SLOGANS));
  const x = el("button", "sancord-fijo-x", "✕");
  x.type = "button";
  x.setAttribute("aria-label", "Cerrar anuncio");
  b.append(logo(), texto, x);
  let intentos = 0;
  x.addEventListener("click", () => {
    intentos++;
    if (intentos === 1) {
      texto.textContent = "Jaja no. Probá de nuevo.";
      x.style.order = "-1";
      return;
    }
    b.classList.add("ocultar");
    setTimeout(() => {
      intentos = 0;
      x.style.order = "";
      texto.textContent = "Volvimos. Te extrañábamos.";
      b.classList.remove("ocultar");
    }, 20000);
  });
  setInterval(() => intentos === 0 && (texto.textContent = azar(SLOGANS)), 7000);
  document.body.append(b);
}

// Con la pestaña en segundo plano, el titulo titila.
function tituloInsistente() {
  const original = document.title;
  let timer = null;
  document.addEventListener("visibilitychange", () => {
    clearInterval(timer);
    if (!document.hidden) {
      document.title = original;
      return;
    }
    let alterna = false;
    timer = setInterval(() => {
      alterna = !alterna;
      document.title = alterna ? "¡Volvé! Sancord te espera" : original;
    }, 1200);
  });
}

/* ---------- Chiqui Tapia (meme) ---------- */

const FRASE_TAPIA = "No trates de entenderla, disfrutala";
const FRASES_TAPIA = [
  FRASE_TAPIA,
  "¿El tren no llega? No trates de entenderlo, disfrutalo.",
  "Te estoy mirando. Subí al tren.",
  "Esta app no se explica, se siente.",
];

// La foto la pone el dueño del sitio en img/tapia.png. Si no esta, el meme
// sale igual pero solo con texto.
let fotoTapia = null;
const cargaTapia = new Promise((ok) => {
  const img = new Image();
  img.onload = () => {
    fotoTapia = img.src;
    ok(true);
  };
  img.onerror = () => ok(false);
  img.src = "img/tapia.png";
});

function caraTapia(clase) {
  const cara = el("div", `tapia-cara ${clase || ""}`);
  if (fotoTapia) cara.style.backgroundImage = `url("${fotoTapia}")`;
  else cara.classList.add("sin-foto");
  return cara;
}

// Se asoma desde el costado de la pantalla, te mira, dice algo y se va.
function tapiaQueSeAsoma() {
  const caja = el("div", "tapia-asoma");
  const globo = el("div", "tapia-globo");
  caja.append(caraTapia(), globo);
  document.body.append(caja);
  const asomarse = () => {
    if (document.hidden) return;
    globo.textContent = azar(FRASES_TAPIA);
    caja.classList.toggle("derecha", Math.random() < 0.5);
    caja.classList.add("visible");
    setTimeout(() => caja.classList.remove("visible"), 5000);
  };
  setTimeout(asomarse, 9000);
  setInterval(asomarse, 35000);
  caja.addEventListener("click", () => caja.classList.remove("visible"));
}

function tarjetaTapia() {
  const a = el("aside", "sancord-anuncio tapia-anuncio");
  a.append(el("span", "sancord-tag", "Mensaje institucional"), caraTapia("grande"));
  const texto = el("p", "tapia-frase", `“${FRASE_TAPIA}”`);
  a.append(texto, el("small", null, "— el Chiqui, sobre los horarios del Roca"));
  return a;
}

const anuncios = [];
let tarjetaDeTapia = null;
let cargado = 0;

document.addEventListener("trenes:listo", () => {
  cargado = Date.now();
  document.querySelector(".cabecera").after(banner());
  const status = document.getElementById("loginStatus");
  if (status && !status.classList.contains("ocultar")) {
    status.append(el("small", "sancord-mini", " · asegurado por Sancord"));
  }
  document.querySelector(".pie").prepend(
    el(
      "p",
      "sancord-disclaimer",
      "Patrocinio de mentira, es un meme. Ni Sancor Seguros ni el Chiqui Tapia tienen nada que ver con esta página (ni nos pagaron un cospel)."
    )
  );
  mascota();
  bannerFijo();
  tituloInsistente();
  cargaTapia.then(() => {
    interstitial(5);
    tapiaQueSeAsoma();
  });
});

document.addEventListener("trenes:busqueda", ({ detail }) => {
  anuncios.splice(0).forEach((a) => a.remove());
  tarjetaDeTapia?.remove();
  tarjetaDeTapia = null;
  document.getElementById("estadoActualizacion").dataset.sancord =
    detail.modo === "vivo" ? "Monitoreado por Sancord Seguros™" : "";
  // Cada busqueda (salvo la automatica al abrir) trae su anuncio de 3 segundos.
  if (Date.now() - cargado > 4000) interstitial(3);
});

document.addEventListener("trenes:resultados", ({ detail }) => {
  const result = document.getElementById("result");
  const tarjetas = [...result.querySelectorAll(".horario")];
  const demorado = detail.trenes.find((t) => t.demoraMin >= 5);

  // Un anuncio cada 2 trenes. app.js reordena las tarjetas en cada sondeo,
  // asi que los reubicamos siempre.
  const necesarios = Math.max(1, Math.floor(tarjetas.length / 2));
  while (anuncios.length < necesarios) {
    const i = anuncios.length;
    anuncios.push(
      tarjetaAnuncio(i === 0 && demorado ? azar(MENSAJES_DEMORA)(demorado.demoraMin) : azar(SLOGANS))
    );
  }
  anuncios.forEach((a, i) => {
    const ancla = tarjetas[i * 2 + 1];
    if (ancla) ancla.nextElementSibling !== a && ancla.after(a);
    else if (i === 0) result.append(a);
    else a.remove();
  });

  if (!tarjetaDeTapia) tarjetaDeTapia = tarjetaTapia();
  const primera = tarjetas[0];
  if (primera && primera.nextElementSibling !== tarjetaDeTapia) primera.after(tarjetaDeTapia);
  else if (!primera) result.prepend(tarjetaDeTapia);

  if (detail.trenes.length === 0) {
    anuncios[0].querySelector("p").textContent =
      "¿No hay trenes? Con Sancord Seguros tampoco, pero te sentís acompañado.";
  }
});
