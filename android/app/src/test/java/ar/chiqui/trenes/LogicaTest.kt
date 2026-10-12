package ar.chiqui.trenes

import ar.chiqui.trenes.datos.Actualizacion
import ar.chiqui.trenes.datos.CambioTren
import ar.chiqui.trenes.datos.Estacion
import ar.chiqui.trenes.datos.detectarCambioTren
import ar.chiqui.trenes.datos.LatLon
import ar.chiqui.trenes.datos.ordenarGeografico
import ar.chiqui.trenes.datos.zonasTorneo
import ar.chiqui.trenes.datos.Tren
import ar.chiqui.trenes.ui.cuentaRegresiva
import ar.chiqui.trenes.widget.faltanTexto
import ar.chiqui.trenes.widget.proximoTick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicaTest {
    private val t0 = 1_791_730_800_000L // 2026-10-11 15:00 UTC
    private fun en(seg: Double) = t0 + (seg * 1000).toLong()

    @Test
    fun trenSeguido_cambios() {
        val base = en(600.0)
        assertEquals(CambioTren.Desaparecio, detectarCambioTren(base, null, t0, 5))
        assertEquals(CambioTren.Demora(6), detectarCambioTren(base, en(960.0), t0, 5))
        assertEquals(CambioTren.Demora(5), detectarCambioTren(base, en(900.0), t0, 5))
        assertNull(detectarCambioTren(base, en(840.0), t0, 5))
        assertNull(detectarCambioTren(base, en(300.0), t0, 5)) // adelanto
        assertNull(detectarCambioTren(en(-30.0), null, t0, 5)) // ya pasó su hora
        assertNull(detectarCambioTren(en(30.0), null, t0, 5)) // llegando
        assertNull(detectarCambioTren(null, null, t0, 5))
    }

    @Test
    fun detectarCambioTren_adelantadoYPasoNoEsDesaparicion() {
        val base = en(600.0) // al activar: llegaba en 10 min
        // Se adelantó: la última vez venía en 30 s y después ya no aparece.
        assertNull(detectarCambioTren(base, null, t0, 5, ultimaVista = en(30.0)))
        // La última vez faltaban 5 min: sí desapareció.
        assertEquals(CambioTren.Desaparecio, detectarCambioTren(base, null, t0, 5, ultimaVista = en(300.0)))
        // La demora sigue midiéndose contra la base, no contra la última vista.
        assertEquals(CambioTren.Demora(6), detectarCambioTren(base, en(960.0), t0, 5, ultimaVista = en(900.0)))
    }

    @Test
    fun cuentaRegresiva_formatos() {
        fun chk(seg: Double, num: String, uni: String) {
            val c = cuentaRegresiva(en(seg), t0)
            assertEquals(num, c.num); assertEquals(uni, c.uni)
        }
        chk(65.0, "01:05", "min  seg")
        chk(65.9, "01:05", "min  seg")
        chk(3599.0, "59:59", "min  seg")
        chk(3723.0, "1:02:03", "h  min  seg")
        chk(0.0, "YA", "en andén")
        chk(-120.0, "YA", "en andén")
        assertTrue(cuentaRegresiva(en(0.0), t0).ya)
        val pasado = cuentaRegresiva(en(-121.0), t0)
        assertEquals("ya pasó", pasado.uni)
        assertTrue(pasado.num.matches(Regex("\\d\\d:\\d\\d")))
        val lejos = cuentaRegresiva(en(24 * 3600.0), t0)
        assertTrue(lejos.num.matches(Regex("\\d\\d:\\d\\d")))
        assertNotEquals("h  min  seg", lejos.uni)
    }

    @Test
    fun torneoChiqui_zonas() {
        val par = zonasTorneo(listOf("a", "b", "c", "d", "e", "f"))
        assertEquals(3, par.mitad)
        assertEquals(listOf("a", "c", "e"), par.zonaA)
        assertEquals(listOf("b", "d", "f"), par.zonaB)
        assertNull(par.escritorio)
        val impar = zonasTorneo(listOf("a", "b", "c", "d", "e"))
        assertEquals(2, impar.mitad)
        assertEquals(listOf("a", "c"), impar.zonaA)
        assertEquals(listOf("b", "d"), impar.zonaB)
        assertEquals("e", impar.escritorio)
        assertEquals("solo", zonasTorneo(listOf("solo")).escritorio)
        assertNull(zonasTorneo(emptyList<String>()).escritorio)
    }

    @Test
    fun ordenCabecera() {
        var id = 0
        fun est(nombre: String, lat: Double? = null, lon: Double? = null) =
            Estacion(id++, nombre, emptyList(), lat?.let { LatLon(it, lon!!) }, nombre.lowercase())
        val lista = listOf(
            est("Tigre", -34.4255, -58.5797),
            est("Zárate"),
            est("Retiro", -34.5913, -58.3747),
            est("Belgrano C", -34.5625, -58.4566),
            est("Álvarez"),
            est("San Isidro", -34.4712, -58.5085),
        )
        assertEquals(
            listOf("Retiro", "Belgrano C", "San Isidro", "Tigre", "Álvarez", "Zárate"),
            ordenarGeografico(lista).map { it.nombre },
        )
        assertEquals(listOf("A", "B"), ordenarGeografico(listOf(est("B"), est("A"))).map { it.nombre })
    }

    @Test
    fun widget_faltan() {
        assertEquals("ahora", faltanTexto(en(30.0), t0))
        assertEquals("ahora", faltanTexto(en(-30.0), t0))
        assertNull(faltanTexto(en(-61.0), t0))
        assertEquals("en 4 min", faltanTexto(en(299.0), t0))
        assertEquals("en 1 h 05", faltanTexto(en(3900.0), t0))
    }

    @Test
    fun widget_proximoTick() {
        fun tren(seg: Double) = Tren("c", "r", "o", "d", false, en(seg), null, false, null, null, null, null, null, null, null)
        assertEquals(t0 + 60000, proximoTick(emptyList(), t0))
        assertEquals(en(35.0), proximoTick(listOf(tren(30.0), tren(600.0)), t0))
        assertEquals(t0 + 15000, proximoTick(listOf(tren(1.0)), t0))
    }

    @Test
    fun actualizacion_numero() {
        assertEquals(204, Actualizacion.numero("v1.0.204"))
        assertEquals(7, Actualizacion.numero("v2.1.7"))
        assertNull(Actualizacion.numero("sin-numero"))
    }
}
