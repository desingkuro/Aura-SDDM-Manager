package com.example.aura.data.repository

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SddmPkexecTest {

    @Test
    fun `distingue una cancelacion de autenticacion`() {
        val message = describePkexecFailure(126, "")

        assertTrue(message.contains("Autenticación cancelada"))
        assertFalse(message.contains("autorizó"))
    }

    @Test
    fun `avisa cuando no hay agente Polkit`() {
        val message = describePkexecFailure(125, "")

        assertTrue(message.contains("agente Polkit"))
    }

    @Test
    fun `distingue una autorizacion denegada`() {
        val message = describePkexecFailure(127, "")

        assertTrue(message.contains("No se autorizó"))
    }

    @Test
    fun `incluye el detalle de pkexec en otros errores`() {
        val message = describePkexecFailure(1, "fallo de prueba")

        assertTrue(message.contains("código 1"))
        assertTrue(message.contains("fallo de prueba"))
    }
}
