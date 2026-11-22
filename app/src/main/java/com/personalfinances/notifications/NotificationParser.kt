package com.personalfinances.notifications

import com.personalfinances.domain.Currency
import com.personalfinances.domain.Money
import com.personalfinances.data.db.ParserRuleEntity

/** Tipo de medio de pago, si la notificación lo dice. */
enum class CardKind { CREDIT, DEBIT }

data class ParsedTransaction(
    val amount: Long,
    val currency: Currency,
    val merchant: String?,
    val isIncome: Boolean,
    /** Últimos 4 dígitos de la tarjeta/cuenta, si aparecen. */
    val lastDigits: String?,
    val cardKind: CardKind? = null,
    val kind: TxKind = TxKind.EXPENSE,
)

enum class TxKind { EXPENSE, INCOME, TRANSFER, CARD_PAYMENT }

/** Resultado de aplicar las reglas enseñadas por el usuario. */
sealed interface RuleOutcome {
    data object NoMatch : RuleOutcome
    data object Ignore : RuleOutcome
    data class Parsed(val tx: ParsedTransaction) : RuleOutcome
}

/**
 * Extrae montos de notificaciones de bancos, billeteras y Google Wallet.
 * Es deliberadamente heurístico: el usuario siempre confirma antes de registrar.
 */
object NotificationParser {

    const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"

    private val amountRegex = Regex(
        """(?i)(US\$|USD|CLP|EUR|€|UF|\$)\s?([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)""" +
            """|([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)\s?(USD|CLP|EUR|pesos)\b"""
    )

    private val expenseWords = listOf(
        "compra", "compraste", "pagaste", "pago", "cargo", "cargaron", "gasto", "gastaste", "transacci",
        "débito", "debito", "giro", "retiro", "transferiste", "enviaste", "purchase", "paid", "spent", "suscripci",
    )
    private val incomeWords = listOf(
        "abono", "recibiste", "depósito", "deposito", "te transfiri", "te enviaron", "transferencia recibida",
        "reembolso", "devoluci", "received", "rentabilidad", "ganaste",
    )
    private val ignoreWords = listOf(
        "código", "codigo", "clave", "otp", "verificaci", "rechazad", "declined", "promo", "oferta", "descuento exclusivo",
    )

    private val digitsRegex = Regex("""(?i)(?:terminada en|terminación|termino en|término|final|\*{1,4}|•{1,4}|x{2,4})\s*(\d{4})\b""")
    private val merchantRegex = Regex(
        """(?i)\b(?:en|a|at|comercio:?)\s+([A-Za-zÁÉÍÓÚÑáéíóúñ0-9][A-Za-zÁÉÍÓÚÑáéíóúñ0-9*&.'\- ]{1,40}?)(?=\s+(?:el|por|con|a las|del|on|for|with|usando|tarjeta)\b|[,.;:!](?:\s|$)|\s*$)"""
    )

    private val creditRegex = Regex("""(?i)(cr[ée]dito|credit card|tarjeta de cr)""")
    private val debitRegex = Regex("""(?i)(d[ée]bito|debit|cuenta corriente|cta\.? ?cte|cuenta vista|cuenta ?rut|prepago|redcompra)""")

    /** "crédito" / "débito" (o cuenta corriente, vista, RUT…) en el texto; null si no dice o es ambiguo. */
    fun cardKind(text: String): CardKind? {
        val credit = creditRegex.containsMatchIn(text)
        val debit = debitRegex.containsMatchIn(text)
        return when {
            credit && !debit -> CardKind.CREDIT
            debit && !credit -> CardKind.DEBIT
            else -> null
        }
    }

    private val cardPaymentRegex = Regex("""(?i)(pago (de )?(tu )?tarjeta|pagaste (tu|la) tarjeta|abono a (tu )?tarjeta|pago (de |a )?(tu )?(tarjeta de )?cr[ée]dito)""")
    private val transferOutRegex = Regex("""(?i)(transferencia (enviada|realizada|exitosa|a terceros)|transferiste|enviaste (dinero|\$))""")

    /** Texto completo tal como lo ve el lector (título + cuerpo). */
    fun fullText(title: String?, text: String?) = "${title.orEmpty().trim()}. ${text.orEmpty().trim()}"

    /** Identifica "el mismo tipo" de notificación: primeras 3 palabras, con los dígitos reemplazados. */
    fun signature(full: String): String = full.lowercase().replace(Regex("[0-9]"), "#").replace(Regex("[^\\p{L}#\\s]"), " ")
        .split(Regex("\\s+")).filter { it.isNotBlank() }.take(3).joinToString(" ")

    /** Todos los montos del texto, en orden. */
    fun amounts(full: String, defaultCurrency: Currency): List<Pair<Long, Currency>> =
        amountRegex.findAll(full).mapNotNull { toAmount(it, defaultCurrency) }.toList()

    private fun toAmount(match: MatchResult, defaultCurrency: Currency): Pair<Long, Currency>? {
        val (symbol, number) = if (match.groupValues[2].isNotEmpty()) match.groupValues[1] to match.groupValues[2]
        else match.groupValues[4] to match.groupValues[3]
        val currency = when (symbol.uppercase()) {
            "US$", "USD" -> Currency.USD
            "EUR", "€" -> Currency.EUR
            "UF" -> Currency.UF
            "CLP", "PESOS" -> Currency.CLP
            else -> defaultCurrency
        }
        val amount = Money.parse(number, currency) ?: return null
        return if (amount > 0) amount to currency else null
    }

    fun words(full: String): List<String> = full.split(Regex("\\s+")).filter { it.isNotBlank() }
    private fun clean(w: String) = w.lowercase().trim { !it.isLetterOrDigit() }

    fun applyRules(packageName: String, title: String?, text: String?, rules: List<ParserRuleEntity>, defaultCurrency: Currency): RuleOutcome {
        val full = fullText(title, text)
        val sig = signature(full)
        val rule = rules.firstOrNull { it.packageName == packageName && it.signature == sig } ?: return RuleOutcome.NoMatch
        if (rule.kind == "IGNORE") return RuleOutcome.Ignore
        val (amount, currency) = amounts(full, defaultCurrency).getOrNull(rule.amountOrdinal) ?: return RuleOutcome.NoMatch
        val merchant = rule.merchantPrefix?.let { prefix ->
            val w = words(full)
            val start = w.indexOfFirst { clean(it) == prefix }
            if (start < 0) null else {
                val rest = w.drop(start + 1)
                val end = rule.merchantSuffix?.let { suf -> rest.indexOfFirst { clean(it) == suf }.takeIf { it > 0 } } ?: minOf(rest.size, 4)
                rest.take(end).joinToString(" ").trim { !it.isLetterOrDigit() }.takeIf { it.isNotBlank() }
            }
        }
        val kind = runCatching { TxKind.valueOf(rule.kind) }.getOrDefault(TxKind.EXPENSE)
        return RuleOutcome.Parsed(ParsedTransaction(amount, currency, merchant, kind == TxKind.INCOME, digitsRegex.find(full)?.groupValues?.get(1), cardKind(full), kind))
    }

    fun parse(
        packageName: String,
        title: String?,
        text: String?,
        defaultCurrency: Currency = Currency.CLP,
        rules: List<ParserRuleEntity> = emptyList(),
    ): ParsedTransaction? {
        when (val r = applyRules(packageName, title, text, rules, defaultCurrency)) {
            RuleOutcome.Ignore -> return null
            is RuleOutcome.Parsed -> return r.tx
            RuleOutcome.NoMatch -> Unit
        }
        val t = title.orEmpty().trim()
        val body = text.orEmpty().trim()
        val full = "$t. $body"
        val lower = full.lowercase()

        if (ignoreWords.any { lower.contains(it) }) return null

        val match = amountRegex.find(body) ?: amountRegex.find(t) ?: return null
        val (amount, currency) = toAmount(match, defaultCurrency) ?: return null

        val isWallet = packageName == GOOGLE_WALLET
        val isIncome = !isWallet && incomeWords.any { lower.contains(it) } && expenseWords.none { lower.contains(it) && it != "pago" }
        val looksLikeExpense = isWallet || expenseWords.any { lower.contains(it) }
        if (!isIncome && !looksLikeExpense) return null

        val merchant = when {
            isWallet && t.isNotBlank() && amountRegex.find(t) == null -> t
            else -> merchantRegex.findAll(full)
                .map { it.groupValues[1].trim() }
                .firstOrNull { candidate -> amountRegex.find(candidate) == null && candidate.length >= 2 && !candidate.lowercase().startsWith("tu ") }
        }?.take(40)

        val digits = digitsRegex.find(full)?.groupValues?.get(1)
        val kind = when {
            isIncome -> TxKind.INCOME
            cardPaymentRegex.containsMatchIn(full) -> TxKind.CARD_PAYMENT
            transferOutRegex.containsMatchIn(full) -> TxKind.TRANSFER
            else -> TxKind.EXPENSE
        }
        return ParsedTransaction(amount, currency, merchant, isIncome, digits, cardKind(full), kind)
    }
}
