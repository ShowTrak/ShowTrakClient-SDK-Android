package io.showtrak.sdk

import java.util.Locale

/**
 * Options describing how an event (action) is presented and handled.
 *
 * @property label    Display label shown in the ShowTrak menu. Defaults to the event name.
 * @property colour   Palette index 0..7 (see the integration spec). Defaults to 7 (dark grey).
 * @property hasFeedback Whether the handler reports success/error back to the server.
 * @property icon     Bootstrap Icons name shown beside the event, e.g. "lightning-charge-fill"
 *                    (browse them at https://icons.getbootstrap.com). The `bi-` prefix is
 *                    optional. Defaults to [DEFAULT_ICON] when omitted.
 * @property timeoutMs How long the handler may run before the SDK resolves its [Ack] as
 *                    [AckStatus.RESOLVED_TIMEOUT]. Raise it for genuinely slow work.
 */
data class EventOptions @JvmOverloads constructor(
    val label: String? = null,
    val colour: Int = 7,
    val hasFeedback: Boolean = true,
    val icon: String? = null,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    init {
        require(colour in 0..7) { "EventOptions.colour must be between 0 and 7" }
        if (label != null) {
            val normalized = label.trim()
            require(normalized.isNotEmpty()) {
                "EventOptions.label cannot be blank when provided"
            }
            require(normalized.length <= 80) {
                "EventOptions.label must be <= 80 characters"
            }
        }
        if (icon != null) {
            require(normalizeIconName(icon) != null) {
                "EventOptions.icon must be a Bootstrap Icons name of <= $MAX_ICON_LENGTH " +
                    "lowercase letters, digits and hyphens (got '$icon')"
            }
        }
        require(timeoutMs in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            "EventOptions.timeoutMs must be between $MIN_TIMEOUT_MS and $MAX_TIMEOUT_MS " +
                "(got $timeoutMs)"
        }
    }

    /**
     * The icon actually published to the server: the normalized [icon], or
     * [DEFAULT_ICON] when none was supplied. Not part of the data-class
     * identity — it is derived from [icon].
     */
    internal val resolvedIcon: String
        get() = icon?.let { normalizeIconName(it) } ?: DEFAULT_ICON

    companion object {
        /** Glyph used for events that declare no icon. Matches the server's default. */
        const val DEFAULT_ICON = "terminal"

        /** Default handler budget, matching the server's default script timeout. */
        const val DEFAULT_TIMEOUT_MS = 15_000L

        /** Longest progress message [Ack.feedback] will send; longer ones are cut. */
        const val MAX_FEEDBACK_LENGTH = 255

        private const val MIN_TIMEOUT_MS = 1_000L
        private const val MAX_TIMEOUT_MS = 600_000L
        private const val MAX_ICON_LENGTH = 64
        private val ICON_PATTERN = Regex("^[a-z0-9-]+$")

        /**
         * Reduce an icon reference to the bare name the server expects:
         * "bi bi-terminal", "bi-terminal" and "terminal" all yield "terminal".
         * Returns null when the value cannot be used as an icon name.
         */
        internal fun normalizeIconName(raw: String): String? {
            val name = raw.trim().lowercase(Locale.US)
                .removePrefix("bi ")
                .trimStart()
                .removePrefix("bi-")
            if (name.isEmpty() || name.length > MAX_ICON_LENGTH) return null
            if (!name.matches(ICON_PATTERN)) return null
            return name
        }
    }
}

/** How an event handler's [Ack] finished, or that it has not finished yet. */
enum class AckStatus {
    /** Still running: none of success, error or the timeout has fired. */
    UNRESOLVED,

    /** [Ack.success] was called. */
    RESOLVED_SUCCESS,

    /** [Ack.error] was called, or the handler threw. */
    RESOLVED_ERROR,

    /** The handler ran past [EventOptions.timeoutMs] without resolving. */
    RESOLVED_TIMEOUT,
}

/**
 * Passed to an event handler when an operator triggers the event. The handler
 * must call exactly one of [success] / [error] when it is done (for
 * [EventOptions.hasFeedback] events; for fire-and-forget events the calls are
 * harmless no-ops).
 *
 * A handler that has not resolved within [EventOptions.timeoutMs] is resolved
 * for it as [AckStatus.RESOLVED_TIMEOUT]. Every method here is safe to call
 * from any thread, and every one is a no-op once the ack has resolved — so a
 * slow handler that eventually finishes cannot overwrite the timeout result.
 */
interface Ack {
    val requestId: String
    val eventId: String

    /** Report successful completion. No-op if already resolved. */
    fun success()

    /** Report failure, with a reason shown to the operator. No-op if already resolved. */
    fun error(message: String)

    /**
     * Optionally report progress while the handler runs: the message replaces
     * the status text on this event's row in ShowTrak's execution view, leaving
     * the progress bar alone. Entirely optional — an event that never calls it
     * behaves exactly as before.
     *
     * Messages are trimmed and capped at 255 characters (longer ones are cut,
     * not rejected). Blank messages, calls after the ack has resolved, and
     * calls on a `hasFeedback = false` event are all ignored.
     */
    fun feedback(message: String)

    /** True once this ack has resolved, by success, error or timeout. */
    fun isResolved(): Boolean

    /** The current [AckStatus]. */
    fun getStatus(): AckStatus
}

/** High-level connection lifecycle, surfaced for optional UI. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    PENDING_ADOPTION,
    ONLINE,
}

/** Immutable snapshot of current SDK runtime state. */
data class SDKStatus(
    val connectionState: ConnectionState,
    val connected: Boolean,
    val adopted: Boolean,
    val clientId: String?,
    val hostname: String,
    val appVersion: String,
    val desiredState: String,
    val degradedMessage: String?,
    val serverHost: String?,
    val serverPort: Int?,
    val registeredEventIds: List<String>,
)
