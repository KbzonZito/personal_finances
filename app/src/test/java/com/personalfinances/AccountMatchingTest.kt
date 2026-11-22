package com.personalfinances

import com.personalfinances.data.AccountMatcher
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.SourceRuleEntity
import com.personalfinances.notifications.CardKind
import com.personalfinances.notifications.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountMatchingTest {
    private val checking = AccountEntity(1, "Cuenta corriente", AccountType.CHECKING, lastDigits = "1111,2222")
    private val visa = AccountEntity(2, "Visa", AccountType.CREDIT_CARD, lastDigits = "4321")
    private val master = AccountEntity(3, "Mastercard", AccountType.CREDIT_CARD)
    private val tenpo = AccountEntity(4, "Tenpo", AccountType.YIELD)
    private val all = listOf(checking, visa, master, tenpo)
    private val bank = "cl.bank"

    @Test fun parserDetectsCardKind() {
        assertEquals(CardKind.CREDIT, NotificationParser.parse(bank, "Compra", "Compra con tu Tarjeta de Crédito por $10.000 en JUMBO")!!.cardKind)
        assertEquals(CardKind.DEBIT, NotificationParser.parse(bank, "Compra", "Compra con tarjeta de débito por $5.000 en COPEC")!!.cardKind)
        assertEquals(CardKind.DEBIT, NotificationParser.parse(bank, "Cargo", "Cargo en Cuenta Corriente por $20.000")!!.cardKind)
        assertNull(NotificationParser.parse(bank, "Compra", "Compra por $5.000 en COPEC")!!.cardKind)
    }

    @Test fun digitsWinIncludingSecondCardOfAnAccount() {
        assertEquals(1L, AccountMatcher.suggest(all, emptyList(), bank, "2222", CardKind.CREDIT))
        assertEquals(2L, AccountMatcher.suggest(all, emptyList(), bank, "4321", null))
    }

    @Test fun kindUsesLearnedRuleOrSingleCandidate() {
        // Débito: la única cuenta corriente.
        assertEquals(1L, AccountMatcher.suggest(all, emptyList(), bank, null, CardKind.DEBIT))
        // Crédito: hay dos tarjetas → sin regla no adivina…
        assertNull(AccountMatcher.suggest(all, emptyList(), bank, null, CardKind.CREDIT))
        // …pero aprende cuál usaste para ese banco con crédito.
        val rules = listOf(SourceRuleEntity(packageName = bank, lastDigits = "k:CREDIT", accountId = 3))
        assertEquals(3L, AccountMatcher.suggest(all, rules, bank, null, CardKind.CREDIT))
        // Una regla general del banco que apunta a la corriente no se usa para una compra con crédito.
        val general = listOf(SourceRuleEntity(packageName = bank, lastDigits = "", accountId = 1))
        assertNull(AccountMatcher.suggest(all, general, bank, null, CardKind.CREDIT))
        assertEquals(1L, AccountMatcher.suggest(all, general, bank, null, null))
    }

    @Test fun archivedAccountsAreIgnored() {
        val archived = all.map { if (it.id == 1L) it.copy(archived = true) else it }
        assertEquals(4L, AccountMatcher.suggest(archived, emptyList(), bank, null, CardKind.DEBIT))
    }
}
