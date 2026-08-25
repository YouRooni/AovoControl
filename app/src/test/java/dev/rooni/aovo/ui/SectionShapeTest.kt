package dev.rooni.aovo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import dev.rooni.aovo.ui.component.SectionDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Grouped containers only read as one block if the outward-facing corners are round and the
 * seams between tiles are square. These cases pin that rule down for both layouts.
 */
class SectionShapeTest {

    private fun RoundedCornerShape.corners() = listOf(topStart, topEnd, bottomStart, bottomEnd)

    private val outer = RoundedCornerShape(SectionDefaults.OuterCorner).topStart
    private val inner = RoundedCornerShape(SectionDefaults.InnerCorner).topStart

    @Test
    fun `a lone tile is round on every corner`() {
        assertEquals(listOf(outer, outer, outer, outer), SectionDefaults.columnShape(0, 1).corners())
    }

    @Test
    fun `a column rounds only the top of the first and the bottom of the last tile`() {
        val first = SectionDefaults.columnShape(0, 3)
        val middle = SectionDefaults.columnShape(1, 3)
        val last = SectionDefaults.columnShape(2, 3)

        assertEquals(listOf(outer, outer, inner, inner), first.corners())
        assertEquals(listOf(inner, inner, inner, inner), middle.corners())
        assertEquals(listOf(inner, inner, outer, outer), last.corners())
    }

    @Test
    fun `a grid rounds only the four corners of the whole block`() {
        val topLeft = SectionDefaults.gridShape(row = 0, column = 0, rows = 2, columns = 2)
        val topRight = SectionDefaults.gridShape(row = 0, column = 1, rows = 2, columns = 2)
        val bottomLeft = SectionDefaults.gridShape(row = 1, column = 0, rows = 2, columns = 2)
        val bottomRight = SectionDefaults.gridShape(row = 1, column = 1, rows = 2, columns = 2)

        assertEquals(listOf(outer, inner, inner, inner), topLeft.corners())
        assertEquals(listOf(inner, outer, inner, inner), topRight.corners())
        assertEquals(listOf(inner, inner, outer, inner), bottomLeft.corners())
        assertEquals(listOf(inner, inner, inner, outer), bottomRight.corners())
    }

    @Test
    fun `a middle row of a tall grid stays square all round`() {
        val middle = SectionDefaults.gridShape(row = 1, column = 0, rows = 3, columns = 2)
        assertEquals(listOf(inner, inner, inner, inner), middle.corners())
    }

    @Test
    fun `a single row grid keeps its outer left and right corners`() {
        val left = SectionDefaults.gridShape(row = 0, column = 0, rows = 1, columns = 2)
        val right = SectionDefaults.gridShape(row = 0, column = 1, rows = 1, columns = 2)
        assertEquals(listOf(outer, inner, outer, inner), left.corners())
        assertEquals(listOf(inner, outer, inner, outer), right.corners())
    }
}
