package com.personalfinances

import com.personalfinances.data.db.ParserRuleEntity
import com.personalfinances.domain.Currency
import com.personalfinances.notifications.NotificationParser
import com.personalfinances.notifications.RuleOutcome
import com.personalfinances.notifications.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachParserTest {
    private val pkg = "cl.raro"

    @Test fun signatureIgnoresDigitsAndPunctuation() {
        val a = NotificationParser.signature(NotificationParser.fullText("Movimiento 123", "Se registró $5.000 en X"))
        val b = NotificationParser.signature(NotificationParser.fullText("Movimiento 987", "Se registró $8.990 en Y"))
        assertEquals(a, b)
    }

    @Test fun taughtRuleReadsAmountAndMerchant() {
        // Formato que la heurística no entiende (no dice "compra"/"pago").
        val title = "Movimiento"
        val text = "Se registró saldo $1.000 y luego $12.990 para LA FERIA SPA hoy"
        assertNull(NotificationParser.parse(pkg, title, text))
        val sig = NotificationParser.signature(NotificationParser.fullText(title, text))
        val rule = ParserRuleEntity(packageName = pkg, signature = sig, kind = "EXPENSE", amountOrdinal = 1, merchantPrefix = "para", merchantSuffix = "hoy")
        val p = NotificationParser.parse(pkg, title, "Se registró saldo $2.000 y luego $4.500 para DON PEPE hoy", Currency.CLP, listOf(rule))!!
        assertEquals(4_500L, p.amount)
        assertEquals("DON PEPE", p.merchant)
        assertEquals(TxKind.EXPENSE, p.kind)
    }

    @Test fun ignoreRuleSilencesThatKindOfNotification() {
        val title = "Compra"; val text = "Compra por $9.990 en NETFLIX"
        val sig = NotificationParser.signature(NotificationParser.fullText(title, text))
        val rules = listOf(ParserRuleEntity(packageName = pkg, signature = sig, kind = "IGNORE"))
        assertEquals(RuleOutcome.Ignore, NotificationParser.applyRules(pkg, title, text, rules, Currency.CLP))
        assertNull(NotificationParser.parse(pkg, title, text, Currency.CLP, rules))
        // Otra app no se ve afectada.
        assertTrue(NotificationParser.parse("otra.app", title, text, Currency.CLP, rules) != null)
    }

    @Test fun detectsTransfersAndCardPayments() {
        assertEquals(TxKind.TRANSFER, NotificationParser.parse(pkg, "Transferencia enviada", "Transferiste $50.000 a Juan Pérez")!!.kind)
        assertEquals(TxKind.CARD_PAYMENT, NotificationParser.parse(pkg, "Pago recibido", "Realizaste un pago de tu tarjeta de crédito por $200.000")!!.kind)
        assertEquals(TxKind.EXPENSE, NotificationParser.parse(pkg, "Compra", "Compra por $3.000 en COPEC")!!.kind)
        assertEquals(TxKind.INCOME, NotificationParser.parse(pkg, "Transferencia", "Recibiste $10.000 de Ana")!!.kind)
    }
}
