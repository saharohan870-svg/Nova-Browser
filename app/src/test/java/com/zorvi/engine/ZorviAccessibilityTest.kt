package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZorviAccessibilityTest {
    @Test fun exposesHeadingsLinksAndLandmarksWithRoles() {
        val tree = ZorviAccessibilityTree.from(
            HtmlDocument.parse("<nav aria-label='Main menu'><a href='/home'>Home</a></nav><main><h2>Welcome</h2></main>")
        )
        assertTrue(tree.nodes.any { it.role == "navigation" && it.label == "Main menu" })
        assertTrue(tree.nodes.any { it.role == "link" && it.label == "Home" })
        assertTrue(tree.nodes.any { it.role == "heading" && it.headingLevel == 2 && it.label == "Welcome" })
        assertTrue(tree.nodes.any { it.role == "main" })
    }

    @Test fun derivesFormFieldNamesFromAssociatedLabelsAndPlaceholders() {
        val tree = ZorviAccessibilityTree.from(
            HtmlDocument.parse("<label for='email'>Email address</label><input id='email' type='email'><textarea placeholder='Your message'></textarea>")
        )
        assertTrue(tree.nodes.any { it.role == "text-field" && it.label == "Email address" })
        assertTrue(tree.nodes.any { it.role == "text-field" && it.label == "Your message" })
    }

    @Test fun mapsNativeControlTypesAndDisabledState() {
        val tree = ZorviAccessibilityTree.from(
            HtmlDocument.parse("<input type='checkbox' aria-label='Remember me'><input type='radio' aria-label='Option A' disabled><select aria-label='Country'></select>")
        )
        assertTrue(tree.nodes.any { it.role == "checkbox" && it.label == "Remember me" })
        assertTrue(tree.nodes.any { it.role == "radio-button" && !it.enabled })
        assertTrue(tree.nodes.any { it.role == "combo-box" && it.label == "Country" })
    }

    @Test fun ignoresHiddenAndDecorativeContent() {
        val tree = ZorviAccessibilityTree.from(
            HtmlDocument.parse("<h1>Visible</h1><p aria-hidden='true'>Decorative</p><div hidden><button>Hidden button</button></div><input type='hidden' value='secret'>")
        )
        assertEquals(1, tree.nodes.size)
        assertEquals("Visible", tree.nodes.single().label)
        assertFalse(tree.nodes.any { it.label.contains("secret") })
    }
}
