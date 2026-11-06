package com.shd.codestudio.ui.screens

import com.shd.codestudio.ui.theme.Ide
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shd.codestudio.ui.LtrContent
import com.shd.codestudio.ui.backend.IdeBackend
import com.shd.codestudio.ui.backend.UiCodeStyle
import com.shd.codestudio.ui.components.ExpressiveScaffold
import com.shd.codestudio.ui.components.IconButtonCa
import com.shd.codestudio.ui.components.SettingsCard
import com.shd.codestudio.ui.components.SettingsChoiceRow
import com.shd.codestudio.ui.components.SettingsDivider
import com.shd.codestudio.ui.components.SettingsSliderRow
import com.shd.codestudio.ui.components.SettingsToggleRow
import com.shd.codestudio.ui.editor.CodeLanguage
import com.shd.codestudio.ui.editor.highlight
import com.shd.codestudio.ui.generated.resources.Res
import com.shd.codestudio.ui.generated.resources.back
import com.shd.codestudio.ui.generated.resources.codestyle_after_comma
import com.shd.codestudio.ui.generated.resources.codestyle_after_comma_desc
import com.shd.codestudio.ui.generated.resources.codestyle_after_imports
import com.shd.codestudio.ui.generated.resources.codestyle_after_type_cast
import com.shd.codestudio.ui.generated.resources.codestyle_after_type_cast_desc
import com.shd.codestudio.ui.generated.resources.codestyle_around_lambda_arrow
import com.shd.codestudio.ui.generated.resources.codestyle_around_lambda_arrow_desc
import com.shd.codestudio.ui.generated.resources.codestyle_around_operators
import com.shd.codestudio.ui.generated.resources.codestyle_around_operators_desc
import com.shd.codestudio.ui.generated.resources.codestyle_around_ternary
import com.shd.codestudio.ui.generated.resources.codestyle_around_ternary_desc
import com.shd.codestudio.ui.generated.resources.codestyle_before_brace
import com.shd.codestudio.ui.generated.resources.codestyle_before_brace_desc
import com.shd.codestudio.ui.generated.resources.codestyle_before_field
import com.shd.codestudio.ui.generated.resources.codestyle_before_first_member
import com.shd.codestudio.ui.generated.resources.codestyle_before_method
import com.shd.codestudio.ui.generated.resources.codestyle_before_parens
import com.shd.codestudio.ui.generated.resources.codestyle_before_parens_desc
import com.shd.codestudio.ui.generated.resources.codestyle_before_semicolon
import com.shd.codestudio.ui.generated.resources.codestyle_before_semicolon_desc
import com.shd.codestudio.ui.generated.resources.codestyle_between_types
import com.shd.codestudio.ui.generated.resources.codestyle_binary_expressions
import com.shd.codestudio.ui.generated.resources.codestyle_blank_lines
import com.shd.codestudio.ui.generated.resources.codestyle_brace_placement
import com.shd.codestudio.ui.generated.resources.codestyle_brace_end_of_line
import com.shd.codestudio.ui.generated.resources.codestyle_brace_next_line
import com.shd.codestudio.ui.generated.resources.codestyle_chained_calls
import com.shd.codestudio.ui.generated.resources.codestyle_comments
import com.shd.codestudio.ui.generated.resources.codestyle_continuation_indent
import com.shd.codestudio.ui.generated.resources.codestyle_continuation_indent_desc
import com.shd.codestudio.ui.generated.resources.codestyle_format_comments
import com.shd.codestudio.ui.generated.resources.codestyle_format_comments_desc
import com.shd.codestudio.ui.generated.resources.codestyle_indent_size
import com.shd.codestudio.ui.generated.resources.codestyle_indent_size_desc
import com.shd.codestudio.ui.generated.resources.codestyle_indent_with_tabs
import com.shd.codestudio.ui.generated.resources.codestyle_indent_with_tabs_desc
import com.shd.codestudio.ui.generated.resources.codestyle_indentation
import com.shd.codestudio.ui.generated.resources.codestyle_keep_at_most
import com.shd.codestudio.ui.generated.resources.codestyle_keep_at_most_desc
import com.shd.codestudio.ui.generated.resources.codestyle_kotlin_note
import com.shd.codestudio.ui.generated.resources.codestyle_lang_java
import com.shd.codestudio.ui.generated.resources.codestyle_lang_kotlin
import com.shd.codestudio.ui.generated.resources.codestyle_lang_xml
import com.shd.codestudio.ui.generated.resources.codestyle_markup
import com.shd.codestudio.ui.generated.resources.codestyle_preset_android_studio
import com.shd.codestudio.ui.generated.resources.codestyle_xml_android_conventions
import com.shd.codestudio.ui.generated.resources.codestyle_xml_android_conventions_desc
import com.shd.codestudio.ui.generated.resources.codestyle_xml_note
import com.shd.codestudio.ui.generated.resources.codestyle_xml_rearrange
import com.shd.codestudio.ui.generated.resources.codestyle_xml_rearrange_desc
import com.shd.codestudio.ui.generated.resources.codestyle_xml_space_in_empty_tag
import com.shd.codestudio.ui.generated.resources.codestyle_xml_space_in_empty_tag_desc
import com.shd.codestudio.ui.generated.resources.codestyle_xml_wrap_attributes
import com.shd.codestudio.ui.generated.resources.codestyle_xml_wrap_attributes_desc
import com.shd.codestudio.ui.generated.resources.codestyle_language
import com.shd.codestudio.ui.generated.resources.codestyle_language_desc
import com.shd.codestudio.ui.generated.resources.codestyle_line_width
import com.shd.codestudio.ui.generated.resources.codestyle_line_width_desc
import com.shd.codestudio.ui.generated.resources.codestyle_method_arguments
import com.shd.codestudio.ui.generated.resources.codestyle_method_parameters
import com.shd.codestudio.ui.generated.resources.codestyle_preset_android
import com.shd.codestudio.ui.generated.resources.codestyle_preset_custom
import com.shd.codestudio.ui.generated.resources.codestyle_preset_google
import com.shd.codestudio.ui.generated.resources.codestyle_preset_kotlin_official
import com.shd.codestudio.ui.generated.resources.codestyle_preview
import com.shd.codestudio.ui.generated.resources.codestyle_preview_no_project
import com.shd.codestudio.ui.generated.resources.codestyle_reformat_on_save
import com.shd.codestudio.ui.generated.resources.codestyle_reformat_on_save_desc
import com.shd.codestudio.ui.generated.resources.codestyle_spaces
import com.shd.codestudio.ui.generated.resources.codestyle_style
import com.shd.codestudio.ui.generated.resources.codestyle_style_desc
import com.shd.codestudio.ui.generated.resources.codestyle_title
import com.shd.codestudio.ui.generated.resources.codestyle_within_parens
import com.shd.codestudio.ui.generated.resources.codestyle_within_parens_desc
import com.shd.codestudio.ui.generated.resources.codestyle_wrap_comments
import com.shd.codestudio.ui.generated.resources.codestyle_wrap_comments_desc
import com.shd.codestudio.ui.generated.resources.codestyle_wrap_if_long
import com.shd.codestudio.ui.generated.resources.codestyle_wrap_never
import com.shd.codestudio.ui.generated.resources.codestyle_wrap_one_per_line
import com.shd.codestudio.ui.generated.resources.codestyle_wrapping_and_braces
import com.shd.codestudio.ui.icons.CaIcons
import com.shd.codestudio.ui.platform.touchInput
import com.shd.codestudio.ui.theme.Ca
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val LANG_JAVA = "java"
private const val LANG_KOTLIN = "kotlin"
internal const val LANG_XML = "xml"
internal const val PRESET_CUSTOM = "custom"
internal const val FORMAT_ON_SAVE_KEY = "settings.codeStyle.formatOnSave"

/**
 * The dedicated Code Style screen: a per-language formatting profile (Java / Kotlin / XML), a preset, the
 * detailed options grouped by category, and a live preview that runs the real formatter on a fixed sample as
 * you tune. Java honors every option; the Kotlin re-indenter honors only indentation / tabs / blank-lines /
 * inline spacing, so the Java-only groups are hidden on the Kotlin tab. The XML tab shows indentation, line
 * width and blank lines plus its own markup group.
 *
 * The live preview runs the real formatter, which needs an open project's engine; with [hasProject] false
 * (the screen reached from the project picker's hub) it's disabled and the card shows a hint instead. Editing
 * + persisting the profile still works either way.
 */
@Composable
fun CodeStyleScreen(backend: IdeBackend, hasProject: Boolean, onBack: () -> Unit) {
    val iconBox = if (touchInput) 42 else 34

    val state = rememberCodeStyleScreenState(backend, hasProject)
    val display = state.display
    val javaOnly = state.javaOnly
    val xml = state.xml
    val custom = state.custom

    ExpressiveScaffold(title = stringResource(Res.string.codestyle_title), onBack = onBack) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsCard(null) {
                SettingsChoiceRow(
                    stringResource(Res.string.codestyle_language), stringResource(Res.string.codestyle_language_desc), state.language,
                    listOf(
                        LANG_JAVA to stringResource(Res.string.codestyle_lang_java),
                        LANG_KOTLIN to stringResource(Res.string.codestyle_lang_kotlin),
                        LANG_XML to stringResource(Res.string.codestyle_lang_xml),
                    ),
                ) { state.selectLanguage(it) }
                SettingsDivider()
                SettingsChoiceRow(
                    stringResource(Res.string.codestyle_style), stringResource(Res.string.codestyle_style_desc), state.style.preset,
                    presetOptions(state.language),
                ) { picked -> state.update(if (picked == PRESET_CUSTOM) display.copy(preset = PRESET_CUSTOM) else presetDefaults(picked)) }
                SettingsDivider()
                SettingsToggleRow(stringResource(Res.string.codestyle_reformat_on_save), stringResource(Res.string.codestyle_reformat_on_save_desc), state.formatOnSave) {
                    state.updateFormatOnSave(it)
                }
            }

            PreviewCard(state.preview, state.language, hasProject)

            if (!javaOnly) {
                Text(
                    stringResource(if (xml) Res.string.codestyle_xml_note else Res.string.codestyle_kotlin_note),
                    color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (custom) {
                SettingsCard(stringResource(Res.string.codestyle_indentation)) {
                    SettingsSliderRow(stringResource(Res.string.codestyle_indent_size), stringResource(Res.string.codestyle_indent_size_desc), display.indentSize, 1, 8, 1, null) { state.edit { copy(indentSize = it) } }
                    if (javaOnly) {
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_continuation_indent), stringResource(Res.string.codestyle_continuation_indent_desc), display.continuationIndent, 1, 16, 1, null) { state.edit { copy(continuationIndent = it) } }
                    }
                    if (javaOnly || xml) {
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_line_width), stringResource(Res.string.codestyle_line_width_desc), display.maxLineLength, 60, 200, 10, null) { state.edit { copy(maxLineLength = it) } }
                    }
                    SettingsDivider()
                    SettingsToggleRow(stringResource(Res.string.codestyle_indent_with_tabs), stringResource(Res.string.codestyle_indent_with_tabs_desc), display.useTabs) { state.edit { copy(useTabs = it) } }
                }

                if (javaOnly) {
                    SettingsCard(stringResource(Res.string.codestyle_wrapping_and_braces)) {
                        SettingsChoiceRow(stringResource(Res.string.codestyle_brace_placement), null, display.braceStyle, listOf("endOfLine" to stringResource(Res.string.codestyle_brace_end_of_line), "nextLine" to stringResource(Res.string.codestyle_brace_next_line))) { state.edit { copy(braceStyle = it) } }
                        SettingsDivider()
                        SettingsChoiceRow(stringResource(Res.string.codestyle_method_parameters), null, display.wrapMethodParameters, wrapOptions()) { state.edit { copy(wrapMethodParameters = it) } }
                        SettingsDivider()
                        SettingsChoiceRow(stringResource(Res.string.codestyle_method_arguments), null, display.wrapMethodArguments, wrapOptions()) { state.edit { copy(wrapMethodArguments = it) } }
                        SettingsDivider()
                        SettingsChoiceRow(stringResource(Res.string.codestyle_chained_calls), null, display.wrapChainedCalls, wrapOptions()) { state.edit { copy(wrapChainedCalls = it) } }
                        SettingsDivider()
                        SettingsChoiceRow(stringResource(Res.string.codestyle_binary_expressions), null, display.wrapBinaryExpressions, wrapOptions()) { state.edit { copy(wrapBinaryExpressions = it) } }
                    }
                }

                if (xml) {
                    SettingsCard(stringResource(Res.string.codestyle_markup)) {
                        SettingsToggleRow(stringResource(Res.string.codestyle_xml_android_conventions), stringResource(Res.string.codestyle_xml_android_conventions_desc), display.xmlAndroidConventions) { state.edit { copy(xmlAndroidConventions = it) } }
                        SettingsDivider()
                        SettingsChoiceRow(stringResource(Res.string.codestyle_xml_wrap_attributes), stringResource(Res.string.codestyle_xml_wrap_attributes_desc), display.xmlWrapAttributes, wrapOptions()) { state.edit { copy(xmlWrapAttributes = it) } }
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_xml_space_in_empty_tag), stringResource(Res.string.codestyle_xml_space_in_empty_tag_desc), display.xmlSpaceInEmptyTag) { state.edit { copy(xmlSpaceInEmptyTag = it) } }
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_xml_rearrange), stringResource(Res.string.codestyle_xml_rearrange_desc), display.xmlRearrangeAttributes) { state.edit { copy(xmlRearrangeAttributes = it) } }
                    }
                }

                // Inline spacing. The first five rules are honored by Kotlin too (token-level rewriting); the
                // rest need Java's full formatter, so they are Java-only. XML has no expression spacing.
                if (!xml) SettingsCard(stringResource(Res.string.codestyle_spaces)) {
                    SettingsToggleRow(stringResource(Res.string.codestyle_within_parens), stringResource(Res.string.codestyle_within_parens_desc), display.spaceWithinParens) { state.edit { copy(spaceWithinParens = it) } }
                    SettingsDivider()
                    SettingsToggleRow(stringResource(Res.string.codestyle_after_comma), stringResource(Res.string.codestyle_after_comma_desc), display.spaceAfterComma) { state.edit { copy(spaceAfterComma = it) } }
                    SettingsDivider()
                    SettingsToggleRow(stringResource(Res.string.codestyle_around_operators), stringResource(Res.string.codestyle_around_operators_desc), display.spaceAroundOperators) { state.edit { copy(spaceAroundOperators = it) } }
                    SettingsDivider()
                    SettingsToggleRow(stringResource(Res.string.codestyle_before_brace), stringResource(Res.string.codestyle_before_brace_desc), display.spaceBeforeBrace) { state.edit { copy(spaceBeforeBrace = it) } }
                    SettingsDivider()
                    SettingsToggleRow(stringResource(Res.string.codestyle_around_lambda_arrow), stringResource(Res.string.codestyle_around_lambda_arrow_desc), display.spaceAroundLambdaArrow) { state.edit { copy(spaceAroundLambdaArrow = it) } }
                    if (javaOnly) {
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_before_parens), stringResource(Res.string.codestyle_before_parens_desc), display.spaceBeforeParens) { state.edit { copy(spaceBeforeParens = it) } }
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_before_semicolon), stringResource(Res.string.codestyle_before_semicolon_desc), display.spaceBeforeSemicolon) { state.edit { copy(spaceBeforeSemicolon = it) } }
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_around_ternary), stringResource(Res.string.codestyle_around_ternary_desc), display.spaceAroundTernary) { state.edit { copy(spaceAroundTernary = it) } }
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_after_type_cast), stringResource(Res.string.codestyle_after_type_cast_desc), display.spaceAfterTypeCast) { state.edit { copy(spaceAfterTypeCast = it) } }
                    }
                }

                SettingsCard(stringResource(Res.string.codestyle_blank_lines)) {
                    SettingsSliderRow(stringResource(Res.string.codestyle_keep_at_most), stringResource(Res.string.codestyle_keep_at_most_desc), display.blankLinesToKeep, 0, 5, 1, null) { state.edit { copy(blankLinesToKeep = it) } }
                    if (javaOnly) {
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_after_imports), null, display.blankLinesAfterImports, 0, 5, 1, null) { state.edit { copy(blankLinesAfterImports = it) } }
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_before_method), null, display.blankLinesBeforeMethod, 0, 5, 1, null) { state.edit { copy(blankLinesBeforeMethod = it) } }
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_before_field), null, display.blankLinesBeforeField, 0, 5, 1, null) { state.edit { copy(blankLinesBeforeField = it) } }
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_before_first_member), null, display.blankLinesBeforeFirstMember, 0, 5, 1, null) { state.edit { copy(blankLinesBeforeFirstMember = it) } }
                        SettingsDivider()
                        SettingsSliderRow(stringResource(Res.string.codestyle_between_types), null, display.blankLinesBetweenTypes, 0, 5, 1, null) { state.edit { copy(blankLinesBetweenTypes = it) } }
                    }
                }

                if (javaOnly) {
                    SettingsCard(stringResource(Res.string.codestyle_comments)) {
                        SettingsToggleRow(stringResource(Res.string.codestyle_format_comments), stringResource(Res.string.codestyle_format_comments_desc), display.formatComments) { state.edit { copy(formatComments = it) } }
                        SettingsDivider()
                        SettingsToggleRow(stringResource(Res.string.codestyle_wrap_comments), stringResource(Res.string.codestyle_wrap_comments_desc), display.wrapComments) { state.edit { copy(wrapComments = it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(preview: String, language: String, hasProject: Boolean) {
    SettingsCard(stringResource(Res.string.codestyle_preview)) {
        // The card shows formatter output, and every option above it (indent width, continuation indent,
        // brace placement, alignment) is judged by where a line starts and how far it is indented. The
        // editor pins itself to LTR for the same reason, so its preview has to agree: mirrored, the sample
        // would not match the file the formatter is about to write.
        LtrContent {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    .background(Ide.colors.editorBg, RoundedCornerShape(Ca.radius.control))
                    .verticalScroll(rememberScrollState()).padding(12.dp),
            ) {
                if (!hasProject) {
                    Text(
                        stringResource(Res.string.codestyle_preview_no_project),
                        color = MaterialTheme.colorScheme.outline,
                        style = Ide.type.codeSmall,
                    )
                } else if (preview.isEmpty()) {
                    Text("…", color = MaterialTheme.colorScheme.outline, style = Ide.type.codeSmall)
                } else {
                    val codeLang = when (language) {
                        LANG_KOTLIN -> CodeLanguage.Kotlin
                        LANG_XML -> CodeLanguage.Xml
                        else -> CodeLanguage.Java
                    }
                    val syntax = Ide.colors.syntax
                    val highlighted = remember(preview, codeLang, syntax) { highlight(preview, codeLang, syntax) }
                    Text(
                        highlighted,
                        style = Ide.type.codeSmall,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun presetOptions(language: String): List<Pair<String, String>> = when (language) {
    LANG_KOTLIN -> listOf(
        "kotlin_official" to stringResource(Res.string.codestyle_preset_kotlin_official),
        "android" to stringResource(Res.string.codestyle_preset_android),
        PRESET_CUSTOM to stringResource(Res.string.codestyle_preset_custom),
    )
    LANG_XML -> listOf(
        "android_studio" to stringResource(Res.string.codestyle_preset_android_studio),
        PRESET_CUSTOM to stringResource(Res.string.codestyle_preset_custom),
    )
    else -> listOf(
        "google" to stringResource(Res.string.codestyle_preset_google),
        "android" to stringResource(Res.string.codestyle_preset_android),
        PRESET_CUSTOM to stringResource(Res.string.codestyle_preset_custom),
    )
}

@Composable
private fun wrapOptions(): List<Pair<String, String>> =
    listOf(
        "never" to stringResource(Res.string.codestyle_wrap_never),
        "ifLong" to stringResource(Res.string.codestyle_wrap_if_long),
        "onePerLine" to stringResource(Res.string.codestyle_wrap_one_per_line),
    )

/** What the controls display: a named preset shows its canonical values; Custom shows the stored fields. */
internal fun displayStyle(style: UiCodeStyle): UiCodeStyle =
    if (style.preset == PRESET_CUSTOM) style else presetDefaults(style.preset)

/** The canonical values for a named preset (presets differ only in indentation, and the XML preset keeps two
 *  blank lines; the rest are shared). */
private fun presetDefaults(preset: String): UiCodeStyle = when (preset) {
    "android" -> UiCodeStyle(preset = "android", indentSize = 4, continuationIndent = 8)
    "kotlin_official" -> UiCodeStyle(preset = "kotlin_official", indentSize = 4, continuationIndent = 8)
    "android_studio" -> UiCodeStyle(preset = "android_studio", indentSize = 4, continuationIndent = 8, blankLinesToKeep = 2)
    else -> UiCodeStyle(preset = "google", indentSize = 2, continuationIndent = 4)
}
