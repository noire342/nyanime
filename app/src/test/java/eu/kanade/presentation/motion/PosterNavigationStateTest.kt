package eu.kanade.presentation.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PosterNavigationStateTest {
    @Test
    fun `duplicate titles use only the tapped card and the same pair on return`() {
        val state = PosterNavigationState<String>()
        state.connect("home", "details", "second-row-card", "Same title", "decoded-poster")
        assertEquals("second-row-card", state.between("home", "details")?.element)
        assertEquals(state.between("home", "details"), state.between("details", "home"))
        assertNull(state.between("other-home", "details"))
        assertNull(state.between("details", "details"))
    }

    @Test
    fun `nested detail navigation retains separate return destinations`() {
        val state = PosterNavigationState<String>()
        state.connect("home", "first", "home-card", "First", "one")
        state.connect("first", "second", "related-card", "Second", "two")
        assertEquals("related-card", state.between("second", "first")?.element)
        assertEquals("home-card", state.between("first", "home")?.element)
        assertNull(state.between("home", "second"))
    }

    @Test
    fun `popped artwork is released after transition while previous route remains available`() {
        val state = PosterNavigationState<String>()
        state.connect("home", "first", "home-card", "First", "one")
        state.connect("first", "second", "related-card", "Second", "two")
        state.retain(setOf("home", "first", "second"))
        assertEquals("two", state.destination("second")?.artwork)
        state.retain(setOf("home", "first"))
        assertNull(state.destination("second"))
        assertEquals("one", state.destination("first")?.artwork)
        state.retain(setOf("home"))
        assertNull(state.destination("first"))
    }

    @Test
    fun `removed origin and disabling motion cannot leave an orphan return image`() {
        val state = PosterNavigationState<String>()
        state.connect("home", "details", "card", "Title", "image")
        state.retain(setOf("details"))
        assertNull(state.destination("details"))
        state.connect("home", "details", "card", "Title", "image")
        state.clear()
        assertNull(state.between("details", "home"))
    }

    @Test
    fun `a click without a new destination never records a transition`() {
        val state = PosterNavigationState<String>()
        state.connect("home", "home", "card", "Title", "image")
        assertNull(state.destination("home"))
    }
}
