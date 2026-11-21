package com.personalfinances

import com.personalfinances.domain.Currency
import com.personalfinances.notifications.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {

    @Test fun bankPurchase() {
        val p = NotificationParser.parse("cl.bank", "Compra aprobada", "Compra por $12.990 en LIDER EXPRESS con tarjeta terminada en 4321 el 21/11")!!
        assertEquals(12_990L, p.amount)
        assertEquals(Currency.CLP, p.currency)
        assertEquals("LIDER EXPRESS", p.merchant)
        assertEquals("4321", p.lastDigits)
        assertFalse(p.isIncome)
    }

    @Test fun googleWallet() {
        val p = NotificationParser.parse(NotificationParser.GOOGLE_WALLET, "Starbucks", "CLP 4.500 con Visa ••••1234")!!
        assertEquals(4_500L, p.amount)
        assertEquals("Starbucks", p.merchant)
        assertEquals("1234", p.lastDigits)
    }

    @Test fun usdPurchase() {
        val p = NotificationParser.parse("cl.bank", "Compra internacional", "Realizaste una compra por US$ 15,99 en NETFLIX.COM")!!
        assertEquals(1_599L, p.amount)
        assertEquals(Currency.USD, p.currency)
        assertEquals("NETFLIX.COM", p.merchant)
    }

    @Test fun income() {
        val p = NotificationParser.parse("cl.wallet", "Transferencia", "Recibiste $150.000 de Juan Pérez")!!
        assertTrue(p.isIncome)
        assertEquals(150_000L, p.amount)
    }

    @Test fun ignoresOtp() {
        assertNull(NotificationParser.parse("cl.bank", "Clave", "Tu código para la compra de $10.000 es 123456"))
    }

    @Test fun ignoresNonTransactions() {
        assertNull(NotificationParser.parse("cl.bank", "Hola", "Tienes un nuevo mensaje"))
    }
}
