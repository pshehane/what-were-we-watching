package net.shehane.watching.data

import net.shehane.watching.model.Library
import net.shehane.watching.model.Show

/**
 * Turning a pile of episode synopses into something worth reading.
 *
 * Three ways to do it, in falling order of quality, and the app walks down the
 * list until one of them works:
 *
 *  1. A cloud model, which can be told exactly what to write. Only exists when
 *     whoever built the app supplied a key, and only works with a network.
 *  2. The phone's own model, through AICore. No network, no key, no cost, and no
 *     say in what it writes beyond how much text you hand it.
 *  3. Nothing at all: the synopses themselves, which is what this app did before
 *     any of this and remains the floor nothing falls below.
 *
 * The prompt building here is pure so the one rule that matters can be tested:
 * nothing at or after the position ever reaches a model.
 */
object Summary {

    enum class Source {
        CLOUD,
        DEVICE,
        /** The synopses, unsummarised. Never fails, so it is what everything lands on. */
        RAW,
    }

    /** Why the thing you picked is not what you got. Null when it is. */
    enum class Fallback {
        NO_KEY,
        NO_NETWORK,
        NO_DEVICE_MODEL,
        /** The phone can do it, but is still fetching the model. Try again later. */
        DEVICE_DOWNLOADING,
        FAILED,
    }

    data class Result(
        val text: String?,
        val source: Source,
        val fallback: Fallback?,
        /** What the phone's model calls itself, when it was the one that answered. */
        val modelName: String? = null,
    )

    /**
     * What the chosen mode can actually deliver right now.
     *
     * Cloud wants a key and a network. On-device wants a phone with AICore. The
     * preference is only ever a preference; this is where it meets the facts.
     */
    fun resolve(
        mode: String,
        hasCloudKey: Boolean,
        hasNetwork: Boolean,
        hasDeviceModel: Boolean,
    ): Pair<Source, Fallback?> = when (mode) {
        Library.SUMMARY_NONE -> Source.RAW to null

        Library.SUMMARY_CLOUD -> when {
            !hasCloudKey -> onDeviceOr(hasDeviceModel, Fallback.NO_KEY)
            !hasNetwork -> onDeviceOr(hasDeviceModel, Fallback.NO_NETWORK)
            else -> Source.CLOUD to null
        }

        else -> onDeviceOr(hasDeviceModel, null)
    }

    private fun onDeviceOr(hasDeviceModel: Boolean, why: Fallback?): Pair<Source, Fallback?> =
        if (hasDeviceModel) Source.DEVICE to why
        else Source.RAW to (why ?: Fallback.NO_DEVICE_MODEL)

    /**
     * The same text, trimmed to fit a budget.
     *
     * The oldest material goes first: a phone-sized model has room for a few
     * thousand characters, and what happened three seasons ago matters less than
     * what happened last week. Season synopses go before episodes for the same
     * reason, since they are the coarsest thing in there.
     */
    fun sourceText(recap: Recap.CatchUp, maxChars: Int): String {
        var earlier = recap.earlier
        var recent = recap.recent

        while (true) {
            val text = sourceText(recap.copy(earlier = earlier, recent = recent))
            if (text.length <= maxChars) return text

            when {
                earlier.isNotEmpty() -> earlier = earlier.drop(1)
                recent.size > 1 -> recent = recent.dropLast(1)
                // One episode left and still too long: the caller's budget is
                // smaller than a single synopsis, so hand back what will fit.
                else -> return text.take(maxChars)
            }
        }
    }

    /**
     * The synopses, oldest first, as the model sees them.
     *
     * Built from [Recap.catchUp], so the cut-off is the same one the raw view
     * uses and there is only one place it can go wrong.
     */
    fun sourceText(recap: Recap.CatchUp): String = buildString {
        // Oldest first: a summary reads forwards even though the list shows the
        // most recent at the top.
        for ((number, synopsis) in recap.earlier.sortedBy { it.first }) {
            appendLine("Season $number: $synopsis")
            appendLine()
        }
        for (entry in recap.recent.reversed()) {
            append("S${entry.season} E${entry.episode} ")
            appendLine(entry.title)
            entry.summary?.let { appendLine(it) }
            appendLine()
        }
    }.trim()

    /**
     * The instruction for the cloud model.
     *
     * One bullet for the story, then one for each main character. The spoiler rule
     * is stated as well as enforced: the text below the prompt already stops at the
     * position, so the model has nothing later to leak even if it ignored the
     * instruction, but saying it improves what comes back.
     */
    fun prompt(
        show: Show,
        upTo: String,
        characters: List<String>,
        body: String,
    ): String = buildString {
        appendLine("Write a spoiler-free recap for someone coming back to a TV show.")
        appendLine()
        appendLine("Show: ${show.title}")
        appendLine("They are about to watch $upTo. They have not seen it, or anything after it.")
        appendLine("Everything below is from episodes they have already watched.")
        appendLine()
        appendLine("Write one bullet for the story so far.")
        if (characters.isNotEmpty()) {
            appendLine("Then one bullet for each of these characters, in this order:")
            characters.forEach { appendLine("  $it") }
            appendLine("Name the character first, then say where things stand for them now.")
        }
        appendLine()
        appendLine("One or two sentences per bullet. Plain English.")
        appendLine("Start every bullet with \"- \". No headings, no bold, no preamble.")
        appendLine("Do not mention episode numbers.")
        appendLine()
        appendLine("---")
        append(body)
    }

    /** Splits a model's answer into bullets, whichever marker it reached for. */
    fun bullets(text: String): List<String> = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim() }
        .filter { it.isNotEmpty() }
        .toList()

    /**
     * How many bullets to ask the phone's model for.
     *
     * The formula is one for the plot and one per main character, but ML Kit only
     * offers one, two or three and will not be told what goes in them. So this is
     * the formula clamped to what the API can express, and the screen says plainly
     * that the phone chooses the content.
     */
    fun deviceBulletCount(characters: Int): Int = (1 + characters).coerceIn(1, 3)
}
