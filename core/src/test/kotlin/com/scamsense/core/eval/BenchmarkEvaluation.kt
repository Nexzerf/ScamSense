package com.scamsense.core.eval

import com.scamsense.core.engine.AnalysisOutcome
import com.scamsense.core.engine.CheckType
import com.scamsense.core.engine.RiskLevel
import com.scamsense.core.engine.ScamAnalyzer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reproducible accuracy evaluation on a held-out Thai benchmark
 * (core/src/test/resources/eval/thai_scam_benchmark.tsv).
 *
 * Writes core/build/eval/results.json for the report. The asserted floors are regression
 * guards set below the measured values — they are not tuned targets. The engine must not
 * be tuned on this set; new rules should be validated on separate data.
 */
class BenchmarkEvaluation {

    @Serializable data class Item(val id: String, val label: String, val category: String, val text: String)

    @Serializable data class Prediction(
        val id: String, val label: String, val category: String, val variant: String,
        val score: Int, val level: String, val block: Boolean, val scamType: String?, val topReason: String?, val micros: Long,
    )

    @Serializable data class Metrics(
        val n: Int, val tp: Int, val fp: Int, val tn: Int, val fn: Int,
        val accuracy: Double, val precision: Double, val recall: Double, val f1: Double, val falsePositiveRate: Double,
    )

    @Serializable data class Results(
        val datasetSize: Int, val scams: Int, val benign: Int,
        val suspiciousThreshold: Metrics, val dangerousThreshold: Metrics,
        val obfuscatedRecall: Double, val obfuscatedCount: Int,
        val perCategoryRecall: Map<String, Double>, val perCategoryFalsePositive: Map<String, Int>,
        val latencyMicrosMean: Double, val latencyMicrosP95: Long,
        val predictions: List<Prediction>,
    )

    private val analyzer = ScamAnalyzer()

    private fun load(): List<Item> =
        javaClass.getResourceAsStream("/eval/thai_scam_benchmark.tsv")!!.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('\t').let { Item(it[0], it[1], it[2], it[3]) } }

    /** Evasion tricks seen in real scam SMS: Thai digits, spaced Latin, zero-width chars, เเ for แ, split keywords. */
    private fun obfuscate(text: String): String {
        var t = text
        listOf("บัญชี" to "บั.ญ.ชี", "โอนเงิน" to "โ-อ-น-เงิน", "รหัส" to "ร หั ส", "ด่วน" to "ด่ว.น").forEach { (a, b) -> t = t.replace(a, b) }
        t = Regex("\\b[A-Za-z]{2,5}\\b").replace(t) { m -> m.value.uppercase().toCharArray().joinToString(" ") }
        t = t.map { c -> if (c in '0'..'9') '๐' + (c - '0') else c }.joinToString("")
        t = t.replace("แ", "เเ")
        val sb = StringBuilder()
        var thai = 0
        for (c in t) {
            sb.append(c)
            if (c in 'ก'..'ฮ' && ++thai % 4 == 0) sb.append('​')
        }
        return sb.toString()
    }

    private fun predict(item: Item, variant: String, text: String): Prediction {
        val t0 = System.nanoTime()
        val r = (analyzer.analyze(CheckType.MESSAGE, text) as AnalysisOutcome.Success).result
        val micros = (System.nanoTime() - t0) / 1_000
        return Prediction(item.id, item.label, item.category, variant, r.score, r.level.name, r.shouldBlock,
            r.matchedScamType, r.signals.firstOrNull { it.weight > 0 }?.title, micros)
    }

    private fun metrics(ps: List<Prediction>, positive: (Prediction) -> Boolean): Metrics {
        val tp = ps.count { it.label == "scam" && positive(it) }
        val fn = ps.count { it.label == "scam" && !positive(it) }
        val fp = ps.count { it.label == "benign" && positive(it) }
        val tn = ps.count { it.label == "benign" && !positive(it) }
        val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        return Metrics(ps.size, tp, fp, tn, fn, (tp + tn).toDouble() / ps.size, precision, recall, f1,
            if (fp + tn == 0) 0.0 else fp.toDouble() / (fp + tn))
    }

    @Test fun evaluate() {
        val items = load()
        repeat(3) { items.forEach { i -> analyzer.analyze(CheckType.MESSAGE, i.text) } } // JIT warm-up
        val clean = items.map { predict(it, "original", it.text) }
        val obf = items.filter { it.label == "scam" }.map { predict(it, "obfuscated", obfuscate(it.text)) }

        val flagged: (Prediction) -> Boolean = { it.level != RiskLevel.SAFE.name }
        val dangerous: (Prediction) -> Boolean = { it.level == RiskLevel.DANGEROUS.name }
        val susp = metrics(clean, flagged)
        val dang = metrics(clean, dangerous)
        val obfRecall = obf.count(flagged).toDouble() / obf.size
        val perCatRecall = clean.filter { it.label == "scam" }.groupBy { it.category }
            .mapValues { (_, v) -> v.count(flagged).toDouble() / v.size }.toSortedMap()
        val perCatFp = clean.filter { it.label == "benign" }.groupBy { it.category }
            .mapValues { (_, v) -> v.count(flagged) }.toSortedMap()
        val lat = clean.map { it.micros }.sorted()

        val results = Results(
            items.size, items.count { it.label == "scam" }, items.count { it.label == "benign" },
            susp, dang, obfRecall, obf.size, perCatRecall, perCatFp,
            lat.average(), lat[(lat.size * 95 / 100).coerceAtMost(lat.size - 1)],
            clean + obf,
        )
        val out = File("build/eval").apply { mkdirs() }
        File(out, "results.json").writeText(Json { prettyPrint = true }.encodeToString(Results.serializer(), results))

        println("EVAL n=${items.size} | flagged: acc=%.3f prec=%.3f rec=%.3f f1=%.3f fpr=%.3f | dangerous: prec=%.3f rec=%.3f | obfuscated recall=%.3f | mean=%.0fus p95=%dus"
            .format(susp.accuracy, susp.precision, susp.recall, susp.f1, susp.falsePositiveRate, dang.precision, dang.recall,
                obfRecall, results.latencyMicrosMean, results.latencyMicrosP95))
        clean.filter { flagged(it) != (it.label == "scam") }.forEach { println("MISS ${it.id} ${it.label} score=${it.score} ${it.level} ${it.topReason}") }

        // Regression guards (set after the first measured run, below the measured values).
        assertTrue("recall ${susp.recall}", susp.recall >= MIN_RECALL)
        assertTrue("false-positive rate ${susp.falsePositiveRate}", susp.falsePositiveRate <= MAX_FPR)
        assertTrue("obfuscated recall $obfRecall", obfRecall >= MIN_OBF_RECALL)
    }

    companion object {
        const val MIN_RECALL = 0.90
        const val MAX_FPR = 0.10
        const val MIN_OBF_RECALL = 0.85
    }
}
