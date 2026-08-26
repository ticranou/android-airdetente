package com.airchecklists.app.data.local

import android.content.Context
import com.airchecklists.app.data.sensors.FdrSample
import java.io.File

/**
 * Persists the flight-recorder rolling buffer to internal storage
 * (filesDir/fdr/). Two kinds of file:
 *  - session.jsonl : the current rolling buffer, rewritten atomically on each flush;
 *  - incident-<ts>.jsonl : a frozen snapshot around an incident (reserved for v2).
 *
 * Records are written one compact CSV line per sample so the file stays small and
 * append/replace stays cheap. All calls are blocking IO — invoke off the main thread.
 */
class FlightRecorderStore(context: Context) {

    private val dir: File = File(context.filesDir, "fdr").apply { mkdirs() }

    val sessionFile: File get() = File(dir, "session.jsonl")

    /** Atomically (re)write the whole current buffer as the session file. */
    fun writeSession(samples: List<FdrSample>) = writeAtomic(sessionFile, samples)

    /** Freeze a buffer to a timestamped incident file (v2 usage). Returns the file. */
    fun freezeIncident(samples: List<FdrSample>, epochMs: Long): File {
        val f = File(dir, "incident-$epochMs.jsonl")
        writeAtomic(f, samples)
        return f
    }

    private fun writeAtomic(target: File, samples: List<FdrSample>) {
        val tmp = File(dir, "${target.name}.tmp")
        tmp.bufferedWriter().use { w ->
            // Header describing the compact columns.
            w.append("# t_ms,kind,v1,v2,v3\n")
            samples.forEach { s ->
                w.append(s.tMs.toString()).append(',')
                    .append(s.kind.name).append(',')
                    .append(fmt(s.v1)).append(',')
                    .append(fmt(s.v2)).append(',')
                    .append(fmt(s.v3)).append('\n')
            }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun fmt(d: Double?): String = d?.toString() ?: ""
}
