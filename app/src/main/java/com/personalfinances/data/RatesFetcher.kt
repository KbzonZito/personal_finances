package com.personalfinances.data

import com.personalfinances.domain.Currency
import com.personalfinances.domain.ExchangeRates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Obtiene dólar, euro y UF desde mindicador.cl (indicadores del Banco Central de Chile). */
object RatesFetcher {
    suspend fun fetch(current: ExchangeRates): ExchangeRates = withContext(Dispatchers.IO) {
        val conn = URL("https://mindicador.cl/api").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            fun value(key: String) = json.optJSONObject(key)?.optDouble("valor")?.takeIf { !it.isNaN() && it > 0 }
            val updated = current.clpPerUnit.toMutableMap()
            value("dolar")?.let { updated[Currency.USD] = it }
            value("euro")?.let { updated[Currency.EUR] = it }
            value("uf")?.let { updated[Currency.UF] = it }
            value("bitcoin")?.let { btcUsd -> updated[Currency.BTC] = btcUsd * (updated[Currency.USD] ?: Currency.USD.defaultRateClp) }
            ExchangeRates(updated)
        } finally {
            conn.disconnect()
        }
    }
}
