package com.nova.engine.media

/**
 * Phase 10 engine-side models for media elements, form controls and accessibility.
 *
 * These classes are platform-neutral. They describe browser semantics and state;
 * they do not decode audio/video or invoke Android TalkBack by themselves.
 */

enum class MediaKind { AUDIO, VIDEO }
enum class MediaPreload { NONE, METADATA, AUTO }
enum class MediaPlaybackState { IDLE, PLAYING, PAUSED, ENDED, ERROR }

data class MediaSnapshot(
    val kind: MediaKind,
    val source: String? = null,
    val controls: Boolean = true,
    val autoplay: Boolean = false,
    val loop: Boolean = false,
    val muted: Boolean = false,
    val preload: MediaPreload = MediaPreload.METADATA,
    val state: MediaPlaybackState = MediaPlaybackState.IDLE,
    val currentTimeSeconds: Double = 0.0,
    val durationSeconds: Double? = null,
    val volume: Double = 1.0,
    val errorMessage: String? = null
) {
    init {
        require(currentTimeSeconds.isFinite() && currentTimeSeconds >= 0.0)
        require(durationSeconds == null || (durationSeconds.isFinite() && durationSeconds >= 0.0))
        require(volume.isFinite() && volume in 0.0..1.0)
    }

    val hasSource: Boolean get() = !source.isNullOrBlank()
    val canSeek: Boolean get() = durationSeconds != null && durationSeconds > 0.0

    fun play(): MediaSnapshot =
        if (hasSource && state != MediaPlaybackState.ERROR) copy(state = MediaPlaybackState.PLAYING)
        else this

    fun pause(): MediaSnapshot =
        if (state == MediaPlaybackState.PLAYING) copy(state = MediaPlaybackState.PAUSED) else this

    fun seekTo(seconds: Double): MediaSnapshot {
        require(seconds.isFinite() && seconds >= 0.0)
        val bounded = durationSeconds?.let { seconds.coerceAtMost(it) } ?: seconds
        return copy(
            currentTimeSeconds = bounded,
            state = if (durationSeconds != null && bounded >= durationSeconds) {
                MediaPlaybackState.ENDED
            } else if (state == MediaPlaybackState.ENDED) {
                MediaPlaybackState.PAUSED
            } else state
        )
    }

    fun withVolume(value: Double): MediaSnapshot {
        require(value.isFinite())
        return copy(volume = value.coerceIn(0.0, 1.0), muted = value <= 0.0)
    }

    fun markEnded(): MediaSnapshot =
        if (loop && durationSeconds != null) copy(
            currentTimeSeconds = 0.0,
            state = MediaPlaybackState.PLAYING
        ) else copy(
            currentTimeSeconds = durationSeconds ?: currentTimeSeconds,
            state = MediaPlaybackState.ENDED
        )

    fun markError(message: String): MediaSnapshot =
        copy(state = MediaPlaybackState.ERROR, errorMessage = message)
}

enum class FormControlKind { TEXT, SEARCH, EMAIL, URL, PASSWORD, NUMBER, CHECKBOX, RADIO, SELECT, TEXTAREA, BUTTON, SUBMIT }

data class FormControlState(
    val kind: FormControlKind,
    val name: String = "",
    val value: String = "",
    val required: Boolean = false,
    val disabled: Boolean = false,
    val checked: Boolean = false,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: Regex? = null
) {
    fun validate(): List<String> {
        if (disabled) return emptyList()
        val errors = mutableListOf<String>()
        if (required && (if (kind == FormControlKind.CHECKBOX || kind == FormControlKind.RADIO) !checked else value.isBlank())) {
            errors += "This field is required."
        }
        if (value.isNotEmpty() && minLength != null && value.length < minLength) {
            errors += "Enter at least $minLength characters."
        }
        if (value.isNotEmpty() && maxLength != null && value.length > maxLength) {
            errors += "Enter no more than $maxLength characters."
        }
        if (value.isNotEmpty() && pattern != null && !pattern.matches(value)) {
            errors += "The value does not match the required format."
        }
        if (value.isNotEmpty() && kind == FormControlKind.EMAIL &&
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)) {
            errors += "Enter a valid email address."
        }
        if (value.isNotEmpty() && kind == FormControlKind.URL &&
            !Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value)) {
            errors += "Enter a valid URL including its scheme."
        }
        return errors
    }
}

enum class AccessibleRole {
    GENERIC, DOCUMENT, MAIN, NAVIGATION, REGION, HEADING, PARAGRAPH,
    LINK, BUTTON, TEXT_FIELD, SEARCH_FIELD, CHECKBOX, RADIO, COMBO_BOX,
    IMAGE, VIDEO, AUDIO, LIST, LIST_ITEM, FORM
}

enum class AccessibleAction { CLICK, FOCUS, SET_TEXT, TOGGLE, SELECT, PLAY, PAUSE, SEEK }

data class AccessibleNode(
    val role: AccessibleRole,
    val label: String = "",
    val description: String? = null,
    val value: String? = null,
    val headingLevel: Int? = null,
    val disabled: Boolean = false,
    val checked: Boolean? = null,
    val required: Boolean = false,
    val invalid: Boolean = false,
    val hidden: Boolean = false,
    val actions: Set<AccessibleAction> = emptySet(),
    val children: List<AccessibleNode> = emptyList()
)

/**
 * Minimal DOM-facing contract. The parser/DOM adapter can implement this without
 * depending on Android UI classes.
 */
interface AccessibleElement {
    val tagName: String
    val textContent: String get() = ""
    val attributes: Map<String, String> get() = emptyMap()
    val children: List<AccessibleElement> get() = emptyList()
}

class AccessibilityTreeBuilder {
    fun build(root: AccessibleElement): AccessibleNode? = buildNode(root)

    private fun buildNode(element: AccessibleElement): AccessibleNode? {
        val attrs = element.attributes
        val ariaHidden = attrs["aria-hidden"].equals("true", ignoreCase = true)
        val hidden = attrs.containsKey("hidden") || attrs["style"]?.contains("display:none", true) == true
        if (ariaHidden || hidden) return null

        val tag = element.tagName.lowercase()
        val role = explicitRole(attrs["role"]) ?: inferRole(tag, attrs)
        val label = firstNonBlank(
            attrs["aria-label"],
            attrs["alt"].takeIf { tag == "img" },
            attrs["title"],
            attrs["placeholder"],
            attrs["value"].takeIf { tag == "button" || tag == "input" },
            element.textContent
        )
        val description = attrs["aria-description"] ?: attrs["title"]
        val level = if (role == AccessibleRole.HEADING) {
            tag.removePrefix("h").toIntOrNull()?.coerceIn(1, 6)
        } else null
        val disabled = attrs.containsKey("disabled") ||
            attrs["aria-disabled"].equals("true", ignoreCase = true)
        val checked = when {
            attrs.containsKey("checked") -> true
            attrs["aria-checked"] == "true" -> true
            attrs["aria-checked"] == "false" -> false
            else -> null
        }
        val required = attrs.containsKey("required") ||
            attrs["aria-required"].equals("true", ignoreCase = true)
        val invalid = attrs["aria-invalid"].equals("true", ignoreCase = true)
        val value = attrs["value"] ?: attrs["aria-valuetext"]
        val actions = actionsFor(role, disabled)
        val childNodes = element.children.mapNotNull(::buildNode)

        // An image without alternative text is decorative and should not be announced.
        if (tag == "img" && label.isBlank()) return null

        return AccessibleNode(
            role = role,
            label = label,
            description = description,
            value = value,
            headingLevel = level,
            disabled = disabled,
            checked = checked,
            required = required,
            invalid = invalid,
            actions = actions,
            children = childNodes
        )
    }

    private fun inferRole(tag: String, attrs: Map<String, String>): AccessibleRole = when {
        tag.matches(Regex("h[1-6]")) -> AccessibleRole.HEADING
        tag == "a" && !attrs["href"].isNullOrBlank() -> AccessibleRole.LINK
        tag == "button" || (tag == "input" && attrs["type"] in setOf("button", "submit", "reset")) -> AccessibleRole.BUTTON
        tag == "input" && attrs["type"] == "checkbox" -> AccessibleRole.CHECKBOX
        tag == "input" && attrs["type"] == "radio" -> AccessibleRole.RADIO
        tag == "input" && attrs["type"] == "search" -> AccessibleRole.SEARCH_FIELD
        tag == "input" || tag == "textarea" -> AccessibleRole.TEXT_FIELD
        tag == "select" -> AccessibleRole.COMBO_BOX
        tag == "img" -> AccessibleRole.IMAGE
        tag == "video" -> AccessibleRole.VIDEO
        tag == "audio" -> AccessibleRole.AUDIO
        tag == "form" -> AccessibleRole.FORM
        tag == "main" -> AccessibleRole.MAIN
        tag == "nav" -> AccessibleRole.NAVIGATION
        tag == "section" -> AccessibleRole.REGION
        tag == "p" -> AccessibleRole.PARAGRAPH
        tag == "ul" || tag == "ol" -> AccessibleRole.LIST
        tag == "li" -> AccessibleRole.LIST_ITEM
        tag == "html" || tag == "body" -> AccessibleRole.DOCUMENT
        else -> AccessibleRole.GENERIC
    }

    private fun explicitRole(value: String?): AccessibleRole? = when (value?.lowercase()) {
        "main" -> AccessibleRole.MAIN
        "navigation" -> AccessibleRole.NAVIGATION
        "region" -> AccessibleRole.REGION
        "heading" -> AccessibleRole.HEADING
        "link" -> AccessibleRole.LINK
        "button" -> AccessibleRole.BUTTON
        "textbox" -> AccessibleRole.TEXT_FIELD
        "searchbox" -> AccessibleRole.SEARCH_FIELD
        "checkbox" -> AccessibleRole.CHECKBOX
        "radio" -> AccessibleRole.RADIO
        "combobox" -> AccessibleRole.COMBO_BOX
        "img" -> AccessibleRole.IMAGE
        "list" -> AccessibleRole.LIST
        "listitem" -> AccessibleRole.LIST_ITEM
        "form" -> AccessibleRole.FORM
        else -> null
    }

    private fun actionsFor(role: AccessibleRole, disabled: Boolean): Set<AccessibleAction> {
        if (disabled) return emptySet()
        return when (role) {
            AccessibleRole.LINK, AccessibleRole.BUTTON -> setOf(AccessibleAction.CLICK, AccessibleAction.FOCUS)
            AccessibleRole.TEXT_FIELD, AccessibleRole.SEARCH_FIELD -> setOf(AccessibleAction.FOCUS, AccessibleAction.SET_TEXT)
            AccessibleRole.CHECKBOX, AccessibleRole.RADIO -> setOf(AccessibleAction.FOCUS, AccessibleAction.TOGGLE)
            AccessibleRole.COMBO_BOX -> setOf(AccessibleAction.FOCUS, AccessibleAction.SELECT)
            AccessibleRole.VIDEO, AccessibleRole.AUDIO -> setOf(
                AccessibleAction.FOCUS, AccessibleAction.PLAY, AccessibleAction.PAUSE, AccessibleAction.SEEK
            )
            else -> emptySet()
        }
    }

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
}
