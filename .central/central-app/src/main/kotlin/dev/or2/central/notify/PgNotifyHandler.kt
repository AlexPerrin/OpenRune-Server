package dev.or2.central.notify

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PgNotifyChannel(
    val channel: String,
)

fun interface PgNotifyHandler {
    fun handle(payload: String?)
}
