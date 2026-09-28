package moe.rgsekai.sekaitune.simpstream

/**
 * Pure JVM logging facade for the :core module.
 *
 * Timber is an Android-only AAR and cannot be referenced in :core (pure JVM module).
 * The :app module installs a Timber sink into [sink] during startup.
 */
object SimpStreamLog {
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    fun interface Sink {
        fun log(level: Int, tag: String, message: String, error: Throwable?)
    }

    @Volatile
    var sink: Sink? = null

    fun d(tag: String, message: String) {
        val s = sink
        if (s != null) s.log(DEBUG, tag, message, null)
        else println("D/$tag: $message")
    }

    fun i(tag: String, message: String) {
        val s = sink
        if (s != null) s.log(INFO, tag, message, null)
        else println("I/$tag: $message")
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        val s = sink
        if (s != null) s.log(WARN, tag, message, error)
        else {
            println("W/$tag: $message")
            error?.printStackTrace()
        }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        val s = sink
        if (s != null) s.log(ERROR, tag, message, error)
        else {
            System.err.println("E/$tag: $message")
            error?.printStackTrace()
        }
    }
}
