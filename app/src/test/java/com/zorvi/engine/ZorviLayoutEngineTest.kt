package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZorviLayoutEngineTest {
    private val engine = ZorviLayoutEngine(measureText = { it.length * 10f }, lineHeight = 20f)

    @Test fun blockChildrenStackVerticallyAndRespectMargins() {
        val root = ZorviLayoutNode(style = ZorviLayoutStyle(), children = listOf(
            ZorviLayoutNode(text = "one", style = ZorviLayoutStyle(height = 20f, marginBottom = 5f)),
            ZorviLayoutNode(text = "two", style = ZorviLayoutStyle(height = 30f))
        ))
        val box = engine.layout(root, 200f)
        assertEquals(0f, box.children[0].y, 0.01f)
        assertEquals(25f, box.children[1].y, 0.01f)
        assertEquals(55f, box.height, 0.01f)
    }

    @Test fun inlineTextWrapsToAvailableWidth() {
        val root = ZorviLayoutNode(children = listOf(
            ZorviLayoutNode(text = "one two three", style = ZorviLayoutStyle(display = ZorviDisplay.INLINE))
        ))
        val box = engine.layout(root, 60f)
        assertEquals(40f, box.children.single().height, 0.01f)
    }

    @Test fun flexRowDistributesGrowSpaceAndGap() {
        val root = ZorviLayoutNode(style = ZorviLayoutStyle(
            display = ZorviDisplay.FLEX, gap = 10f
        ), children = listOf(
            ZorviLayoutNode(style = ZorviLayoutStyle(width = 40f, height = 20f)),
            ZorviLayoutNode(style = ZorviLayoutStyle(width = 40f, height = 20f, flexGrow = 1f))
        ))
        val box = engine.layout(root, 200f)
        assertEquals(40f, box.children[0].width, 0.01f)
        assertEquals(150f, box.children[1].width, 0.01f)
        assertEquals(50f, box.children[1].x, 0.01f)
    }

    @Test fun flexColumnStacksItems() {
        val root = ZorviLayoutNode(style = ZorviLayoutStyle(
            display = ZorviDisplay.FLEX, flexDirection = ZorviFlexDirection.COLUMN, gap = 4f
        ), children = listOf(
            ZorviLayoutNode(style = ZorviLayoutStyle(height = 12f)),
            ZorviLayoutNode(style = ZorviLayoutStyle(height = 18f))
        ))
        val box = engine.layout(root, 100f)
        assertEquals(16f, box.children[1].y, 0.01f)
    }

    @Test fun gridPlacesChildrenInFixedColumns() {
        val root = ZorviLayoutNode(style = ZorviLayoutStyle(
            display = ZorviDisplay.GRID, gridColumns = 2, gap = 10f
        ), children = List(4) { ZorviLayoutNode(style = ZorviLayoutStyle(height = 25f)) })
        val box = engine.layout(root, 210f)
        assertEquals(100f, box.children[0].width, 0.01f)
        assertEquals(110f, box.children[1].x, 0.01f)
        assertEquals(25f, box.children[2].y, 0.01f)
        assertEquals(110f, box.children[3].x, 0.01f)
    }

    @Test fun displayNoneDoesNotProduceBoxes() {
        val root = ZorviLayoutNode(children = listOf(
            ZorviLayoutNode(text = "hidden", style = ZorviLayoutStyle(display = ZorviDisplay.NONE)),
            ZorviLayoutNode(text = "shown")
        ))
        val box = engine.layout(root, 100f)
        assertEquals(1, box.children.size)
        assertTrue(box.height > 0f)
    }
}
