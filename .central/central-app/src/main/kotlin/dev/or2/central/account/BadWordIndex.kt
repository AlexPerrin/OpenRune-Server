package dev.or2.central.account

import dev.or2.central.config.BadWordsConfig
import dev.or2.central.config.CentralConfig
import dev.or2.sql.OpenRuneSql
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class BadWordIndex(
    private val config: CentralConfig,
) {
    private val log = LoggerFactory.getLogger(BadWordIndex::class.java)
    private val merged = AtomicReference(loadMerged())

    fun roots(): Set<String> = merged.get()

    fun refresh() {
        merged.set(loadMerged())
    }

    private fun loadMerged(): Set<String> {
        return try {
            val local = readUtf8("profanity/bad_words_local.txt")
            val custom = readUtf8("profanity/bad_words_custom.txt")
            val remote = fetchRemoteWords(config.badWords)
            AccountNameAuthPolicy.parseBadWordLines(local + "\n" + custom + "\n" + remote)
        } catch (e: Exception) {
            log.warn("Bad words merge failed (classpath only): {}", e.message)
            val local = readUtf8("profanity/bad_words_local.txt")
            val custom = readUtf8("profanity/bad_words_custom.txt")
            AccountNameAuthPolicy.parseBadWordLines(local + "\n" + custom)
        }
    }

    private fun readUtf8(resourcePath: String): String =
        OpenRuneSql::class.java.classLoader.getResourceAsStream(resourcePath)
            ?.use { ins -> InputStreamReader(ins, StandardCharsets.UTF_8).use { it.readText() } }
            .orEmpty()

    private fun fetchRemoteWords(badWords: BadWordsConfig): String {
        val url = badWords.remoteUrl
        if (url.isBlank()) return ""
        return try {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
            val request =
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() in 200..299) response.body() else ""
        } catch (e: Exception) {
            log.warn("Bad words fetch failed: {}", e.message)
            ""
        }
    }
}
