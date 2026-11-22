package com.personalfinances.data

import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.SourceRuleEntity
import com.personalfinances.notifications.CardKind

/**
 * Elige la cuenta más probable para una compra detectada. En orden:
 * 1. últimos 4 dígitos configurados en una cuenta;
 * 2. lo aprendido para esa app + esos dígitos;
 * 3. tipo de tarjeta ("crédito"/"débito") + lo aprendido para esa app con ese tipo, o la única cuenta de ese tipo;
 * 4. lo aprendido para esa app (compatible con el tipo, si se sabe).
 */
object AccountMatcher {
    const val KIND_PREFIX = "k:"

    fun matchesKind(a: AccountEntity, kind: CardKind): Boolean = when (kind) {
        CardKind.CREDIT -> a.type == AccountType.CREDIT_CARD
        CardKind.DEBIT -> a.type == AccountType.CHECKING || a.type == AccountType.DEBIT || a.type == AccountType.YIELD || a.type == AccountType.SAVINGS
    }

    fun suggest(accounts: List<AccountEntity>, rules: List<SourceRuleEntity>, packageName: String, digits: String?, kind: CardKind?): Long? {
        val active = accounts.filter { !it.archived }
        val byId = active.associateBy { it.id }
        val appRules = rules.filter { it.packageName == packageName && it.accountId in byId }

        if (digits != null) {
            active.firstOrNull { digits in it.digitsList }?.let { return it.id }
            appRules.firstOrNull { it.lastDigits == digits }?.let { return it.accountId }
        }
        if (kind != null) {
            appRules.firstOrNull { it.lastDigits == KIND_PREFIX + kind.name }?.let { return it.accountId }
            appRules.firstOrNull { matchesKind(byId.getValue(it.accountId), kind) }?.let { return it.accountId }
            active.filter { matchesKind(it, kind) }.singleOrNull()?.let { return it.id }
            if (kind == CardKind.DEBIT) active.filter { it.type == AccountType.CHECKING || it.type == AccountType.DEBIT }.singleOrNull()?.let { return it.id }
        }
        val compatible = appRules.filter { kind == null || matchesKind(byId.getValue(it.accountId), kind) }
        return (compatible.firstOrNull { it.lastDigits.isEmpty() } ?: compatible.firstOrNull())?.accountId
    }
}
