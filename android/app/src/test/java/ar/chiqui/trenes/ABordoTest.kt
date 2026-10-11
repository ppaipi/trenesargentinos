package ar.chiqui.trenes

import ar.chiqui.trenes.datos.Fuente
import ar.chiqui.trenes.datos.LatLon
import ar.chiqui.trenes.datos.ParadaViaje
import ar.chiqui.trenes.datos.progresoViaje
import ar.chiqui.trenes.datos.proyectarEnLinea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Mismos casos que tests/logica.test.js (web).
class ABordoTest {
    private val t0 = 1_791_730_800_000L
    private val via = listOf(LatLon(-34.6, -58.4), LatLon(-34.6, -58.433))
    private val paradas = (0..3).map { i -> ParadaViaje(i, "E$i", LatLon(-34.6, -58.4 - 0.011 * i), t0 + i * 120_000L) }

    @Test
    fun proyeccion() {
        val r = proyectarEnLinea(via, LatLon(-34.601, -58.411))!!
        assertEquals(1.0, r.km, 0.05)
        assertEquals(0.11, r.distKm, 0.02)
        assertNull(proyectarEnLinea(emptyList(), LatLon(-34.6, -58.4)))
    }

    @Test
    fun porGps() {
        val entre = progresoViaje(paradas, LatLon(-34.6, -58.4165), via, t0)
        assertEquals(Fuente.GPS, entre.fuente)
        assertEquals(1, entre.ultima)
        assertEquals(2, entre.siguiente)
        assertFalse(entre.enEstacion)
        val enE2 = progresoViaje(paradas, LatLon(-34.6, -58.4219), via, t0)
        assertEquals(2, enE2.ultima)
        assertTrue(enE2.enEstacion)
        val destino = progresoViaje(paradas, LatLon(-34.6, -58.433), via, t0)
        assertTrue(destino.llego)
        assertNull(destino.siguiente)
        assertEquals(1, progresoViaje(paradas.reversed(), LatLon(-34.6, -58.4165), via, t0).ultima)
        assertEquals(1, progresoViaje(paradas, LatLon(-34.6, -58.4165), null, t0).ultima)
    }

    @Test
    fun estimado() {
        val sinGps = progresoViaje(paradas, null, null, t0 + 130_000)
        assertEquals(Fuente.ESTIMADO, sinGps.fuente)
        assertEquals(1, sinGps.ultima)
        assertTrue(sinGps.enEstacion)
        val lejos = progresoViaje(paradas, LatLon(-34.7, -58.41), via, t0 - 1000)
        assertEquals(Fuente.ESTIMADO, lejos.fuente)
        assertEquals(-1, lejos.ultima)
        assertEquals(0, lejos.siguiente)
    }
}
