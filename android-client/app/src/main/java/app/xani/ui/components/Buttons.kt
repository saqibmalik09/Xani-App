package app.xani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.xani.ui.theme.BrandGradient
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import androidx.compose.foundation.clickable

/** Light-surface CTA: 52dp tall, 6dp corners, brand gradient, greys out when invalid. */
@Composable
fun PrimaryCta(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    trailingArrow: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, enabled && !loading)
    val shape = RoundedCornerShape(rdp(6))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rdp(52))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) Modifier.shadow(
                    elevation = rdp(10),
                    shape = shape,
                    spotColor = Dark.Primary,
                    ambientColor = Dark.Primary,
                ) else Modifier,
            )
            .clip(shape)
            .then(
                if (enabled) Modifier.background(BrandGradient)
                else Modifier.background(Colors.Disabled),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Spinner(size = rdp(20))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(8)),
            ) {
                XaniText(
                    text = text,
                    style = Type.LabelStrong,
                    color = if (enabled) Color.White else Colors.Muted,
                )
                if (trailingArrow) {
                    ArrowRightIcon(
                        size = rdp(16),
                        tint = if (enabled) Color.White else Colors.Muted,
                    )
                }
            }
            if (leading != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = rdp(16)),
                ) {
                    leading()
                }
            }
        }
    }
}

/** Dark-surface CTA from the Figma `Btn` component: 56dp tall, 18dp corners. */
@Composable
fun DarkCta(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, enabled)
    val shape = RoundedCornerShape(rdp(18))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rdp(56))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .shadow(
                elevation = rdp(14),
                shape = shape,
                spotColor = Dark.Primary,
                ambientColor = Dark.Accent,
            )
            .clip(shape)
            .background(BrandGradient)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        XaniText(text = text, style = Type.LabelStrong, color = Color.White)
    }
}

/**
 * Provider row: white pill with the brand glyph pinned to the leading edge and the
 * label optically centred, matching the Figma auth sheet.
 */
@Composable
fun ProviderButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, true)
    val shape = RoundedCornerShape(rdp(6))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rdp(52))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Colors.Background)
            .border(rdp(1.5f), Colors.BorderStrong, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = rdp(16)),
        contentAlignment = Alignment.CenterStart,
    ) {
        icon()
        XaniText(
            text = label,
            style = Type.Label.copy(fontWeight = Type.LabelStrong.fontWeight),
            color = Colors.Text,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Compact provider tile used on the login screen (glyph only). */
@Composable
fun ProviderTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, true)
    val shape = RoundedCornerShape(rdp(6))

    Box(
        modifier = modifier
            .height(rdp(48))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Colors.Background)
            .border(rdp(1.5f), Colors.BorderStrong, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun CircleIconButton(
    diameter: Dp,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: @Composable () -> Unit,
) {
    val circle = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(diameter)
            .clip(circle)
            .border(rdp(1.5f), borderColor, circle)
            .pressable(onClick = onClick)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Dark.Primary,
) {
    XaniText(
        text = text,
        style = Type.LabelBold,
        color = color,
        modifier = modifier.pressable(role = Role.Button, onClick = onClick),
    )
}
