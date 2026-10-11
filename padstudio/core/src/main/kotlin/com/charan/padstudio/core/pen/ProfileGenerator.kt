package com.charan.padstudio.core.pen

import java.util.UUID

/**
 * Builds the Key Mapper groups and key maps for a pen profile.
 *
 * Layout: a root group "PadStudio Pen" with one child group per context. A context group's
 * constraints are "app in foreground" for each of its apps, OR'd. The "Everywhere else" group is
 * the AND of "app NOT in foreground" for every app in every other context, including pass-through
 * ones, so exactly one key map can match any (app, control, press). Key Mapper ANDs a key map's
 * constraints with all its parent groups' (DetectKeyMapsUseCase.processKeyMapsAndGroups).
 *
 * UIDs are name-based, so regenerating the same profile produces the same JSON.
 */
class ProfileGenerator(private val facts: DeviceFacts) {

    fun generate(profile: PenProfile, setup: PenSetup): GeneratedProfile {
        val warnings = mutableListOf<String>()
        val resolver = ActionResolver(facts, setup.mode)
        val contexts = profile.contexts.associateBy { it.id }
        val root = KmGroup(uid("group:root"), ROOT_GROUP_NAME, parentUid = null)
        val groups = mutableListOf(root)
        val keyMaps = mutableListOf<KmKeyMap>()

        val bindings = dedupe(profile.bindings, warnings)
        if (setup.mode == PenMode.BASIC) warnSwallowedShortPresses(bindings, warnings)

        val everyContextApp = profile.contexts.flatMap { it.packages }.distinct()
        for ((contextId, contextBindings) in bindings.groupBy { it.contextId }) {
            val context = contexts[contextId]
            val group = when {
                contextId == PenProfile.EVERYWHERE -> KmGroup(
                    uid = uid("group:${PenProfile.EVERYWHERE}"),
                    name = "Everywhere else",
                    parentUid = root.uid,
                    constraints = everyContextApp.map { KmConstraint.AppForeground(it, isNot = true) },
                    mode = ConstraintMode.AND,
                )
                context == null -> {
                    warnings += "Unknown context \"$contextId\", ${contextBindings.size} binding(s) skipped."
                    continue
                }
                context.passThrough -> {
                    warnings += "${context.title} is pass-through, so its ${contextBindings.size} binding(s) were ignored."
                    continue
                }
                context.packages.isEmpty() -> {
                    warnings += "${context.title} has no apps installed, so it was skipped."
                    continue
                }
                else -> KmGroup(
                    uid = uid("group:${context.id}"),
                    name = context.title,
                    parentUid = root.uid,
                    constraints = context.packages.map { KmConstraint.AppForeground(it) },
                    mode = ConstraintMode.OR,
                )
            }
            groups += group
            for (binding in contextBindings) {
                keyMaps += keyMapsFor(binding, group, context, setup, resolver, warnings)
            }
        }
        return GeneratedProfile(groups, keyMaps, warnings.distinct())
    }

    private fun keyMapsFor(
        binding: Binding,
        group: KmGroup,
        context: PenContext?,
        setup: PenSetup,
        resolver: ActionResolver,
        warnings: MutableList<String>,
    ): List<KmKeyMap> {
        val control = PenControl.byId(binding.control)
        val trigger = setup.controls[binding.control]
        if (trigger == null) {
            warnings += "${control.label} was not recorded, so \"${group.name}: ${binding.intent.label}\" was skipped."
            return emptyList()
        }
        if (trigger is PenTrigger.Evdev) {
            EvdevSafetyFilter.check(trigger)?.let {
                warnings += "Refused ${control.label}: $it."
                return emptyList()
            }
            if (setup.mode == PenMode.BASIC) {
                warnings += "${control.label} is an Expert-mode (evdev) trigger; it only works while Expert mode is running."
            }
        }

        val perAppPackages = if (binding.intent.perApp && context != null) context.packages else listOf(null)
        val multipleApps = context != null && context.packages.size > 1
        return perAppPackages.flatMap { packageName ->
            val resolution = resolver.resolve(binding.intent, packageName)
            warnings += resolution.warnings
            resolution.variants.map { variant ->
                val appConstraint = if (packageName != null && multipleApps) {
                    listOf(KmConstraint.AppForeground(packageName))
                } else {
                    emptyList()
                }
                KmKeyMap(
                    uid = uid("keymap:${binding.contextId}:${binding.control}:${binding.press}:${packageName ?: "*"}:${variant.key}"),
                    name = buildString {
                        append("${group.name} · ${control.label} ${binding.press.label} → ${binding.intent.label}")
                        if (packageName != null && multipleApps) append(" (${facts.appNames[packageName] ?: packageName})")
                        if (variant.key != "default") append(" [${variant.key}]")
                    },
                    groupUid = group.uid,
                    controlId = binding.control,
                    trigger = trigger,
                    press = binding.press,
                    actions = variant.actions,
                    constraints = appConstraint + variant.constraints,
                    mode = ConstraintMode.AND,
                )
            }
        }
    }

    private fun dedupe(bindings: List<Binding>, warnings: MutableList<String>): List<Binding> {
        bindings.groupBy { Triple(it.contextId, it.control, it.press) }
            .filterValues { it.size > 1 }
            .forEach { (key, duplicates) ->
                warnings += "${key.first}: ${PenControl.byId(key.second).label} ${key.third.label} was bound " +
                    "${duplicates.size} times; using \"${duplicates.first().intent.label}\"."
            }
        return bindings.distinctBy { Triple(it.contextId, it.control, it.press) }
    }

    /** In Basic mode a mapped long/double press with no mapped short press loses the short press. */
    private fun warnSwallowedShortPresses(bindings: List<Binding>, warnings: MutableList<String>) {
        bindings.groupBy { it.contextId to it.control }.forEach { (key, list) ->
            val presses = list.map { it.press }.toSet()
            if (Press.SHORT !in presses && presses.isNotEmpty()) {
                warnings += "${key.first}: only ${presses.joinToString { it.label }} of ${PenControl.byId(key.second).label} " +
                    "is mapped, so a plain press there will do nothing in Basic mode."
            }
        }
    }

    companion object {
        const val ROOT_GROUP_NAME = "PadStudio Pen"

        fun uid(name: String): String = UUID.nameUUIDFromBytes("padstudio:$name".toByteArray()).toString()
    }
}
