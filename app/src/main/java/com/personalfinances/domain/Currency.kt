package com.personalfinances.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Monedas soportadas. Los montos se guardan siempre como [Long] en la unidad mínima
 * de cada moneda (CLP sin decimales, USD en centavos, UF con 2 decimales, etc.).
 */
enum class Currency(
    val code: String,
    val symbol: String,
    val decimals: Int,
    val displayName: String,
    val flag: String,
    /** Valor aproximado de 1 unidad en CLP, usado solo como tasa inicial. */
    val defaultRateClp: Double,
) {
    CLP("CLP", "$", 0, "Peso chileno", "🇨🇱", 1.0),
    UF("UF", "UF ", 2, "Unidad de Fomento", "🇨🇱", 39_600.0),
    USD("USD", "US$", 2, "Dólar estadounidense", "🇺🇸", 950.0),
    EUR("EUR", "€", 2, "Euro", "🇪🇺", 1_040.0),
    GBP("GBP", "£", 2, "Libra esterlina", "🇬🇧", 1_230.0),
    ARS("ARS", "AR$", 2, "Peso argentino", "🇦🇷", 0.85),
    BRL("BRL", "R$", 2, "Real brasileño", "🇧🇷", 165.0),
    MXN("MXN", "MX$", 2, "Peso mexicano", "🇲🇽", 50.0),
    PEN("PEN", "S/ ", 2, "Sol peruano", "🇵🇪", 255.0),
    COP("COP", "COL$", 0, "Peso colombiano", "🇨🇴", 0.23),
    JPY("JPY", "¥", 0, "Yen japonés", "🇯🇵", 6.3),
    BTC("BTC", "₿", 8, "Bitcoin", "🪙", 90_000_000.0);

    private val factor: Long get() = pow10(decimals)

    fun toMinor(major: BigDecimal): Long =
        major.setScale(decimals, RoundingMode.HALF_UP).movePointRight(decimals).toLong()

    fun toMajor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, decimals)

    fun toMajorDouble(minor: Long): Double = minor.toDouble() / factor

    fun fromMajorDouble(major: Double): Long = Math.round(major * factor)

    companion object {
        fun fromCode(code: String): Currency = entries.firstOrNull { it.code == code } ?: CLP
        private fun pow10(n: Int): Long { var r = 1L; repeat(n) { r *= 10 }; return r }
    }
}

object Money {
    private val symbols = DecimalFormatSymbols(Locale("es", "CL"))

    private fun formatter(decimals: Int, forceDecimals: Boolean): DecimalFormat {
        val pattern = if (decimals == 0) "#,##0" else if (forceDecimals) "#,##0.${"0".repeat(decimals)}" else "#,##0.${"#".repeat(decimals)}"
        return DecimalFormat(pattern, symbols)
    }

    /** Formatea un monto en unidad mínima: `$12.345`, `US$12,50`, `-$3.000`. */
    fun format(minor: Long, currency: Currency, showSign: Boolean = false, compact: Boolean = false): String {
        val major = currency.toMajor(abs(minor))
        val body = if (compact && currency.decimals <= 2 && major >= BigDecimal(1_000_000)) {
            formatter(1, false).format(major.divide(BigDecimal(1_000_000))) + "M"
        } else {
            formatter(currency.decimals, currency.decimals in 1..2).format(major)
        }
        val sign = when {
            minor < 0 -> "-"
            showSign && minor > 0 -> "+"
            else -> ""
        }
        return "$sign${currency.symbol}$body"
    }

    /**
     * Interpreta texto ingresado por el usuario o leído de notificaciones.
     * Acepta `12.345`, `12.345,50`, `12,345.50`, `12345`, `12,5`.
     */
    fun parse(text: String, currency: Currency): Long? {
        val cleaned = text.trim().replace(Regex("[^0-9.,]"), "")
        if (cleaned.isEmpty() || cleaned.none { it.isDigit() }) return null
        val lastSep = cleaned.indexOfLast { it == '.' || it == ',' }
        val normalized = if (lastSep < 0) {
            cleaned
        } else {
            val tail = cleaned.substring(lastSep + 1)
            val head = cleaned.substring(0, lastSep).replace(".", "").replace(",", "")
            // Si después del último separador hay exactamente 3 dígitos y la moneda no usa 3 decimales,
            // es separador de miles.
            if (tail.length == 3 || tail.isEmpty()) head + tail else "$head.$tail"
        }
        return runCatching { currency.toMinor(BigDecimal(normalized)) }.getOrNull()
    }
}

/** Tasas de cambio expresadas como "valor de 1 unidad en CLP". */
data class ExchangeRates(val clpPerUnit: Map<Currency, Double>) {
    fun rate(c: Currency): Double = clpPerUnit[c] ?: c.defaultRateClp

    fun convert(minor: Long, from: Currency, to: Currency): Long {
        if (from == to) return minor
        val clp = from.toMajorDouble(minor) * rate(from)
        return to.fromMajorDouble(clp / rate(to))
    }

    companion object {
        val DEFAULT = ExchangeRates(Currency.entries.associateWith { it.defaultRateClp })
    }
}
