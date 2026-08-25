package dev.rooni.aovo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout is an ordered list packed into a fixed-column grid, which is what makes
 * reordering safe: there is no way to leave a hole or overlap two tiles.
 */
class DashboardLayoutTest {

    private fun tile(id: String, span: Int = 1, type: TileType = TileType.Speed) =
        DashboardTile(id = id, type = type, span = span)

    private fun layout(vararg tiles: DashboardTile) = DashboardLayout(tiles.toList())

    @Test
    fun `tiles share a row until the columns run out`() {
        val rows = layout(tile("a", span = 2), tile("b", span = 2)).rows()
        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b"), rows[0].map { it.id })
    }

    @Test
    fun `a full width tile takes a row of its own`() {
        val rows = layout(tile("a", span = 2), tile("wide", span = 4), tile("b", span = 2)).rows()
        assertEquals(3, rows.size)
        assertEquals(listOf("a"), rows[0].map { it.id })
        assertEquals(listOf("wide"), rows[1].map { it.id })
        assertEquals(listOf("b"), rows[2].map { it.id })
    }

    @Test
    fun `no row ever exceeds the column count`() {
        val many = (1..9).map { tile("t$it", span = if (it % 3 == 0) 4 else 2) }
        DashboardLayout(many).rows().forEach { row ->
            assertTrue(row.sumOf { it.clampedSpan() } <= DashboardLayout.COLUMNS)
        }
    }

    @Test
    fun `packing never loses or duplicates a tile`() {
        val many = (1..11).map { tile("t$it", span = if (it % 4 == 0) 4 else 2) }
        val packed = DashboardLayout(many).rows().flatten().map { it.id }
        assertEquals(many.map { it.id }, packed)
    }

    @Test
    fun `moving a tile shifts it by one position`() {
        val moved = layout(tile("a"), tile("b"), tile("c")).move("b", -1)
        assertEquals(listOf("b", "a", "c"), moved.tiles.map { it.id })
    }

    @Test
    fun `moving past the ends is clamped, not wrapped`() {
        val start = layout(tile("a"), tile("b"))
        assertEquals(listOf("a", "b"), start.move("a", -1).tiles.map { it.id })
        assertEquals(listOf("a", "b"), start.move("b", 1).tiles.map { it.id })
    }

    @Test
    fun `moving an unknown id changes nothing`() {
        val start = layout(tile("a"), tile("b"))
        assertEquals(start, start.move("nope", 1))
    }

    @Test
    fun `dragging a tile to a new index reinserts it there`() {
        val start = layout(tile("a"), tile("b"), tile("c"), tile("d"))
        assertEquals(
            listOf("b", "c", "a", "d"),
            start.moveTo(0, 2).tiles.map { it.id },
        )
        assertEquals(
            listOf("a", "d", "b", "c"),
            start.moveTo(3, 1).tiles.map { it.id },
        )
    }

    @Test
    fun `dragging onto itself or out of range changes nothing`() {
        val start = layout(tile("a"), tile("b"))
        assertEquals(start, start.moveTo(0, 0))
        assertEquals(start, start.moveTo(5, 1))
        assertEquals(listOf("b", "a"), start.moveTo(0, 9).tiles.map { it.id })
    }

    @Test
    fun `a drag never loses a tile`() {
        var current = layout(tile("a"), tile("b"), tile("c"), tile("d"), tile("e"))
        repeat(20) { step ->
            current = current.moveTo(step % 5, (step * 3) % 5)
            assertEquals(5, current.tiles.size)
            assertEquals(5, current.tiles.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `resizing snaps to a width the tile type allows`() {
        val metric = layout(tile("a", type = TileType.Speed))
        assertEquals(DashboardLayout.COLUMNS, metric.resize("a", 99).tiles[0].clampedSpan())
        assertEquals(1, metric.resize("a", 0).tiles[0].clampedSpan())

        // The connection card has only two states, so anything else snaps to one of them.
        val connection = DashboardLayout(listOf(DashboardTile("b", TileType.Connection)))
        assertEquals(listOf(2, 4), TileType.Connection.allowedSpans)
        assertEquals(4, connection.resize("b", 3).tiles[0].clampedSpan())
        assertEquals(2, connection.resize("b", 1).tiles[0].clampedSpan())
    }

    @Test
    fun `the speedometer and mode selector are always full width`() {
        listOf(TileType.Gauge, TileType.ModeSelector).forEach { type ->
            val single = DashboardLayout(listOf(DashboardTile("x", type)))
            assertEquals(DashboardLayout.COLUMNS, single.resize("x", 1).tiles[0].clampedSpan())
            assertTrue(!type.canResizeWidth)
        }
    }

    @Test
    fun `stepping width stops at the ends instead of wrapping`() {
        val tile = DashboardTile("a", TileType.Speed, span = 1)
        assertEquals(1, tile.stepSpan(-1))
        assertEquals(2, tile.stepSpan(1))
        assertEquals(4, tile.copy(span = 4).stepSpan(1))
    }

    @Test
    fun `tapping width wraps round through every allowed state`() {
        var tile = DashboardTile("a", TileType.Speed, span = 1)
        val seen = mutableListOf(tile.clampedSpan())
        repeat(TileType.Speed.allowedSpans.size) {
            tile = tile.copy(span = tile.nextSpan())
            seen.add(tile.clampedSpan())
        }
        assertEquals(seen.first(), seen.last())
        assertEquals(TileType.Speed.allowedSpans.toSet(), seen.toSet())
    }

    @Test
    fun `only tiles that can use the room may change height`() {
        assertTrue(TileType.Gauge.canResizeHeight)
        assertTrue(TileType.Spacer.canResizeHeight)
        listOf(TileType.Speed, TileType.Lock, TileType.Connection).forEach {
            assertTrue(it.name, !it.canResizeHeight)
        }
    }

    @Test
    fun `small tiles default to half a row and wide ones to the whole row`() {
        listOf(
            TileType.Speed, TileType.Battery, TileType.Power, TileType.Lock,
            TileType.Headlight, TileType.Profile,
        ).forEach { assertEquals(it.name, 2, DashboardTile("x", it).clampedSpan()) }

        listOf(TileType.Connection, TileType.Gauge, TileType.ModeSelector).forEach {
            assertEquals(it.name, DashboardLayout.COLUMNS, DashboardTile("x", it).clampedSpan())
        }
    }

    @Test
    fun `the connection card toggles between half and full width`() {
        var tile = DashboardTile("c", TileType.Connection)
        assertEquals(4, tile.clampedSpan())
        tile = tile.copy(span = tile.nextSpan())
        assertEquals(2, tile.clampedSpan())
        tile = tile.copy(span = tile.nextSpan())
        assertEquals(4, tile.clampedSpan())
    }

    @Test
    fun `a stored width that is no longer allowed heals to a valid one`() {
        // Layouts saved before the connection card lost its three-column state.
        val stale = DashboardLayout.decode(
            """[{"id":"c","type":"connection","span":3,"height":"m"}]"""
        )
        assertEquals(4, stale.tiles[0].clampedSpan())
    }

    @Test
    fun `every metric tile can reach all four widths`() {
        listOf(TileType.Speed, TileType.Odometer, TileType.Spacer).forEach { type ->
            assertEquals(type.name, listOf(1, 2, 3, 4), type.allowedSpans)
        }
    }

    @Test
    fun `a caption is dropped only at the narrowest width`() {
        val tile = DashboardTile("a", TileType.Lock, span = 1)
        assertTrue(!tile.showsLabel)
        assertTrue(tile.copy(span = 2).showsLabel)
    }

    @Test
    fun `height can be set to any step a drag lands on`() {
        val gauge = DashboardLayout(listOf(DashboardTile("a", TileType.Gauge)))
        assertEquals(6, gauge.setHeight("a", 6).tiles[0].clampedHeight())
        assertEquals(TileHeights.MAX, gauge.setHeight("a", 99).tiles[0].clampedHeight())
    }

    @Test
    fun `the speedometer cannot be shrunk into the sizes that are too cramped to read`() {
        val floor = TileHeights.minFor(TileType.Gauge)
        assertEquals(3, floor)

        val gauge = DashboardLayout(listOf(DashboardTile("a", TileType.Gauge)))
        assertEquals(floor, gauge.setHeight("a", 1).tiles[0].clampedHeight())
        assertEquals(floor, gauge.setHeight("a", -4).tiles[0].clampedHeight())

        // A layout saved when those sizes still existed heals to the smallest usable one.
        val stale = DashboardLayout.decode(
            """[{"id":"g","type":"gauge","span":4,"hstep":1}]"""
        )
        assertEquals(floor, stale.tiles[0].clampedHeight())
    }

    @Test
    fun `a spacer may still be as short as one step`() {
        val spacer = DashboardLayout(listOf(DashboardTile("s", TileType.Spacer)))
        assertEquals(TileHeights.MIN, spacer.setHeight("s", 1).tiles[0].clampedHeight())
    }

    @Test
    fun `height cycles through every usable step and wraps`() {
        val floor = TileHeights.minFor(TileType.Gauge)
        var current = DashboardLayout(
            listOf(DashboardTile("a", TileType.Gauge, heightStep = floor))
        )
        val seen = mutableListOf(current.tiles[0].clampedHeight())
        repeat(TileHeights.MAX - floor + 1) {
            current = current.cycleHeight("a")
            seen.add(current.tiles[0].clampedHeight())
        }
        assertEquals(floor, seen.last())
        assertEquals((floor..TileHeights.MAX).toSet(), seen.toSet())
    }

    @Test
    fun `a fixed height tile ignores height changes`() {
        val card = DashboardLayout(listOf(DashboardTile("s", TileType.Speed, heightStep = 1)))
        assertEquals(1, card.setHeight("s", 5).tiles[0].clampedHeight())
        assertEquals(1, card.cycleHeight("s").tiles[0].clampedHeight())
    }

    @Test
    fun `a legacy named height is read back as a step`() {
        val decoded = DashboardLayout.decode(
            """[{"id":"s","type":"spacer","span":4,"height":"l"}]"""
        )
        assertEquals(3, decoded.tiles[0].clampedHeight())
    }

    @Test
    fun `spacers can be added over and over`() {
        var current = DashboardLayout(emptyList())
        repeat(5) { current = current.add(TileType.Spacer) }
        assertEquals(5, current.tiles.size)
        assertEquals(5, current.tiles.map { it.id }.toSet().size)
    }

    @Test
    fun `removing a tile drops exactly that one`() {
        val after = layout(tile("a"), tile("b"), tile("c")).remove("b")
        assertEquals(listOf("a", "c"), after.tiles.map { it.id })
    }

    @Test
    fun `tiles pointing at a deleted profile are pruned`() {
        val start = DashboardLayout(
            listOf(
                tile("keep"),
                DashboardTile("p1", TileType.Profile, profileId = "alive"),
                DashboardTile("p2", TileType.Profile, profileId = "gone"),
            )
        )
        val pruned = start.pruneProfiles(setOf("alive"))
        assertEquals(listOf("keep", "p1"), pruned.tiles.map { it.id })
    }

    @Test
    fun `a layout survives an encode decode round trip`() {
        val original = DashboardLayout(
            listOf(
                DashboardTile("g", TileType.Gauge, span = 4, heightStep = 5),
                DashboardTile("s", TileType.Spacer, span = 1, heightStep = 2),
                DashboardTile("p", TileType.Profile, span = 2, profileId = "abc"),
            )
        )
        assertEquals(original, DashboardLayout.decode(original.encode()))
    }

    @Test
    fun `garbage and emptiness fall back to the default layout`() {
        assertEquals(DashboardLayout.Default, DashboardLayout.decode(null))
        assertEquals(DashboardLayout.Default, DashboardLayout.decode(""))
        assertEquals(DashboardLayout.Default, DashboardLayout.decode("not json"))
        assertEquals(DashboardLayout.Default, DashboardLayout.decode("[]"))
    }

    @Test
    fun `unknown tile types are dropped rather than crashing`() {
        val raw = """[{"id":"x","type":"from_the_future","span":1,"height":"m"},""" +
            """{"id":"y","type":"speed","span":1,"height":"m"}]"""
        val decoded = DashboardLayout.decode(raw)
        assertEquals(listOf("y"), decoded.tiles.map { it.id })
    }

    @Test
    fun `generated ids are unique`() {
        val ids = (1..50).map { DashboardLayout.newId(TileType.Spacer) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the default layout leads with connection and gauge`() {
        val types = DashboardLayout.Default.tiles.map { it.type }
        assertEquals(TileType.Connection, types[0])
        assertEquals(TileType.Gauge, types[1])
        assertNotEquals(0, DashboardLayout.Default.tiles.size)
    }
}
