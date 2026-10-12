package com.charan.padstudio.pen.screen

import com.charan.padstudio.core.pen.Binding
import com.charan.padstudio.core.pen.PenContext
import com.charan.padstudio.core.pen.PenIntent
import com.charan.padstudio.core.pen.PenProfile

// The "Needs a look" chip (plan §10): the profile generator's warnings (GeneratedProfile.warnings, plain
// strings from :core), sorted to the context they concern and put in plain words. The generator's own wording
// stays available as "Original", and Developer details lists every warning raw.

/** A core warning as the screen shows it: [plain] words, and the generator's [original] text. */
data class PenWarning(val plain: String, val original: String)

/**
 * The warnings that concern [context], in the generator's order. A warning belongs to a context when it names
 * the context's key-map group ("\"Camera: Shutter\" was skipped"), starts with its title or id, names one of its
 * apps, or (for "No notes app chosen") when the context binds "Open notes app". "Undo in this context" comes
 * from the one context without apps of its own: "Everywhere else".
 */
fun warningsFor(context: PenContext, warnings: List<String>, bindings: List<Binding>): List<String> =
    warnings.filter { belongsTo(it, context, bindings) }

/** The warnings no listed context claims (a refused or Expert-only trigger, say): Developer details shows them. */
fun penWideWarnings(contexts: List<PenContext>, warnings: List<String>, bindings: List<Binding>): List<String> =
    warnings.filter { warning -> contexts.none { belongsTo(warning, it, bindings) } }

/** [raw] in plain words, naming apps by [appName] (their label) instead of package ids; unknown wording is kept. */
fun plainWarning(raw: String, appName: (String) -> String?): String {
    fun app(packageName: String) = appName(packageName) ?: packageName
    NOT_RECORDED.matchEntire(raw)?.let { m ->
        val (control, _, action) = m.destructured
        return "$control isn't recorded yet, so “$action” does nothing here."
    }
    NO_SHUTTER.find(raw)?.let { m -> return "Shutter isn't set up for ${app(m.groupValues[1])} yet, so it does nothing there." }
    NOT_CALIBRATED.matchEntire(raw)?.let { m ->
        val (packageName, orientation) = m.destructured
        return "Shutter in ${app(packageName)} isn't set up for $orientation, so it does nothing there."
    }
    VOLUME_SHUTTER.matchEntire(raw)?.let { m ->
        return "Shutter in ${app(m.groupValues[1])} sends Volume down, which isn't checked for this app yet."
    }
    NEEDS_INJECTION.find(raw)?.let { m ->
        val (action, where) = m.destructured
        val place = if (where == THIS_CONTEXT) "here" else "in ${app(where)}"
        return "$action $place needs Expert setup with “Key Mapper can press keys” on, or a recorded $action button."
    }
    if (raw.startsWith(NO_NOTES_APP)) return "“Open notes app” does nothing until you choose a notes app under Advanced."
    DUPLICATE.matchEntire(raw)?.let { m ->
        val (_, gesture, times, action) = m.destructured
        return "$gesture has $times actions here, so only “$action” is used."
    }
    SWALLOWED.matchEntire(raw)?.let { m ->
        val (_, presses, control) = m.destructured
        return "Only the $presses of $control has an action here, so a single $control press does nothing in Basic setup."
    }
    return raw
}

/** [warnings] for one context, each in plain words with its original. */
fun penWarnings(warnings: List<String>, appName: (String) -> String?): List<PenWarning> =
    warnings.map { PenWarning(plainWarning(it, appName), it) }

private fun belongsTo(raw: String, context: PenContext, bindings: List<Binding>): Boolean = when {
    "\"${context.title}: " in raw -> true
    raw.startsWith("${context.title} ") -> true
    raw.startsWith("${context.id}: ") -> true
    context.packages.any { mentionsPackage(raw, it) } -> true
    context.id == PenProfile.EVERYWHERE && " in $THIS_CONTEXT " in raw -> true
    raw.startsWith(NO_NOTES_APP) -> bindings.any { it.contextId == context.id && it.intent == PenIntent.OPEN_NOTES }
    else -> false
}

/** [packageName] as a whole word: "for com.android.camera:" yes, "com.android.camera2" or "com.android.camera.x" no. */
private fun mentionsPackage(raw: String, packageName: String): Boolean =
    Regex("(^|[\\s(])${Regex.escape(packageName)}(?=[\\s:,)]|\\.(\\s|$)|$)").containsMatchIn(raw)

// The generator's wording (core/pen/ProfileGenerator.kt and ActionResolver.kt).
private val NOT_RECORDED = Regex("(.+) was not recorded, so \"(.+?): (.+)\" was skipped\\.")
private val NO_SHUTTER = Regex("^No shutter known for (\\S+?): ")
private val NOT_CALIBRATED = Regex("Shutter for (\\S+) is not calibrated in (\\w+), so presses there do nothing\\.")
private val VOLUME_SHUTTER = Regex("Shutter for (\\S+) sends Volume Down, which is not verified for this app yet\\.")
private val NEEDS_INJECTION = Regex("^(Undo|Redo) in (.+?) needs key injection")
private val DUPLICATE = Regex("(\\S+): (.+) was bound (\\d+) times; using \"(.+)\"\\.")
private val SWALLOWED = Regex("(\\S+): only (.+) of (.+) is mapped, so a plain press there will do nothing in Basic mode\\.")
private const val NO_NOTES_APP = "No notes app chosen"
private const val THIS_CONTEXT = "this context"
