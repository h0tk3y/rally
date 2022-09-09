package com.h0tk3y.rally

interface ModifiersValidator {
    data class Problem(val message: String?)

    fun validateModifiers(modifiers: Collection<PositionLineModifier>): Problem?
}

class DefaultModifierValidator : ModifiersValidator {
    override fun validateModifiers(modifiers: Collection<PositionLineModifier>): ModifiersValidator.Problem? {
        val avg = modifiers.filter { it is PositionLineModifier.SetAvg || it is PositionLineModifier.EndAvg }
        if (avg.size > 1) {
            return ModifiersValidator.Problem("more than one setavg/endavg modifiers found")
        }
        
        val here = modifiers.filterIsInstance<PositionLineModifier.Here>()
        if (here.size > 1) {
            return ModifiersValidator.Problem("more than one here modifier found")
        }
        
        return null
    }
}
