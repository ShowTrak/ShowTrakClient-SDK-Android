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
 */
data class EventOptions @JvmOverloads constructor(
    val label: String? = null,
    val colour: Int = 7,
    val hasFeedback: Boolean = true,
    val icon: String? = null,
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

/**
 * Passed to an event handler when an operator triggers the event. The handler
 * must call exactly one of [success] / [error] when it is done (for
 * [EventOptions.hasFeedback] events; for fire-and-forget events the calls are
 * harmless no-ops).
 */
interface Ack {
    val requestId: String
    val eventId: String
    fun success()
    fun error(message: String)
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
