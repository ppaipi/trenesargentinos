import { test } from "node:test";
import assert from "node:assert/strict";
import { cuentaRegresiva, zonasTorneo, ordenarGeografico, proyectarEnLinea, progresoViaje } from "../js/v4/logica.js";

const T0 = Date.UTC(2026, 9, 11, 15, 0, 0);
const en = (seg) => new Date(T0 + seg * 1000);

test("cuenta regresiva: MM:SS, H:MM:SS, YA y pasados", () => {
  assert.deepEqual(cuentaRegresiva(en(65), T0), { num: "01:05", uni: "min  seg" });
  assert.deepEqual(cuentaRegresiva(en(65.9), T0), { num: "01:05", uni: "min  seg" }); // trunca
  assert.deepEqual(cuentaRegresiva(en(3599), T0), { num: "59:59", uni: "min  seg" });
  assert.deepEqual(cuentaRegresiva(en(3600 + 2 * 60 + 3), T0), { num: "1:02:03", uni: "h  min  seg" });
  assert.deepEqual(cuentaRegresiva(en(0), T0), { num: "YA", uni: "en andén", ya: true });
  assert.deepEqual(cuentaRegresiva(en(-120), T0), { num: "YA", uni: "en andén", ya: true });
  const pasado = cuentaRegresiva(en(-121), T0);
  assert.equal(pasado.uni, "ya pasó");
  assert.match(pasado.num, /^\d\d:\d\d$/);
  const lejos = cuentaRegresiva(en(24 * 3600), T0);
  assert.match(lejos.num, /^\d\d:\d\d$/);
  assert.notEqual(lejos.uni, "h  min  seg");
});

test("torneo Chiqui: zonas salteadas y ascenso por escritorio", () => {
  const par = zonasTorneo(["a", "b", "c", "d", "e", "f"]);
  assert.deepEqual(par, { mitad: 3, zonaA: ["a", "c", "e"], zonaB: ["b", "d", "f"], escritorio: undefined });
  const impar = zonasTorneo(["a", "b", "c", "d", "e"]);
  assert.deepEqual(impar, { mitad: 2, zonaA: ["a", "c"], zonaB: ["b", "d"], escritorio: "e" });
  assert.deepEqual(zonasTorneo(["solo"]), { mitad: 0, zonaA: [], zonaB: [], escritorio: "solo" });
  assert.deepEqual(zonasTorneo([]), { mitad: 0, zonaA: [], zonaB: [], escritorio: undefined });
});

test("orden de cabecera: desde la más cercana al centro hacia afuera, sin coords al final", () => {
  const est = (nombre, coords) => ({ nombre, coords });
  const lista = [
    est("Tigre", [-34.4255, -58.5797]),
    est("Zárate", null),
    est("Retiro", [-34.5913, -58.3747]),
    est("Belgrano C", [-34.5625, -58.4566]),
    est("Álvarez", null),
    est("San Isidro", [-34.4712, -58.5085]),
  ];
  const orden = ordenarGeografico(lista).map((e) => e.nombre);
  assert.deepEqual(orden, ["Retiro", "Belgrano C", "San Isidro", "Tigre", "Álvarez", "Zárate"]);
  assert.deepEqual(ordenarGeografico([est("B", null), est("A", null)]).map((e) => e.nombre), ["A", "B"]);
});

// Vía recta hacia el oeste con 4 paradas cada ~1 km (0.011° de longitud).
const via = [[-34.6, -58.4], [-34.6, -58.433]];
const paradasRectas = [0, 1, 2, 3].map((i) => ({
  nombre: `E${i}`,
  coords: [-34.6, -58.4 - 0.011 * i],
  hora: T0 + i * 120000,
}));

test("proyectarEnLinea: km sobre la vía y distancia a ella", () => {
  const r = proyectarEnLinea(via, [-34.601, -58.411]);
  assert.ok(Math.abs(r.km - 1.0) < 0.05, `km ${r.km}`);
  assert.ok(Math.abs(r.distKm - 0.11) < 0.02, `dist ${r.distKm}`);
  assert.equal(proyectarEnLinea([], [-34.6, -58.4]), null);
});

test("progresoViaje por GPS: última pasada, siguiente y llegada", () => {
  const entre12 = progresoViaje(paradasRectas, { pos: [-34.6, -58.4165], linea: via, ahora: T0 });
  assert.equal(entre12.fuente, "gps");
  assert.equal(entre12.ultima, 1);
  assert.equal(entre12.siguiente, 2);
  assert.equal(entre12.enEstacion, false);
  const enE2 = progresoViaje(paradasRectas, { pos: [-34.6, -58.4219], linea: via });
  assert.equal(enE2.ultima, 2);
  assert.equal(enE2.enEstacion, true);
  const destino = progresoViaje(paradasRectas, { pos: [-34.6, -58.433], linea: via });
  assert.equal(destino.llego, true);
  assert.equal(destino.siguiente, null);
  // Sentido contrario: las paradas recorren la vía al revés.
  const alReves = progresoViaje([...paradasRectas].reverse(), { pos: [-34.6, -58.4165], linea: via });
  assert.equal(alReves.ultima, 1);
  // Sin recorrido: une las paradas.
  assert.equal(progresoViaje(paradasRectas, { pos: [-34.6, -58.4165] }).ultima, 1);
});

test("progresoViaje estimado: sin GPS o lejos de la vía usa las horas", () => {
  const sinGps = progresoViaje(paradasRectas, { ahora: T0 + 130000 });
  assert.equal(sinGps.fuente, "estimado");
  assert.equal(sinGps.ultima, 1);
  assert.equal(sinGps.enEstacion, true);
  const lejos = progresoViaje(paradasRectas, { pos: [-34.7, -58.41], linea: via, ahora: T0 - 1000 });
  assert.equal(lejos.fuente, "estimado");
  assert.equal(lejos.ultima, -1);
  assert.equal(lejos.siguiente, 0);
});
