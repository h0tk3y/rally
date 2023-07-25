package com.h0tk3y.rally

import java.util.*
import kotlin.collections.ArrayDeque

fun preprocessRoadmap(input: Collection<RoadmapInputLine>): Collection<RoadmapInputLine> = buildList {
    val syntheticQueue = TreeSet<PositionLine>(compareBy { it.atKm.valueKm })
    val ordinaryQueue = ArrayDeque(input)

    val existingDistances = input.filterIsInstance<PositionLine>().mapTo(mutableSetOf()) { it.atKm }
    val max = existingDistances.maxBy { it.valueKm }

    while (syntheticQueue.isNotEmpty() || ordinaryQueue.isNotEmpty()) {
        when (val e = ordinaryQueue.firstOrNull()) {
            null -> {
                addAll(syntheticQueue)
                syntheticQueue.clear()
            }

            is CommentLine -> add(ordinaryQueue.removeFirst())
            is PositionLine -> {
                when (val r = syntheticQueue.firstOrNull()) {
                    null -> add(ordinaryQueue.removeFirst())
                    else -> {
                        if (r.atKm.valueKm < e.atKm.valueKm) {
                            add(syntheticQueue.pollFirst()!!)
                        } else {
                            add(ordinaryQueue.removeFirst())
                        }
                    }
                }
                val addSyn = e.modifier<PositionLineModifier.AddSynthetic>()?.let { s ->
                    (1..s.count).map { id ->
                        PositionLine(
                            e.atKm + s.interval * id.toDouble(),
                            e.lineNumber.copy(subNumber = e.lineNumber.subNumber + id),
                            listOf(PositionLineModifier.IsSynthetic)
                        )
                    }.filter { it.atKm !in existingDistances && it.atKm.valueKm < max.valueKm }
                }.orEmpty()
                syntheticQueue.addAll(addSyn)
            }

            else -> error("unexpected line $e")
        }
    }
}