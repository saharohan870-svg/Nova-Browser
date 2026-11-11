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


    @Test fun supportsClassLookupAndSimpleSelectorGroups() {
        val document = ZorviDomDocument.from(
            HtmlDocument.parse("<main><p id='first' class='note selected'>One</p><p class='note'>Two</p><a class='selected'>Link</a></main>")
        )

        assertEquals(2, document.getElementsByClassName("note").size)
        assertEquals("first", document.querySelector("#first")?.getAttribute("id"))
        assertEquals(2, document.querySelectorAll("p.note, a.selected").size)
        assertEquals(0, document.querySelectorAll("p > a").size)
    }

    @Test fun supportsWildcardAndRejectsEmptyClassLookup() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<main><p class='a b'>One</p><span>Two</span></main>"))
        assertEquals(6, document.getElementsByTagName("*").size)
        assertTrue(document.getElementsByClassName("").isEmpty())
        assertTrue(document.getElementsByClassName("a b").single().tagName == "p")
        assertEquals(6, document.querySelectorAll("*").size)
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

    @Test fun dispatchesCaptureTargetAndBubbleInOrder() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<div id='parent'><button id='child'>Go</button></div>"))
        val parent = document.getElementById("parent")!!
        val child = document.getElementById("child")!!
        val order = mutableListOf<String>()
        parent.addListenerForTest("click", capture = true) { order += "capture-parent" }
        child.addListenerForTest("click", capture = true) { order += "capture-target" }
        child.addListenerForTest("click", capture = false) { order += "target" }
        parent.addListenerForTest("click", capture = false) { order += "bubble-parent" }

        document.dispatch("click", child)

        assertEquals(listOf("capture-parent", "capture-target", "target", "bubble-parent"), order)
    }

    @Test fun onceListenerIsRemovedAfterFirstDispatch() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<button id='x'>Go</button>"))
        val target = document.getElementById("x")!!
        var calls = 0
        document.addEventListener("click", capture = false, once = true) { calls++ }

        document.dispatch("click", target)
        document.dispatch("click", target)

        assertEquals(1, calls)
    }

    @Test fun nonBubblingEventDoesNotReachAncestors() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<div id='parent'><button id='child'>Go</button></div>"))
        val parent = document.getElementById("parent")!!
        val child = document.getElementById("child")!!
        var calls = 0
        parent.addListenerForTest("focus", capture = false) { calls++ }

        document.dispatch("focus", child, bubbles = false)

        assertEquals(0, calls)
    }

    @Test fun preventDefaultOnlyWorksForCancelableEvents() {
        val document = ZorviDomDocument.from(HtmlDocument.parse("<button id='x'>Go</button>"))
        val target = document.getElementById("x")!!
        var nonCancelable: ZorviDomEvent? = null
        var cancelable: ZorviDomEvent? = null
        target.addListenerForTest("plain") { nonCancelable = it; it.preventDefault() }
        target.addListenerForTest("cancel") { cancelable = it; it.preventDefault() }

        document.dispatch("plain", target)
        document.dispatch("cancel", target, cancelable = true)

        assertEquals(false, nonCancelable?.defaultPrevented)
        assertEquals(true, cancelable?.defaultPrevented)
    }

}

private fun ZorviDomNode.addListenerForTest(
    type: String,
    capture: Boolean = false,
    callback: (ZorviDomEvent) -> Unit
) = addListener(type, capture, once = false, callback = callback)

