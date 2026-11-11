package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZorviDomDocumentTest {
    @Test fun appendChildMovesNodeAndMaintainsParentLinks() {
        val root = ZorviDomNode("root")
        val first = ZorviDomNode("first")
        val second = ZorviDomNode("second")
        val child = ZorviDomNode("child")
        root.appendChild(first)
        root.appendChild(second)
        first.appendChild(child)

        second.appendChild(child)

        assertTrue(first.children.isEmpty())
        assertSame(second, child.parent)
        assertEquals(listOf(child), second.children)
    }

    @Test fun rejectsSelfAndAncestorCycles() {
        val root = ZorviDomNode("root")
        val child = ZorviDomNode("child")
        root.appendChild(child)
        try {
            root.appendChild(root)
            fail("Expected self-cycle to be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            child.appendChild(root)
            fail("Expected ancestor cycle to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun documentLookupAndMutationUpdateTextAndAttributes() {
        val document = ZorviDomDocument.from(
            HtmlDocument.parse("<p id='message' class='old'>Hello</p>")
        )
        assertTrue(document.hasElement("message"))
        assertEquals("Hello", document.getTextById("message"))

        document.setTextById("message", "Updated")
        document.setAttributeById("message", "class", "new")

        assertEquals("Updated", document.getTextById("message"))
        assertEquals("new", document.getAttributeById("message", "class"))
        assertFalse(document.visibleBlocks().isEmpty())
    }

    @Test fun replacingTextDetachesOldChildNodes() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<p id='x'>old</p>"))
        val paragraph = document.getElementById("x")!!
        val oldText = paragraph.children.single()

        document.setTextById("x", "new")

        assertNull(oldText.parent)
        assertEquals("new", paragraph.text())
    }

    @Test fun dispatchesTypedDocumentEvents() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<p id='x'>text</p>"))
        val target = document.getElementById("x")!!
        var observed: ZorviDomEvent? = null
        document.addEventListener("click") { observed = it }

        document.dispatch("click", target)

        assertEquals("click", observed?.type)
        assertSame(target, observed?.target)
    }
}
