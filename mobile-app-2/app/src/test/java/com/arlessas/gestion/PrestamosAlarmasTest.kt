package com.arlessas.gestion

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrestamosAlarmasTest {
    private val zona = ZoneId.of("America/Bogota")

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zona).toInstant().toEpochMilli()

    @Test
    fun domingoAntesDeLasSeisVenceEseDia() {
        assertEquals(epoch(2026, 7, 12, 12, 30), calcularLimitePrestamoEpoch(epoch(2026, 7, 12, 5, 15), zona))
    }

    @Test
    fun domingoDuranteJornadaVenceEseDia() {
        assertEquals(epoch(2026, 7, 12, 12, 30), calcularLimitePrestamoEpoch(epoch(2026, 7, 12, 9, 0), zona))
    }

    @Test
    fun domingoDespuesDelLimiteVenceAlDiaSiguiente() {
        assertEquals(epoch(2026, 7, 13, 12, 30), calcularLimitePrestamoEpoch(epoch(2026, 7, 12, 13, 0), zona))
    }

    @Test
    fun sabadoTambienUsaElMismoHorario() {
        assertEquals(epoch(2026, 7, 11, 12, 30), calcularLimitePrestamoEpoch(epoch(2026, 7, 11, 8, 0), zona))
    }

    @Test
    fun exactamenteDoceYTreintaVenceEnEseInstante() {
        val instante = epoch(2026, 7, 12, 12, 30)
        assertEquals(instante, calcularLimitePrestamoEpoch(instante, zona))
    }

    @Test
    fun proximaRevisionCambiaDeDiaDespuesDelLimite() {
        assertEquals(epoch(2026, 7, 13, 12, 30), proximaRevisionDiariaEpoch(epoch(2026, 7, 12, 12, 31), zona))
    }

    @Test
    fun ampliacionDeTreintaMinutosParteDelMayorPlazo() {
        val plazo = epoch(2026, 7, 12, 12, 30)
        assertEquals(epoch(2026, 7, 12, 13, 0), calcularAmpliacionRapidaEpoch(plazo, epoch(2026, 7, 12, 12, 0), 30))
    }

    @Test
    fun ampliacionPuedePasarAlDiaSiguiente() {
        val plazo = epoch(2026, 7, 12, 23, 30)
        assertEquals(epoch(2026, 7, 13, 1, 30), calcularAmpliacionRapidaEpoch(plazo, epoch(2026, 7, 12, 23, 0), 120))
    }

    @Test
    fun fechaPasadaONoPosteriorEsInvalida() {
        val ahora = epoch(2026, 7, 12, 13, 0)
        val plazo = epoch(2026, 7, 12, 14, 0)
        assertFalse(esFechaAmpliacionValida(epoch(2026, 7, 12, 12, 0), plazo, ahora))
        assertFalse(esFechaAmpliacionValida(plazo, plazo, ahora))
        assertTrue(esFechaAmpliacionValida(epoch(2026, 7, 12, 15, 0), plazo, ahora))
    }

    @Test
    fun sinPrestamosNoProduceGrupos() {
        assertTrue(agruparPrestamosParaAlarma(emptyList(), epoch(2026, 7, 12, 12, 30)).isEmpty())
    }

    @Test
    fun devueltoYConsumibleNoGeneranAlarma() {
        val base = elemento("A", "ASG-1", 0.0)
        val consumible = elemento("B", "ASG-2", 1.0).copy(esConsumible = true)
        assertTrue(agruparPrestamosParaAlarma(listOf(base, consumible), epoch(2026, 7, 12, 12, 30)).isEmpty())
    }

    @Test
    fun vehiculoEImplementosCompartenUnaNotificacion() {
        val elementos = listOf(
            elemento("vehiculo", "VEH-123", 1.0),
            elemento("implemento-1", "VEH-123", 1.0),
            elemento("implemento-2", "VEH-123", 1.0),
        )
        val grupos = agruparPrestamosParaAlarma(elementos, epoch(2026, 7, 12, 12, 30))
        assertEquals(1, grupos.size)
        assertEquals(3, grupos.single().elementos.size)
    }

    @Test
    fun claveDeAvisoEsEstableYCambiaConLaVentana() {
        val primera = claveAvisoPrestamo("ASG-1", 100L)
        assertEquals(primera, claveAvisoPrestamo("ASG-1", 100L))
        assertFalse(primera == claveAvisoPrestamo("ASG-1", 200L))
    }

    private fun elemento(documento: String, asignacion: String, ocupados: Double): PrestamoAlarmaElemento {
        return PrestamoAlarmaElemento(
            documentoId = documento,
            asignacionId = asignacion,
            nombre = documento,
            codigo = documento,
            submodulo = "VEHICULOS",
            asignadoA = "Carlos",
            cantidadOcupada = ocupados,
            fechaSalida = "2026-07-12 08:00",
            fechaLimiteEpochMs = epoch(2026, 7, 12, 12, 30),
            esConsumible = false,
            alarmaActiva = true,
        )
    }
}
