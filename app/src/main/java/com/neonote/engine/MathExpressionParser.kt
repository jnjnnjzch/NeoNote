package com.neonote.engine

public sealed interface MathNode {
    public data class Sequence(val items: List<MathNode>) : MathNode
    public data class Text(val value: String) : MathNode
    public data class Fraction(val numerator: MathNode, val denominator: MathNode) : MathNode
    public data class Root(val radicand: MathNode, val index: MathNode? = null) : MathNode
    public data class Scripts(
        val base: MathNode,
        val superscript: MathNode? = null,
        val subscript: MathNode? = null,
    ) : MathNode
    public data class Delimited(val left: String, val content: MathNode, val right: String) : MathNode
}

public data class MathParseResult(
    val node: MathNode,
    val errors: List<String>,
) {
    public val isValid: Boolean get() = errors.isEmpty()
}

/** Bounded recursive-descent parser for the offline formula renderer. */
public object MathExpressionParser {
    public fun parse(source: String): MathParseResult {
        if (source.length > MaximumSourceLength) {
            return MathParseResult(MathNode.Text(source.take(MaximumSourceLength)), listOf("Formula is too long"))
        }
        val parser = Parser(source)
        val node = parser.parseSequence(null, 0)
        if (!parser.atEnd()) parser.errors += "Unexpected input at ${parser.position + 1}"
        return MathParseResult(node, parser.errors.toList())
    }

    private class Parser(private val source: String) {
        var position: Int = 0
        val errors: MutableList<String> = mutableListOf()

        fun atEnd(): Boolean = position >= source.length

        fun parseSequence(terminator: Char?, depth: Int): MathNode {
            if (depth > MaximumDepth) {
                errors += "Formula nesting is too deep"
                return MathNode.Text("")
            }
            val items = mutableListOf<MathNode>()
            val text = StringBuilder()
            fun flushText() {
                if (text.isNotEmpty()) {
                    items += MathNode.Text(text.toString())
                    text.clear()
                }
            }
            while (!atEnd()) {
                val current = source[position]
                if (terminator != null && current == terminator) {
                    position++
                    flushText()
                    return sequenceOf(items)
                }
                if (current.isWhitespace()) {
                    text.append(' ')
                    position++
                    continue
                }
                flushText()
                val atom = parseAtom(depth + 1)
                items += parseScripts(atom, depth + 1)
            }
            flushText()
            if (terminator != null) errors += "Missing closing $terminator"
            return sequenceOf(items)
        }

        private fun parseAtom(depth: Int): MathNode {
            if (atEnd()) return MathNode.Text("")
            return when (val current = source[position]) {
                '{' -> {
                    position++
                    parseSequence('}', depth)
                }
                '(' -> {
                    position++
                    MathNode.Delimited("(", parseSequence(')', depth), ")")
                }
                '[' -> {
                    position++
                    MathNode.Delimited("[", parseSequence(']', depth), "]")
                }
                '\\' -> parseCommand(depth)
                else -> {
                    position++
                    MathNode.Text(current.toString())
                }
            }
        }

        private fun parseCommand(depth: Int): MathNode {
            position++
            if (atEnd()) return MathNode.Text("\\")
            if (!source[position].isLetter()) {
                val escaped = source[position++]
                return MathNode.Text(escaped.toString())
            }
            val start = position
            while (!atEnd() && source[position].isLetter()) position++
            val command = source.substring(start, position)
            return when (command) {
                "frac" -> MathNode.Fraction(
                    numerator = parseRequiredGroup("fraction numerator", depth),
                    denominator = parseRequiredGroup("fraction denominator", depth),
                )
                "sqrt" -> {
                    skipWhitespace()
                    val index = if (!atEnd() && source[position] == '[') {
                        position++
                        parseSequence(']', depth)
                    } else null
                    MathNode.Root(parseRequiredGroup("root", depth), index)
                }
                "left" -> {
                    skipWhitespace()
                    if (atEnd()) MathNode.Text("") else parseAtom(depth)
                }
                "right" -> {
                    skipWhitespace()
                    if (atEnd()) MathNode.Text("") else parseAtom(depth)
                }
                else -> MathNode.Text(Symbols[command] ?: "\\$command")
            }
        }

        private fun parseRequiredGroup(label: String, depth: Int): MathNode {
            skipWhitespace()
            if (atEnd() || source[position] != '{') {
                errors += "Missing $label group at ${position + 1}"
                return if (atEnd()) MathNode.Text("") else parseAtom(depth)
            }
            position++
            return parseSequence('}', depth)
        }

        private fun parseScripts(base: MathNode, depth: Int): MathNode {
            var superscript: MathNode? = null
            var subscript: MathNode? = null
            while (!atEnd() && (source[position] == '^' || source[position] == '_')) {
                val marker = source[position++]
                skipWhitespace()
                val value = if (!atEnd() && source[position] == '{') {
                    position++
                    parseSequence('}', depth)
                } else if (!atEnd()) {
                    parseAtom(depth)
                } else {
                    errors += "Missing script after $marker"
                    MathNode.Text("")
                }
                if (marker == '^') superscript = value else subscript = value
            }
            return if (superscript == null && subscript == null) base
            else MathNode.Scripts(base, superscript, subscript)
        }

        private fun skipWhitespace() {
            while (!atEnd() && source[position].isWhitespace()) position++
        }
    }

    private fun sequenceOf(items: List<MathNode>): MathNode = when (items.size) {
        0 -> MathNode.Text("")
        1 -> items.single()
        else -> MathNode.Sequence(items.mergeAdjacentText())
    }

    private fun List<MathNode>.mergeAdjacentText(): List<MathNode> = buildList {
        this@mergeAdjacentText.forEach { node ->
            val previous = lastOrNull()
            if (previous is MathNode.Text && node is MathNode.Text) {
                removeAt(lastIndex)
                add(MathNode.Text(previous.value + node.value))
            } else add(node)
        }
    }

    private const val MaximumSourceLength = 4_096
    private const val MaximumDepth = 32

    private val Symbols = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "theta" to "θ", "lambda" to "λ", "mu" to "μ", "pi" to "π", "rho" to "ρ",
        "sigma" to "σ", "phi" to "φ", "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ",
        "Theta" to "Θ", "Lambda" to "Λ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
        "Omega" to "Ω", "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±",
        "leq" to "≤", "geq" to "≥", "neq" to "≠", "approx" to "≈", "infty" to "∞",
        "sum" to "∑", "prod" to "∏", "int" to "∫", "rightarrow" to "→",
        "leftarrow" to "←", "leftrightarrow" to "↔",
    )
}
