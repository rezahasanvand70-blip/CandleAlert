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
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly"

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE), Scope(DRIVE_SCOPE))).build()

    @Volatile var lastError: String = ""

    fun requestAuthorization(activity: Activity, allowResolution: Boolean, onAuthorized: (AuthorizationResult) -> Unit) {
        lastError = ""
        Identity.getAuthorizationClient(activity).authorize(request())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    if (!allowResolution) { lastError = "Google permission needs user approval."; return@addOnSuccessListener }
                    val pending = result.pendingIntent
                    if (pending == null) {
                        lastError = "Google returned a permission request without a resolution."
                        android.widget.Toast.makeText(activity, lastError, android.widget.Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    try {
                        startIntentSenderForResult(activity, pending.intentSender, REQUEST_CODE, null, 0, 0, 0, null)
                    } catch (e: Exception) {
                        lastError = "Could not open Google permission screen: " + (e.message ?: "unknown error")
                        android.widget.Toast.makeText(activity, lastError, android.widget.Toast.LENGTH_LONG).show()
                    }
                } else if (result.accessToken.isNullOrBlank()) {
                    lastError = "Google authorization returned no access token."
                    android.widget.Toast.makeText(activity, lastError, android.widget.Toast.LENGTH_LONG).show()
                } else onAuthorized(result)
            }
            .addOnFailureListener { e ->
                lastError = "Google authorization failed: " + (e.message ?: e.javaClass.simpleName)
                android.widget.Toast.makeText(activity, lastError, android.widget.Toast.LENGTH_LONG).show()
            }
    }

    fun handleAuthorizationResult(activity: Activity, data: Intent?): AuthorizationResult? {
        return try {
            if (data == null) { lastError = "Google did not return an authorization result."; null }
            else {
                val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
                if (result.accessToken.isNullOrBlank()) lastError = "Google authorization finished without an access token." else lastError = ""
                result
            }
        } catch (e: Exception) {
            val raw = e.message ?: e.javaClass.simpleName
            lastError = if (raw.contains("UNREGISTERED", ignoreCase = true)) {
                "Google app is not registered for OAuth. Register package com.example.candlealert with the SHA-1 of this APK in Google Cloud, then retry. Details: $raw"
            } else {
                "Google authorization result error: $raw"
            }
            null
        }
    }
    data class CreatedSheet(val id: String, val url: String)
    data class SheetLookup(val checked: Boolean, val sheet: CreatedSheet?)

    fun findJournalSheet(token: String): SheetLookup {
        val query = "name = 'Candle Alert Journal' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=" + URLEncoder.encode(query, "UTF-8") +
            "&pageSize=100&orderBy=modifiedTime%20desc&fields=" +
            URLEncoder.encode("files(id,name,webViewLink,modifiedTime)", "UTF-8")
        val response = request("GET", url, token, null) ?: return SheetLookup(false, null)
        return try {
            val files = JSONObject(response).optJSONArray("files") ?: JSONArray()
            if (files.length() == 0) SheetLookup(true, null) else {
                val file = files.getJSONObject(0)
                val id = file.optString("id")
                val link = file.optString("webViewLink").ifBlank { "https://docs.google.com/spreadsheets/d/$id/edit" }
                SheetLookup(id.isNotBlank(), if (id.isNotBlank()) CreatedSheet(id, link) else null)
            }
        } catch (e: Exception) {
            lastError = "Could not read Google Drive journal search results: " + (e.message ?: e.javaClass.simpleName)
            SheetLookup(false, null)
        }
    }

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
            put(JSONArray(listOf("Trade ID","Status","Symbol","Direction","Entry Time","Entry","SL","TP","Volume","Exit Time","Duration","Exit","Gross P/L","Swap (signed)","Commission Cost","Exit Reason","Entry Notes","Exit Notes","Net P/L After Costs")))
            trades.forEach { put(JSONArray(it)) }
        }
        val accountRows = JSONArray().apply {
            put(JSONArray(listOf("Item","Value")))
            put(JSONArray(listOf("Initial Balance", initial)))
            put(JSONArray(listOf("Deposits", deposits)))
            put(JSONArray(listOf("Withdrawals", withdrawals)))
        }
        val clearBody = JSONObject().put("ranges", JSONArray().put("Trades!A:S").put("Account!A:B"))
        if (request("POST", "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchClear", token, clearBody.toString()) == null) return false
        val batch = JSONObject().apply {
            put("valueInputOption", "USER_ENTERED")
            put("data", JSONArray()
                .put(JSONObject().put("range", "Trades!A1:S${tradeRows.length()}").put("majorDimension", "ROWS").put("values", tradeRows))
                .put(JSONObject().put("range", "Account!A1:B${accountRows.length()}").put("majorDimension", "ROWS").put("values", accountRows)))
        }
        return request("POST", "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate", token, batch.toString()) != null
    }

    fun readJournal(token: String, spreadsheetId: String): JSONObject? {
        val encoded = URLEncoder.encode("Trades!A1:S", "UTF-8")
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
            if (code in 200..299) response
            else { lastError = "Google Sheets HTTP $code" + if (response.isNotBlank()) ": " + extractGoogleError(response) else ""; null }
        } catch (e: Exception) { lastError = "Google Sheets connection error: " + (e.message ?: e.javaClass.simpleName); null }
    }

    private fun extractGoogleError(response: String): String = try {
        val obj = JSONObject(response)
        val message = obj.optJSONObject("error")?.optString("message")?.trim().orEmpty()
        if (message.isNotBlank()) message else response.take(220)
    } catch (_: Exception) { response.take(220) }
}
