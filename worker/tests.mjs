import { test } from "node:test";
import assert from "node:assert/strict";
import { parseReminder } from "./_worker.js";

test("mañana a las 9 → día siguiente 09:00", () => {
  const r = parseReminder("Llamar a mamá mañana a las 9");
  assert.equal(r.title, "Llamar a mamá");
  const now = new Date();
  const expected = new Date(now);
  expected.setDate(expected.getDate() + 1);
  expected.setHours(9, 0, 0, 0);
  assert.equal(r.date.getTime(), expected.getTime());
});

test("hoy 21:30 → hoy 21:30", () => {
  const r = parseReminder("Ordenar la casa hoy 21:30");
  assert.equal(r.title, "Ordenar la casa");
  const now = new Date();
  const expected = new Date(now);
  expected.setHours(21, 30, 0, 0);
  if (expected.getTime() <= now.getTime()) expected.setDate(expected.getDate() + 1);
  assert.equal(r.date.getTime(), expected.getTime());
});

test("invitación lunes a las 10 → próximo lunes 10:00", () => {
  const now = new Date();
  const r = parseReminder("Reunión de trabajo lunes a las 10");
  assert.equal(r.title, "Reunión de trabajo");
  const target = (1 - now.getDay() + 7) % 7;
  const dayOffset = target === 0 ? 7 : target;
  const expected = new Date(now);
  expected.setDate(expected.getDate() + dayOffset);
  expected.setHours(10, 0, 0, 0);
  assert.equal(r.date.getTime(), expected.getTime());
});

test("sin fecha → error guía", () => {
  const r = parseReminder("Comprar leche");
  assert.ok(r.error);
  assert.match(r.error, /mañana a las 9/);
});

test("sin título → error título", () => {
  const r = parseReminder("mañana a las 9");
  assert.ok(!r.title);
  assert.match(r.error, /título/);
});

test("recordame sacar la basura mañana a las 21 pm → título limpio", () => {
  const r = parseReminder("recordame sacar la basura mañana a las 21:00");
  assert.equal(r.title, "Sacar la basura");
  const now = new Date();
  const expected = new Date(now);
  expected.setDate(expected.getDate() + 1);
  expected.setHours(21, 0, 0, 0);
  assert.equal(r.date.getTime(), expected.getTime());
});

test("en 2 horas → ahora +2h", () => {
  const r = parseReminder("Avísame en 2 horas volver a casa");
  assert.equal(r.title, "Volver a casa");
  const now = new Date();
  const expected = new Date(now);
  expected.setHours(now.getHours() + 2);
  assert.equal(r.date.getTime(), expected.getTime(), 2000);
});

test("pasado mañana a las 19 → +2 días 19:00", () => {
  const r = parseReminder("Gimnasio pasado mañana a las 19");
  assert.equal(r.title, "Gimnasio");
  const now = new Date();
  const expected = new Date(now);
  expected.setDate(expected.getDate() + 2);
  expected.setHours(19, 0, 0, 0);
  assert.equal(r.date.getTime(), expected.getTime());
});

test("Hoy 3:15 pm veterinario → título y hora correctos", () => {
  const r = parseReminder("Hoy 3:15 pm veterinario");
  assert.equal(r.title, "Veterinario");
  const now = new Date();
  const expected = new Date(now);
  expected.setHours(15, 15, 0, 0);
  if (expected.getTime() <= now.getTime()) expected.setDate(expected.getDate() + 1);
  assert.equal(r.date.getTime(), expected.getTime());
});

test("Recordá la reunión el viernes a las 14:30 → título sin artículos sueltos", () => {
  const r = parseReminder("Recordá la reunión el viernes a las 14:30");
  assert.equal(r.title, "Reunión");
  const now = new Date();
  const target = (5 - now.getDay() + 7) % 7;
  const dayOffset = target === 0 ? 7 : target;
  const expected = new Date(now);
  expected.setDate(expected.getDate() + dayOffset);
  expected.setHours(14, 30, 0, 0);
  assert.equal(r.date.getTime(), expected.getTime());
});