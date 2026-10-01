package app.xani.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.theme.responsive

private const val LimitReached = "Characters limit reached"

@Composable
fun LightField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    maxChars: Int = 120,
    prefix: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val atLimit = value.length >= maxChars
    val borderColor by animateColorAsState(
        targetValue = when {
            atLimit && focused -> Colors.Error
            focused -> Dark.Primary
            else -> Colors.BorderStrong
        },
        label = "fieldBorder",
    )
    val shape = RoundedCornerShape(rdp(6))

    BasicTextField(
        value = value,
        onValueChange = { incoming ->
            onValueChange(if (incoming.length <= maxChars) incoming else incoming.take(maxChars))
        },
        modifier = modifier.fillMaxWidth(),
        textStyle = Type.Body.responsive().copy(color = Colors.Text),
        singleLine = true,
        cursorBrush = SolidColor(if (atLimit) Colors.Error else Dark.Primary),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rdp(52))
                    .clip(shape)
                    .background(Colors.Input)
                    .border(rdp(1.5f), borderColor, shape)
                    .padding(horizontal = rdp(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let {
                    Box(Modifier.padding(end = rdp(8))) { it() }
                }
                if (prefix != null) {
                    XaniText(text = prefix, style = Type.Body, color = Colors.Sub)
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        XaniText(
                            text = placeholder,
                            style = Type.Body,
                            color = Colors.Muted,
                            maxLines = 1,
                        )
                    }
                    field()
                }
                trailing?.let {
                    Box(Modifier.padding(start = rdp(8))) { it() }
                }
            }
        },
    )
}

/**
 * Name and username share one caption row so the limit copy and `n/max` counter
 * cannot drift between fields or platforms.
 */
@Composable
fun CountedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxChars: Int,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    prefix: String? = null,
    footnote: String? = null,
    sanitize: ((String) -> String)? = null,
) {
    val atLimit = value.length >= maxChars
    val left = if (atLimit) LimitReached else footnote
    Column(modifier) {
        LightField(
            value = value,
            onValueChange = { incoming ->
                val cleaned = sanitize?.invoke(incoming) ?: incoming
                onValueChange(if (cleaned.length <= maxChars) cleaned else cleaned.take(maxChars))
            },
            placeholder = placeholder,
            keyboardType = keyboardType,
            imeAction = imeAction,
            capitalization = capitalization,
            maxChars = maxChars,
            prefix = prefix,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = rdp(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (left != null) {
                XaniText(
                    text = left,
                    style = Type.Micro,
                    color = if (atLimit) Colors.Error else Colors.Hint,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            XaniText(
                text = "${value.length}/$maxChars",
                style = Type.Micro,
                color = if (atLimit) Colors.Error else Colors.Hint,
                align = TextAlign.End,
            )
        }
    }
}

/** Handle-only: letters, digits, underscore. The leading `@` is visual, never stored. */
fun sanitizeUsername(raw: String): String {
    val out = StringBuilder(raw.length)
    for (c in raw) {
        when (c) {
            in 'a'..'z', in '0'..'9', '_' -> out.append(c)
            in 'A'..'Z' -> out.append(c.lowercaseChar())
        }
    }
    return out.toString()
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    showStrength: Boolean = false,
) {
    Column(modifier) {
        LightField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            maxChars = 72,
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                Box(
                    Modifier
                        .size(rdp(28))
                        .pressable(onClick = onToggleReveal),
                    contentAlignment = Alignment.Center,
                ) {
                    if (revealed) {
                        EyeOffIcon(size = rdp(20), tint = Colors.Muted)
                    } else {
                        EyeIcon(size = rdp(20), tint = Colors.Muted)
                    }
                }
            },
        )

        if (showStrength && value.isNotEmpty()) {
            val strength = passwordStrength(value)
            val color = StrengthColors[strength]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rdp(6)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(4)),
            ) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rdp(3))
                            .clip(RoundedCornerShape(rdp(2)))
                            .background(if (index <= strength) color else Colors.Border),
                    )
                }
                XaniText(
                    text = StrengthLabels[strength],
                    style = Type.Micro.copy(fontWeight = Type.LabelBold.fontWeight),
                    color = color,
                    modifier = Modifier.padding(start = rdp(4)),
                )
            }
        }
    }
}

private val StrengthColors = listOf(Dark.Accent, Dark.Warning, Dark.Success)
private val StrengthLabels = listOf("Weak", "Fair", "Strong")

fun passwordStrength(value: String): Int = when {
    value.length < 4 -> 0
    value.length < 8 -> 1
    else -> 2
}

/**
 * One real text field, caret pinned to the end, so digits always fill left to right
 * into the next empty cell. The field itself is a 1dp hit-target; the painted cells
 * sit on top and a tap anywhere on a cell just focuses the field.
 */
@Composable
fun OtpInput(
    code: String,
    onCodeChange: (String) -> Unit,
    length: Int,
    verified: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeIndex = when {
        verified -> -1
        code.length >= length -> length - 1
        else -> code.length
    }

    Box {
        CompositionLocalProvider(LocalTextToolbar provides DisabledTextToolbar) {
            BasicTextField(
                value = TextFieldValue(code, TextRange(code.length)),
                onValueChange = { incoming ->
                    onCodeChange(incoming.text.filter(Char::isDigit).take(length))
                },
                modifier = modifier
                    .offset(x = (-4000).dp)
                    .size(1.dp),
                textStyle = Type.Otp.responsive().copy(color = Color.Transparent),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(rdp(10))) {
            repeat(length) { index ->
                OtpCell(
                    digit = code.getOrNull(index)?.toString().orEmpty(),
                    verified = verified,
                    active = index == activeIndex,
                )
            }
        }
    }
}

@Composable
private fun OtpCell(digit: String, verified: Boolean, active: Boolean) {
    val filled = digit.isNotEmpty()
    val shape = RoundedCornerShape(rdp(14))
    val border = when {
        verified -> Dark.Success
        active -> Dark.Primary
        filled -> Dark.Primary.copy(alpha = 0.7f)
        else -> Dark.Primary.copy(alpha = 0.18f)
    }

    Box(
        modifier = Modifier
            .width(rdp(46))
            .height(rdp(58))
            .clip(shape)
            .background(
                when {
                    verified || filled -> Dark.Primary.copy(alpha = 0.14f)
                    active -> Dark.Primary.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.04f)
                },
            )
            .border(rdp(1.5f), border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            XaniText(text = digit, style = Type.Otp, color = Dark.Foreground)
        }
    }
}

private object DisabledTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(
        rect: androidx.compose.ui.geometry.Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
