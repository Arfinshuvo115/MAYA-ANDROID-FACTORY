package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

private data class ParsedResponse(
    val thought: String?,
    val bodyBlocks: List<MarkdownBlock>
)

private val THOUGHT_REGEX = Regex(
    pattern = "<!--\\s*thought[^>]*-->(.*?)<!--\\s*/thought\\s*-->",
    options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
)

@Composable
fun MayaFormattedResponse(
    rawText: String,
    modifier: Modifier = Modifier
) {
    val parsed = remember(rawText) { parseMayaResponse(rawText) }
    var thoughtExpanded by remember(rawText) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!parsed.thought.isNullOrBlank()) {
            val thoughtShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp)
            Surface(
                shape = thoughtShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.48f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(thoughtShape)
                    .clickable { thoughtExpanded = !thoughtExpanded }
                    .testTag("thought_toggle_card")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "COG.TRACE // Thought Process · ভাবনা",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (thoughtExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (thoughtExpanded) "Collapse thought" else "Expand thought",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    AnimatedVisibility(visible = thoughtExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = parsed.thought,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        parsed.bodyBlocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = style,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is MarkdownBlock.Bullet -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "▸",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = renderInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp, topEnd = 3.dp, bottomStart = 3.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun parseMayaResponse(rawText: String): ParsedResponse {
    val thoughtMatch = THOUGHT_REGEX.find(rawText)
    val thoughtText = thoughtMatch?.groupValues?.getOrNull(1)?.trim()
    val bodyRaw = if (thoughtMatch != null) {
        rawText.replace(THOUGHT_REGEX, "").trim()
    } else {
        rawText.trim()
    }

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = bodyRaw.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> {
                i++
            }
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            }
            trimmed == "---" || trimmed == "***" -> {
                blocks.add(MarkdownBlock.Divider)
                i++
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                i++
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim()))
                i++
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim()))
                i++
            }
            trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ") -> {
                blocks.add(MarkdownBlock.Bullet(trimmed.drop(2).trim()))
                i++
            }
            else -> {
                val paraLines = mutableListOf(trimmed)
                i++
                while (i < lines.size) {
                    val next = lines[i].trim()
                    if (next.isEmpty() || next.startsWith("#") || next.startsWith("```") ||
                        next.startsWith("* ") || next.startsWith("- ") || next.startsWith("• ") ||
                        next == "---"
                    ) {
                        break
                    }
                    paraLines.add(next)
                    i++
                }
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
            }
        }
    }

    return ParsedResponse(thought = thoughtText, bodyBlocks = blocks)
}

@Composable
private fun renderInlineMarkdown(text: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", index + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append("**")
                        index += 2
                    }
                }
                text[index] == '`' -> {
                    val end = text.indexOf('`', index + 1)
                    if (end != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFamily,
                                color = primaryColor,
                                background = codeBg
                            )
                        ) {
                            append(" ${text.substring(index + 1, end)} ")
                        }
                        index = end + 1
                    } else {
                        append('`')
                        index++
                    }
                }
                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }
}
