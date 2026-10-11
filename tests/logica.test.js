import { test } from "node:test";
import assert from "node:assert/strict";
import { cuentaRegresiva, zonasTorneo, ordenarGeografico, detectarCambioTren } from "../js/v4/logica.js";

test("tren seguido: desaparece, suma demora, poca demora y ya pasó", () => {
  const base = en(10 * 60);
  assert.deepEqual(detectarCambioTren(base, null, T0, 5), { tipo: "desaparecio" });
  assert.deepEqual(detectarCambioTren(base, en(16 * 60), T0, 5), { tipo: "demora", sumaMin: 6 });
  assert.deepEqual(detectarCambioTren(base, en(15 * 60), T0, 5), { tipo: "demora", sumaMin: 5 });
  assert.equal(detectarCambioTren(base, en(14 * 60), T0, 5), null);
  assert.equal(detectarCambioTren(base, en(5 * 60), T0, 5), null); // adelanto
  assert.equal(detectarCambioTren(en(-30), null, T0, 5), null); // ya pasó su hora
  assert.equal(detectarCambioTren(en(30), null, T0, 5), null); // llegando
  assert.equal(detectarCambioTren(null, null, T0, 5), null);
});

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
