package com.neonote.engine

/**
 * Lightweight offline math formatter used by the editor preview. It supports
 * common LaTeX commands, fractions, roots, superscripts and subscripts without
 * network or WebView dependencies. Unsupported commands remain visible rather
 * than silently disappearing.
 */
public object MathExpressionFormatter {
    public fun render(source: String): MathRenderResult {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return MathRenderResult("", emptyList())
        val errors = validate(trimmed)
        var output = trimmed
        Greek.forEach { (command, symbol) -> output = output.replace("\\$command", symbol) }
        Operators.forEach { (command, symbol) -> output = output.replace("\\$command", symbol) }
        output = replaceStructured(output, "\\frac") { args -> "${parenthesize(args[0])}⁄${parenthesize(args[1])}" }
        output = replaceStructured(output, "\\sqrt") { args -> "√${parenthesize(args[0])}" }
        output = replaceDecorations(output, '^', Superscript)
        output = replaceDecorations(output, '_', Subscript)
        output = output.replace("\\left", "").replace("\\right", "")
        output = output.replace("{", "").replace("}", "")
        output = output.replace(Regex("\\\\([A-Za-z]+)"), "$1")
        return MathRenderResult(output, errors)
    }

    private fun validate(source: String): List<String> {
        val errors = mutableListOf<String>()
        var depth = 0
        source.forEachIndexed { index, char ->
            when (char) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth < 0) {
                        errors += "Unexpected } at ${index + 1}"
                        depth = 0
                    }
                }
            }
        }
        if (depth > 0) errors += "Missing $depth closing brace${if (depth == 1) "" else "s"}"
        return errors
    }

    private fun replaceStructured(
        source: String,
        command: String,
        argCount: Int = if (command == "\\frac") 2 else 1,
        replacement: (List<String>) -> String,
    ): String {
        var result = source
        var start = result.indexOf(command)
        while (start >= 0) {
            var cursor = start + command.length
            val args = mutableListOf<String>()
            repeat(argCount) {
                while (cursor < result.length && result[cursor].isWhitespace()) cursor++
                val parsed = parseGroup(result, cursor) ?: return@repeat
                args += parsed.first
                cursor = parsed.second
            }
            if (args.size != argCount) {
                start = result.indexOf(command, start + command.length)
            } else {
                result = result.replaceRange(start, cursor, replacement(args))
                start = result.indexOf(command, start + 1)
            }
        }
        return result
    }

    private fun parseGroup(source: String, start: Int): Pair<String, Int>? {
        if (start !in source.indices || source[start] != '{') return null
        var depth = 0
        for (index in start until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start + 1, index) to (index + 1)
                }
            }
        }
        return null
    }

    private fun replaceDecorations(source: String, marker: Char, mapping: Map<Char, Char>): String {
        val output = StringBuilder()
        var index = 0
        while (index < source.length) {
            if (source[index] != marker || index + 1 >= source.length) {
                output.append(source[index++])
                continue
            }
            val next = index + 1
            if (source[next] == '{') {
                val group = parseGroup(source, next)
                if (group == null) {
                    output.append(marker)
                    index++
                } else {
                    output.append(group.first.map { mapping[it] ?: it }.joinToString(""))
                    index = group.second
                }
            } else {
                output.append(mapping[source[next]] ?: source[next])
                index += 2
            }
        }
        return output.toString()
    }

    private fun parenthesize(value: String): String =
        if (value.length == 1) value else "($value)"

    private val Greek = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "phi" to "φ",
        "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ",
        "Lambda" to "Λ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ", "Omega" to "Ω",
    )
    private val Operators = mapOf(
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±",
        "leq" to "≤", "geq" to "≥", "neq" to "≠", "approx" to "≈",
        "infty" to "∞", "sum" to "∑", "prod" to "∏", "int" to "∫",
        "rightarrow" to "→", "leftarrow" to "←", "leftrightarrow" to "↔",
    )
    private val Superscript = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ',
    )
    private val Subscript = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'o' to 'ₒ', 'r' to 'ᵣ', 'u' to 'ᵤ', 'v' to 'ᵥ', 'x' to 'ₓ',
    )
}

public data class MathRenderResult(val displayText: String, val errors: List<String>) {
    public val isValid: Boolean get() = errors.isEmpty()
}
