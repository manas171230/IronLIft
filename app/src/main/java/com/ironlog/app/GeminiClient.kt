package com.ironlog.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Sends a meal photo to the Gemini API and asks for a JSON nutrition estimate.
 * Uses the free Gemini API tier — get a key at https://aistudio.google.com
 * (no card required). Call estimateMeal() from a background thread only.
 */
object GeminiClient {

    // Read from local.properties -> BuildConfig at build time, so the key
    // itself never lives in a file that gets committed to git. See
    // local.properties.example and the README for setup.
    private val API_KEY = BuildConfig.GEMINI_API_KEY

    // Free-tier vision model. If this model name is ever retired, swap it for
    // another current Gemini model from https://ai.google.dev/gemini-api/docs/models
    private const val MODEL = "gemini-2.5-flash"

    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private const val PROMPT =
        "You are looking at a photo of a meal about to be logged in a food diary. " +
        "Identify the dish and give rough nutrition estimates for the portion shown. " +
        "Respond with ONLY a JSON object, no markdown fences, no other text: " +
        "{\"name\": \"short dish name\", \"calories\": integer kcal, \"protein\": integer grams, " +
        "\"carbs\": integer grams, \"fat\": integer grams}. These are approximate visual " +
        "estimates, not lab-precise figures — round sensibly."

    /**
     * Returns a JSON string: either the estimate object, e.g.
     * {"name":"Grilled chicken bowl","calories":520,"protein":38,"carbs":45,"fat":18}
     * or an error object, e.g. {"error":"network_error"}.
     * Blocking — run on a background thread.
     */
    fun estimateMeal(base64Jpeg: String): String {
        if (API_KEY.isBlank()) return errorJson("missing_key")

        val raw: String
        try {
            val requestBody = JSONObject().apply {
                put(
                    "contents", JSONArray().put(
                        JSONObject().put(
                            "parts", JSONArray()
                                .put(JSONObject().put("text", PROMPT))
                                .put(
                                    JSONObject().put(
                                        "inline_data", JSONObject()
                                            .put("mime_type", "image/jpeg")
                                            .put("data", base64Jpeg)
                                    )
                                )
                        )
                    )
                )
                put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            }

            val url = URL("$ENDPOINT?key=$API_KEY")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.outputStream.use { it.write(requestBody.toString().toByteArray(StandardCharsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""

            if (code == 429) return errorJson("rate_limited")
            if (code !in 200..299) return errorJson("network_error")
            raw = body
        } catch (e: Exception) {
            return errorJson("network_error")
        }

        return try {
            val response = JSONObject(raw)
            val candidates = response.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return errorJson("refused")
            }
            val text = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
            JSONObject(text) // validate it's parseable JSON before handing it back
            text
        } catch (e: Exception) {
            errorJson("invalid_json")
        }
    }

    private fun errorJson(code: String) = JSONObject().put("error", code).toString()
}
