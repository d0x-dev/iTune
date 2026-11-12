package com.darkxvenom.itune.backend.lyrics

import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.WordTiming
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses TTML (Timed Text Markup Language) lyrics format into synchronized [LyricsLine]s and LRC text.
 * Used by BetterLyrics, YouLy, and other modern rich lyrics sources.
 */
object TTMLParser {

    fun isTtml(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("<")) return false
        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) ||
            (trimmed.contains("<p ", ignoreCase = true) && trimmed.contains("begin=", ignoreCase = true))
    }

    fun parseTtml(ttml: String): List<LyricsLine> {
        val clean = ttml.trim()
        if (clean.isBlank()) return emptyList()

        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(clean.toByteArray(Charsets.UTF_8)))
            val timingContext = readTimingContext(doc.documentElement)

            val pElements = doc.getElementsByTagName("*")
            val result = mutableListOf<LyricsLine>()

            for (i in 0 until pElements.length) {
                val elem = pElements.item(i) as? Element ?: continue
                if (!elem.tagName.endsWith("p", ignoreCase = true)) continue

                val beginAttr = elem.getAttribute("begin").takeIf { it.isNotBlank() } ?: continue
                val endAttr = elem.getAttribute("end").takeIf { it.isNotBlank() }
                val durAttr = elem.getAttribute("dur").takeIf { it.isNotBlank() }

                val startSec = parseTime(beginAttr, timingContext)
                val endSec = when {
                    endAttr != null -> parseTime(endAttr, timingContext)
                    durAttr != null -> startSec + parseTime(durAttr, timingContext)
                    else -> startSec + 4.0
                }
                val startMs = (startSec * 1000.0).toLong().coerceAtLeast(0L)
                val endMs = (endSec * 1000.0).toLong().coerceAtLeast(startMs)

                val words = mutableListOf<WordTiming>()
                val lineTextBuilder = StringBuilder()

                extractSpansAndText(elem, words, lineTextBuilder, startMs, endMs, timingContext)

                val fullText = lineTextBuilder.toString().replace(Regex("\\s+"), " ").trim()
                if (fullText.isNotBlank()) {
                    val finalWords = if (words.isNotEmpty()) {
                        words.sortedBy { it.startMs }
                    } else {
                        // Synthesize approximate word timings across line duration
                        val tokens = fullText.split(" ").filter { it.isNotBlank() }
                        if (tokens.isNotEmpty()) {
                            val lineDuration = (endMs - startMs).coerceIn(1200L, 8000L)
                            val wordDuration = lineDuration / tokens.size
                            var curStart = startMs
                            tokens.map { tok ->
                                val curEnd = curStart + wordDuration
                                val wt = WordTiming(startMs = curStart, endMs = curEnd, text = tok)
                                curStart = curEnd
                                wt
                            }
                        } else emptyList()
                    }
                    result.add(LyricsLine(timeMs = startMs, text = fullText, words = finalWords))
                }
            }

            result.sortedBy { it.timeMs }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun ttmlToLrc(ttml: String): String {
        val lines = parseTtml(ttml)
        if (lines.isEmpty()) return ""
        return lines.joinToString("\n") { line ->
            val mins = line.timeMs / 60000L
            val secs = (line.timeMs % 60000L) / 1000L
            val centis = (line.timeMs % 1000L) / 10L
            "[%02d:%02d.%02d] %s".format(mins, secs, centis, line.text)
        }
    }

    private fun extractSpansAndText(
        element: Element,
        words: MutableList<WordTiming>,
        textBuilder: StringBuilder,
        parentStartMs: Long,
        parentEndMs: Long,
        timingContext: TimingContext,
    ) {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) ?: continue
            when (child.nodeType) {
                Node.TEXT_NODE -> {
                    val text = child.textContent
                    if (!text.isNullOrEmpty()) {
                        textBuilder.append(text)
                    }
                }
                Node.ELEMENT_NODE -> {
                    val childElem = child as? Element ?: continue
                    if (childElem.tagName.endsWith("span", ignoreCase = true)) {
                        val beginAttr = childElem.getAttribute("begin").takeIf { it.isNotBlank() }
                        val endAttr = childElem.getAttribute("end").takeIf { it.isNotBlank() }
                        val durAttr = childElem.getAttribute("dur").takeIf { it.isNotBlank() }

                        val spanStartMs = if (beginAttr != null) {
                            (parseTime(beginAttr, timingContext) * 1000.0).toLong()
                        } else parentStartMs

                        val spanEndMs = when {
                            endAttr != null -> (parseTime(endAttr, timingContext) * 1000.0).toLong()
                            durAttr != null -> spanStartMs + (parseTime(durAttr, timingContext) * 1000.0).toLong()
                            else -> parentEndMs
                        }

                        val spanText = childElem.textContent?.trim().orEmpty()
                        if (spanText.isNotBlank()) {
                            words.add(WordTiming(startMs = spanStartMs, endMs = spanEndMs, text = spanText))
                            if (textBuilder.isNotEmpty() && !textBuilder.endsWith(" ")) {
                                textBuilder.append(" ")
                            }
                            textBuilder.append(spanText)
                        }
                    } else if (childElem.tagName.endsWith("br", ignoreCase = true)) {
                        textBuilder.append(" ")
                    } else {
                        extractSpansAndText(childElem, words, textBuilder, parentStartMs, parentEndMs, timingContext)
                    }
                }
            }
        }
    }

    private data class TimingContext(val frameRate: Double = 30.0, val tickRate: Double = 30.0)

    private fun readTimingContext(root: Element?): TimingContext {
        if (root == null) return TimingContext()
        val attrs = root.attributes ?: return TimingContext()
        var frameRate = 30.0
        var tickRate = 30.0
        for (i in 0 until attrs.length) {
            val node = attrs.item(i) ?: continue
            if (node.nodeName.endsWith("frameRate", ignoreCase = true)) {
                node.nodeValue?.toDoubleOrNull()?.let { frameRate = it.coerceAtLeast(1.0) }
            } else if (node.nodeName.endsWith("tickRate", ignoreCase = true)) {
                node.nodeValue?.toDoubleOrNull()?.let { tickRate = it.coerceAtLeast(1.0) }
            }
        }
        return TimingContext(frameRate = frameRate, tickRate = tickRate)
    }

    private fun parseTime(timeStr: String, timingContext: TimingContext): Double {
        val raw = timeStr.trim()
        if (raw.isEmpty()) return 0.0

        val offsetRegex = Regex("""^([0-9]+(?:\.[0-9]+)?)(h|ms|m|s|f|t)$""", RegexOption.IGNORE_CASE)
        offsetRegex.matchEntire(raw)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return 0.0
            return when (m.groupValues[2].lowercase()) {
                "h" -> value * 3600.0
                "m" -> value * 60.0
                "s" -> value
                "ms" -> value / 1000.0
                "f" -> value / timingContext.frameRate
                "t" -> value / timingContext.tickRate
                else -> value
            }
        }

        val cleanClock = raw.replace(';', ':')
        if (cleanClock.contains(":")) {
            val parts = cleanClock.split(":")
            return when (parts.size) {
                2 -> {
                    val m = parts[0].toDoubleOrNull() ?: 0.0
                    val s = parts[1].toDoubleOrNull() ?: 0.0
                    m * 60.0 + s
                }
                3 -> {
                    val h = parts[0].toDoubleOrNull() ?: 0.0
                    val m = parts[1].toDoubleOrNull() ?: 0.0
                    val s = parts[2].toDoubleOrNull() ?: 0.0
                    h * 3600.0 + m * 60.0 + s
                }
                else -> cleanClock.toDoubleOrNull() ?: 0.0
            }
        }

        return raw.toDoubleOrNull() ?: 0.0
    }
}
