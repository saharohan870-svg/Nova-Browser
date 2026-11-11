package com.zorvi.engine

import kotlin.math.max
import kotlin.math.min

/**
 * First layout subsystem for Zorvi Engine.
 *
 * Geometry is expressed in CSS px. This module is deliberately platform-neutral:
 * Android Canvas is only a consumer of LayoutBox output, never part of layout.
 * Supported subset: normal block flow, wrapped inline runs, single-line flex
 * layout (row/column, grow, gap, justify/align) and fixed-column CSS Grid.
 * This is not yet a complete CSS Formatting Context implementation.
 */
internal enum class ZorviDisplay { NONE, BLOCK, INLINE, FLEX, GRID }
internal enum class ZorviFlexDirection { ROW, COLUMN }
internal enum class ZorviJustify { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND }
internal enum class ZorviAlign { START, CENTER, END, STRETCH }

internal data class ZorviLayoutStyle(
    val display: ZorviDisplay = ZorviDisplay.BLOCK,
    val width: Float? = null,
    val height: Float? = null,
    val minWidth: Float = 0f,
    val minHeight: Float = 0f,
    val marginLeft: Float = 0f,
    val marginTop: Float = 0f,
    val marginRight: Float = 0f,
    val marginBottom: Float = 0f,
    val paddingLeft: Float = 0f,
    val paddingTop: Float = 0f,
    val paddingRight: Float = 0f,
    val paddingBottom: Float = 0f,
    val gap: Float = 0f,
    val flexDirection: ZorviFlexDirection = ZorviFlexDirection.ROW,
    val flexGrow: Float = 0f,
    val justifyContent: ZorviJustify = ZorviJustify.START,
    val alignItems: ZorviAlign = ZorviAlign.STRETCH,
    val gridColumns: Int = 1
)

internal data class ZorviLayoutNode(
    val text: String = "",
    val style: ZorviLayoutStyle = ZorviLayoutStyle(),
    val children: List<ZorviLayoutNode> = emptyList()
)

internal data class ZorviLayoutBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val children: List<ZorviLayoutBox> = emptyList()
)

internal class ZorviLayoutEngine(
    private val measureText: (String) -> Float = { it.length * 7f },
    private val lineHeight: Float = 18f
) {
    fun layout(root: ZorviLayoutNode, viewportWidth: Float): ZorviLayoutBox {
        require(viewportWidth >= 0f && viewportWidth.isFinite()) { "Invalid viewport width" }
        return layoutNode(root, 0f, 0f, viewportWidth, null)
    }

    private fun layoutNode(
        node: ZorviLayoutNode,
        x: Float,
        y: Float,
        availableWidth: Float,
        forcedHeight: Float?
    ): ZorviLayoutBox {
        val s = node.style
        if (s.display == ZorviDisplay.NONE) return ZorviLayoutBox(x, y, 0f, 0f)
        val outerWidth = (s.width ?: availableWidth).coerceAtLeast(s.minWidth)
            .coerceAtMost(max(availableWidth, s.minWidth))
        val contentWidth = max(0f, outerWidth - s.paddingLeft - s.paddingRight)
        val contentX = x + s.paddingLeft
        val contentY = y + s.paddingTop
        val children = when (s.display) {
            ZorviDisplay.FLEX -> layoutFlex(node.children, contentX, contentY, contentWidth, s)
            ZorviDisplay.GRID -> layoutGrid(node.children, contentX, contentY, contentWidth, s)
            else -> layoutFlow(node.children, contentX, contentY, contentWidth)
        }
        val contentHeight = children.maxOfOrNull { it.y + it.height - contentY } ?: 0f
        val ownTextHeight = if (node.text.isBlank()) 0f else measureInlineText(node.text, contentWidth).second
        val naturalHeight = max(contentHeight, ownTextHeight) + s.paddingTop + s.paddingBottom
        val height = (forcedHeight ?: s.height ?: naturalHeight).coerceAtLeast(s.minHeight)
        return ZorviLayoutBox(x, y, outerWidth, height, children)
    }

    private fun layoutFlow(
        nodes: List<ZorviLayoutNode>,
        left: Float,
        top: Float,
        width: Float
    ): List<ZorviLayoutBox> {
        val result = mutableListOf<ZorviLayoutBox>()
        var cursorY = top
        var inlineX = left
        var inlineY = top
        var inlineLineHeight = lineHeight

        fun flushInline() {
            if (inlineX > left || inlineLineHeight > lineHeight) {
                cursorY = max(cursorY, inlineY + inlineLineHeight)
            }
            inlineX = left
            inlineY = cursorY
            inlineLineHeight = lineHeight
        }

        nodes.forEach { child ->
            if (child.style.display == ZorviDisplay.NONE) return@forEach
            if (child.style.display == ZorviDisplay.INLINE) {
                val (textWidth, textHeight) = measureInlineText(child.text, width)
                val marginLeft = child.style.marginLeft
                val marginRight = child.style.marginRight
                val required = textWidth + marginLeft + marginRight
                if (inlineX > left && inlineX + required > left + width) flushInline()
                val boxWidth = min(textWidth, max(0f, width))
                val box = ZorviLayoutBox(inlineX + marginLeft, inlineY, boxWidth, textHeight)
                result += box
                inlineX += required
                inlineLineHeight = max(inlineLineHeight, textHeight)
            } else {
                flushInline()
                val childX = left + child.style.marginLeft
                val childY = cursorY + child.style.marginTop
                val childWidth = max(0f, width - child.style.marginLeft - child.style.marginRight)
                val box = layoutNode(child, childX, childY, childWidth, null)
                result += box
                cursorY = box.y + box.height + child.style.marginBottom
                inlineY = cursorY
            }
        }
        flushInline()
        return result
    }

    private fun layoutFlex(
        nodes: List<ZorviLayoutNode>,
        left: Float,
        top: Float,
        width: Float,
        style: ZorviLayoutStyle
    ): List<ZorviLayoutBox> {
        val visible = nodes.filter { it.style.display != ZorviDisplay.NONE }
        if (visible.isEmpty()) return emptyList()
        val row = style.flexDirection == ZorviFlexDirection.ROW
        val mainAvailable = if (row) width else Float.POSITIVE_INFINITY
        val bases = visible.map { child ->
            val declared = if (row) child.style.width else child.style.height
            declared ?: if (row) max(child.style.minWidth, measureInlineText(child.text, width).first)
                else max(child.style.minHeight, lineHeight)
        }
        val gaps = style.gap * max(0, visible.size - 1)
        val free = if (mainAvailable.isFinite()) max(0f, mainAvailable - bases.sum() - gaps) else 0f
        val totalGrow = visible.sumOf { it.style.flexGrow.toDouble() }.toFloat()
        val sizes = visible.indices.map { i ->
            bases[i] + if (totalGrow > 0f) free * visible[i].style.flexGrow / totalGrow else 0f
        }
        val used = sizes.sum() + gaps
        val leftover = if (mainAvailable.isFinite()) max(0f, mainAvailable - used) else 0f
        val leading = when (style.justifyContent) {
            ZorviJustify.CENTER -> leftover / 2f
            ZorviJustify.END -> leftover
            else -> 0f
        }
        val between = when (style.justifyContent) {
            ZorviJustify.SPACE_BETWEEN -> if (visible.size > 1) leftover / (visible.size - 1) else 0f
            ZorviJustify.SPACE_AROUND -> leftover / visible.size
            else -> 0f
        }
        var cursor = (if (row) left else top) + leading +
            if (style.justifyContent == ZorviJustify.SPACE_AROUND) between / 2f else 0f
        return visible.mapIndexed { index, child ->
            val mainSize = sizes[index]
            val crossAvailable = if (row) child.style.height else width
            val crossSize = when (style.alignItems) {
                ZorviAlign.STRETCH -> crossAvailable ?: lineHeight
                else -> crossAvailable ?: lineHeight
            }
            val crossOffset = when (style.alignItems) {
                ZorviAlign.CENTER -> (lineHeight - crossSize) / 2f
                ZorviAlign.END -> lineHeight - crossSize
                else -> 0f
            }
            val box = if (row) {
                layoutNode(child, cursor, top + crossOffset, mainSize, crossSize)
            } else {
                layoutNode(child, left + crossOffset, cursor, crossSize, mainSize)
            }
            cursor += mainSize + style.gap + between
            box
        }
    }

    private fun layoutGrid(
        nodes: List<ZorviLayoutNode>,
        left: Float,
        top: Float,
        width: Float,
        style: ZorviLayoutStyle
    ): List<ZorviLayoutBox> {
        val visible = nodes.filter { it.style.display != ZorviDisplay.NONE }
        if (visible.isEmpty()) return emptyList()
        val columns = style.gridColumns.coerceIn(1, 12)
        val cellWidth = max(0f, (width - style.gap * (columns - 1)) / columns)
        val rowHeights = mutableListOf<Float>()
        val boxes = mutableListOf<ZorviLayoutBox>()
        visible.forEachIndexed { index, child ->
            val column = index % columns
            val row = index / columns
            val x = left + column * (cellWidth + style.gap)
            val y = top + (0 until row).sumOf { rowHeights.getOrElse(it) { lineHeight }.toDouble() }.toFloat() +
                row * style.gap
            val box = layoutNode(child, x, y, cellWidth, null)
            boxes += box
            while (rowHeights.size <= row) rowHeights += 0f
            rowHeights[row] = max(rowHeights[row], box.height)
        }
        return boxes
    }

    private fun measureInlineText(text: String, availableWidth: Float): Pair<Float, Float> {
        if (text.isEmpty()) return 0f to 0f
        val words = text.split(Regex("\\s+"))
        var line = ""
        var widest = 0f
        var lines = 1
        words.forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && measureText(candidate) > availableWidth) {
                widest = max(widest, measureText(line))
                line = word
                lines++
            } else line = candidate
        }
        widest = max(widest, measureText(line))
        return min(widest, availableWidth) to (lines * lineHeight)
    }
}
