package sle.editor.project

import kotlinx.serialization.json.*

internal const val COLLISION_LAYER_COUNT = 16

internal data class CollisionLayers private constructor(
    private val labels: List<String>,
    private val blockedPairs: Set<Pair<Int, Int>>,
) {
    constructor() : this(List(COLLISION_LAYER_COUNT) { "" }, emptySet())

    fun customLabel(slot: Int) = labels[slot]
    fun label(slot: Int) = customLabel(slot).ifBlank { (slot + 1).toString() }

    fun withLabel(slot: Int, name: String) =
        copy(labels = labels.mapIndexed { index, current -> if (index == slot) name else current })

    fun allows(first: Int, second: Int) = pair(first, second) !in blockedPairs

    fun withPair(first: Int, second: Int, allowed: Boolean): CollisionLayers {
        val slots = pair(first, second)
        return copy(blockedPairs = if (allowed) blockedPairs - slots else blockedPairs + slots)
    }

    fun json() = buildJsonObject {
        val namedSlots = labels.dropLastWhile(String::isEmpty)
        if (namedSlots.isNotEmpty()) put("labels", JsonArray(namedSlots.map(::JsonPrimitive)))
        if (blockedPairs.isNotEmpty()) put("blockedPairs", buildJsonArray {
            blockedPairs.sortedWith(compareBy({ it.first }, { it.second })).forEach { (first, second) ->
                add(JsonArray(listOf(JsonPrimitive(first), JsonPrimitive(second))))
            }
        })
    }

    companion object {
        private fun pair(first: Int, second: Int) = minOf(first, second) to maxOf(first, second)

        fun parse(element: JsonElement): CollisionLayers {
            val value = element.jsonObject
            require(value.keys.all { it in setOf("labels", "blockedPairs") }) { "Unknown collision layer setting" }
            val names = value["labels"]?.jsonArray.orEmpty()
            require(names.size <= COLLISION_LAYER_COUNT) { "Expected up to 16 collision layer labels" }
            val labels = List(COLLISION_LAYER_COUNT) { slot ->
                if (slot >= names.size) "" else names[slot].jsonPrimitive.also {
                    require(it.isString) { "Expected collision layer label text" }
                }.content
            }
            fun slot(input: JsonElement): Int {
                val value = input.jsonPrimitive
                val number = value.intOrNull
                require(!value.isString && number != null && number in 0 until COLLISION_LAYER_COUNT) {
                    "Collision layer slots must be integers from 0 to 15"
                }
                return number
            }
            val blocked = value["blockedPairs"]?.jsonArray.orEmpty().map { entry ->
                val slots = entry.jsonArray
                require(slots.size == 2) { "Expected two slots in blocked collision pair" }
                pair(slot(slots[0]), slot(slots[1]))
            }
            return CollisionLayers(labels, blocked.toSet())
        }
    }
}
