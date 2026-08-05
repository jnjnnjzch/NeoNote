package com.neonote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardImagePasteTest {
    @Test
    fun `resolved item MIME overrides aggregate image description`() {
        assertFalse(clipboardUriCanRepresentImage("application/pdf", true, 2))
        assertTrue(clipboardUriCanRepresentImage("image/png", false, 2))
    }

    @Test
    fun `aggregate image description is accepted only for one unresolved URI`() {
        assertTrue(clipboardUriCanRepresentImage(null, true, 1))
        assertFalse(clipboardUriCanRepresentImage(null, true, 2))
        assertFalse(clipboardUriCanRepresentImage(null, false, 1))
    }
}
