package com.personalfinances.notifications

import com.personalfinances.domain.Currency
import com.personalfinances.domain.Money

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
)

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

    fun parse(packageName: String, title: String?, text: String?, defaultCurrency: Currency = Currency.CLP): ParsedTransaction? {
        val t = title.orEmpty().trim()
        val body = text.orEmpty().trim()
        val full = "$t. $body"
        val lower = full.lowercase()

        if (ignoreWords.any { lower.contains(it) }) return null

        val match = amountRegex.find(body) ?: amountRegex.find(t) ?: return null
        val (symbol, number) = if (match.groupValues[2].isNotEmpty()) {
            match.groupValues[1] to match.groupValues[2]
        } else {
            match.groupValues[4] to match.groupValues[3]
        }
        val currency = when (symbol.uppercase()) {
            "US$", "USD" -> Currency.USD
            "EUR", "€" -> Currency.EUR
            "UF" -> Currency.UF
            "CLP", "PESOS" -> Currency.CLP
            else -> defaultCurrency
        }
        val amount = Money.parse(number, currency) ?: return null
        if (amount <= 0) return null

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
        return ParsedTransaction(amount, currency, merchant, isIncome, digits, cardKind(full))
    }
}
