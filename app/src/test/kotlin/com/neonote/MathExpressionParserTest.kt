package com.neonote

import com.neonote.engine.MathExpressionParser
import com.neonote.engine.MathNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MathExpressionParserTest {
    @Test
    fun `fraction root and scripts form a structural tree`() {
        val result = MathExpressionParser.parse("\\frac{x_1}{\\sqrt[3]{y^2}}")
        val fraction = assertIs<MathNode.Fraction>(result.node)
        val numerator = assertIs<MathNode.Scripts>(fraction.numerator)
        val root = assertIs<MathNode.Root>(fraction.denominator)

        assertIs<MathNode.Text>(numerator.subscript)
        assertIs<MathNode.Text>(root.index)
        assertIs<MathNode.Scripts>(root.radicand)
        assertTrue(result.isValid)
    }

    @Test
    fun `common commands resolve to symbols while unknown commands remain visible`() {
        val result = MathExpressionParser.parse("\\alpha + \\unknown")
        val sequence = assertIs<MathNode.Sequence>(result.node)
        val text = sequence.items.filterIsInstance<MathNode.Text>().joinToString("") { it.value }

        assertTrue(text.contains("α"))
        assertTrue(text.contains("\\unknown"))
    }

    @Test
    fun `missing fraction group is reported without throwing`() {
        val result = MathExpressionParser.parse("\\frac{x}")

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("denominator") })
    }

    @Test
    fun `parentheses are represented as delimiters`() {
        val result = MathExpressionParser.parse("(a+b)")
        val delimited = assertIs<MathNode.Delimited>(result.node)

        assertEquals("(", delimited.left)
        assertEquals(")", delimited.right)
    }

    @Test
    fun `excessive source is bounded`() {
        val result = MathExpressionParser.parse("x".repeat(5_000))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("too long") })
    }
}
