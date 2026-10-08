package com.example.candlealert

import android.app.Activity
import android.content.Intent
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

object GoogleSheetsApi {
    const val REQUEST_CODE = 7001
    private const val SCOPE = "https://www.googleapis.com/auth/spreadsheets"

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun requestAuthorization(activity: Activity, allowResolution: Boolean, onAuthorized: (AuthorizationResult) -> Unit) {
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    if (!allowResolution) return@addOnSuccessListener
                    try {
                        result.pendingIntent?.let { startIntentSenderForResult(activity, it.intentSender, REQUEST_CODE, null, 0, 0, 0, null) }
                    } catch (_: Exception) {}
                } else onAuthorized(result)
            }
            .addOnFailureListener {
                if (allowResolution) android.widget.Toast.makeText(activity, "Google authorization is unavailable on this device.", android.widget.Toast.LENGTH_LONG).show()
            }
    }

    fun handleAuthorizationResult(activity: Activity, data: Intent?): AuthorizationResult? =
        try { if (data == null) null else Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data) } catch (_: Exception) { null }

    data class CreatedSheet(val id: String, val url: String)

    fun createJournalSheet(token: String): CreatedSheet? {
        val body = JSONObject().apply {
            put("properties", JSONObject().put("title", "Candle Alert Journal"))
            put("sheets", JSONArray()
                .put(JSONObject().put("properties", JSONObject().put("title", "Trades")))
                .put(JSONObject().put("properties", JSONObject().put("title", "Account"))))
        }
        val response = request("POST", "https://sheets.googleapis.com/v4/spreadsheets", token, body.toString()) ?: return null
        val obj = JSONObject(response)
        val id = obj.optString("spreadsheetId")
        val url = obj.optString("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$id/edit")
        return if (id.isNotBlank()) CreatedSheet(id, url) else null
    }

    fun syncJournal(token: String, spreadsheetId: String, trades: List<List<String>>, initial: String, deposits: String, withdrawals: String): Boolean {
        val tradeRows = JSONArray().apply {
            put(JSONArray(listOf("Trade ID","Status","Symbol","Direction","Entry Time","Entry","SL","TP","Volume","Exit Time","Exit","P/L","Exit Reason","Notes")))
            trades.forEach { put(JSONArray(it)) }
        }
        val accountRows = JSONArray().apply {
            put(JSONArray(listOf("Item","Value")))
            put(JSONArray(listOf("Initial Balance", initial)))
            put(JSONArray(listOf("Deposits", deposits)))
            put(JSONArray(listOf("Withdrawals", withdrawals)))
        }
        val clearBody = JSONObject().put("ranges", JSONArray().put("Trades!A:N").put("Account!A:B"))
        if (request("POST", "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchClear", token, clearBody.toString()) == null) return false
        val batch = JSONObject().apply {
            put("valueInputOption", "USER_ENTERED")
            put("data", JSONArray()
                .put(JSONObject().put("range", "Trades!A1:N${tradeRows.length()}").put("majorDimension", "ROWS").put("values", tradeRows))
                .put(JSONObject().put("range", "Account!A1:B${accountRows.length()}").put("majorDimension", "ROWS").put("values", accountRows)))
        }
        return request("POST", "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate", token, batch.toString()) != null
    }

    fun readJournal(token: String, spreadsheetId: String): JSONObject? {
        val encoded = URLEncoder.encode("Trades!A1:N", "UTF-8")
        val response = request("GET", "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$encoded", token, null) ?: return null
        return try { JSONObject(response) } catch (_: Exception) { null }
    }

    private fun request(method: String, urlString: String, token: String, body: String?): String? {
        return try {
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doInput = true
                if (body != null) doOutput = true
            }
            if (body != null) OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
            conn.disconnect()
            if (code in 200..299) response else null
        } catch (_: Exception) { null }
    }
}
