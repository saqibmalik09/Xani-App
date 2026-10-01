package app.xani.feature.roles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.xani.core.net.FormFieldData
import app.xani.core.net.FormFieldType
import app.xani.feature.auth.help.CountryPickerSheet
import app.xani.feature.auth.help.DialCountries
import app.xani.feature.profile.ProfileIco
import app.xani.feature.profile.ProfileTokens
import app.xani.i18n.Copy
import app.xani.ui.components.LightField
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Colors
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.theme.responsive

/**
 * Renders a role's application form from its server definition. Values are
 * strings keyed by field key (countries as ISO-2, dropdowns as the option).
 * The server re-validates everything; [validateAnswers] only saves a round trip.
 */
@Composable
fun DynamicForm(
    copy: Copy,
    fields: List<FormFieldData>,
    values: Map<String, String>,
    errors: Map<String, String>,
    onChange: (key: String, value: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(rdp(14))) {
        for (f in fields) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XaniText(f.label, Type.LabelBold, color = ProfileTokens.Text, fontSizeOverride = 13f, modifier = Modifier.weight(1f))
                    if (!f.required) {
                        XaniText(copy.t(Copy.RolesFormOptional), Type.Micro, color = ProfileTokens.Muted, fontSizeOverride = 11f)
                    }
                }
                Spacer(Modifier.height(rdp(6)))
                val value = values[f.key].orEmpty()
                when (f.type) {
                    FormFieldType.TEXTAREA -> AreaField(
                        value = value,
                        onValueChange = { onChange(f.key, it) },
                        placeholder = f.hint.ifBlank { f.label },
                        maxChars = f.max?.toInt()?.coerceIn(1, 4000) ?: 4000,
                    )
                    FormFieldType.NUMBER -> LightField(
                        value = value,
                        onValueChange = { onChange(f.key, it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }) },
                        placeholder = f.hint.ifBlank { f.label },
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        maxChars = 24,
                    )
                    FormFieldType.PHONE -> LightField(
                        value = value,
                        onValueChange = { onChange(f.key, it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' || ch == '-' || ch == '(' || ch == ')' }) },
                        placeholder = f.hint.ifBlank { copy.t(Copy.RolesFormPhoneHint) },
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                        maxChars = 20,
                    )
                    FormFieldType.DROPDOWN -> ChoiceField(
                        value = value,
                        options = f.options.filter { it.isNotBlank() },
                        placeholder = copy.t(Copy.RolesFormChoose),
                        onPick = { onChange(f.key, it) },
                    )
                    FormFieldType.COUNTRY -> CountryField(
                        iso = value,
                        placeholder = copy.t(Copy.RolesFormChooseCountry),
                        onPick = { onChange(f.key, it) },
                    )
                    else -> LightField(
                        value = value,
                        onValueChange = { onChange(f.key, it) },
                        placeholder = f.hint.ifBlank { f.label },
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                        maxChars = f.max?.toInt()?.coerceIn(1, 4000) ?: 4000,
                    )
                }
                val err = errors[f.key]
                when {
                    err != null -> XaniText(err, Type.Caption, color = ProfileTokens.Danger, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(6)))
                    f.hint.isNotBlank() && f.type != FormFieldType.TEXT && f.type != FormFieldType.TEXTAREA && f.type != FormFieldType.NUMBER && f.type != FormFieldType.PHONE ->
                        XaniText(f.hint, Type.Caption, color = ProfileTokens.Sub, fontSizeOverride = 12f, modifier = Modifier.padding(top = rdp(6)))
                }
            }
        }
    }
}

/** Mirrors the server's rules so the user sees problems before the round trip. */
fun validateAnswers(copy: Copy, fields: List<FormFieldData>, values: Map<String, String>): Map<String, String> {
    val out = HashMap<String, String>()
    for (f in fields) {
        val v = values[f.key].orEmpty().trim()
        if (v.isEmpty()) {
            if (f.required) out[f.key] = copy.t(Copy.RolesFormRequired)
            continue
        }
        when (f.type) {
            FormFieldType.TEXT, FormFieldType.TEXTAREA -> {
                val min = f.min?.toInt()
                val max = f.max?.toInt()
                if (min != null && v.length < min) out[f.key] = copy.t(Copy.RolesFormMinLen, "n" to min.toString())
                else if (max != null && v.length > max) out[f.key] = copy.t(Copy.RolesFormMaxLen, "n" to max.toString())
            }
            FormFieldType.NUMBER -> {
                val n = v.toDoubleOrNull()
                if (n == null) out[f.key] = copy.t(Copy.RolesFormNumber)
                else if (f.min != null && n < f.min) out[f.key] = copy.t(Copy.RolesFormMin, "n" to trim(f.min))
                else if (f.max != null && n > f.max) out[f.key] = copy.t(Copy.RolesFormMax, "n" to trim(f.max))
            }
            FormFieldType.DROPDOWN -> if (v !in f.options) out[f.key] = copy.t(Copy.RolesFormChoose)
            FormFieldType.PHONE -> if (!Regex("^\\+?[0-9][0-9 ()-]{5,19}$").matches(v)) out[f.key] = copy.t(Copy.RolesFormPhoneHint)
            FormFieldType.COUNTRY -> if (!Regex("^[A-Za-z]{2}$").matches(v)) out[f.key] = copy.t(Copy.RolesFormChooseCountry)
        }
    }
    return out
}

private fun trim(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

// ─── inputs the shared kit does not have ─────────────────────────────────────

@Composable
private fun AreaField(value: String, onValueChange: (String) -> Unit, placeholder: String, maxChars: Int) {
    val shape = RoundedCornerShape(rdp(6))
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(if (it.length <= maxChars) it else it.take(maxChars)) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = Type.Body.responsive().copy(color = Colors.Text),
        cursorBrush = SolidColor(Dark.Primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = rdp(96))
                    .clip(shape)
                    .background(Colors.Input)
                    .border(rdp(1.5f), Colors.BorderStrong, shape)
                    .padding(horizontal = rdp(16), vertical = rdp(12)),
            ) {
                if (value.isEmpty()) XaniText(placeholder, Type.Body, color = Colors.Muted)
                field()
            }
        },
    )
}

@Composable
private fun PickerRow(text: String, muted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(rdp(6))
    Row(
        Modifier
            .fillMaxWidth()
            .height(rdp(52))
            .clip(shape)
            .background(Colors.Input)
            .border(rdp(1.5f), Colors.BorderStrong, shape)
            .pressable(onClick = onClick)
            .padding(horizontal = rdp(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XaniText(text, Type.Body, color = if (muted) Colors.Muted else Colors.Text, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(rdp(8)))
        ProfileIco.Chevron(Color(0xFFBBBBBB), rdp(14))
    }
}

@Composable
private fun ChoiceField(value: String, options: List<String>, placeholder: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    PickerRow(text = value.ifBlank { placeholder }, muted = value.isBlank()) { open = !open }
    if (open) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = rdp(6))
                .clip(RoundedCornerShape(rdp(6)))
                .border(rdp(1), Colors.BorderStrong, RoundedCornerShape(rdp(6)))
                .background(Color.White),
        ) {
            options.forEachIndexed { i, o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = {
                            onPick(o)
                            open = false
                        })
                        .padding(horizontal = rdp(16), vertical = rdp(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XaniText(o, Type.Body, color = if (o == value) ProfileTokens.Accent else Colors.Text, modifier = Modifier.weight(1f))
                    if (o == value) XaniText("✓", Type.LabelBold, color = ProfileTokens.Accent)
                }
                if (i != options.lastIndex) Box(Modifier.fillMaxWidth().height(rdp(1)).background(ProfileTokens.RowLine))
            }
        }
    }
}

@Composable
private fun CountryField(iso: String, placeholder: String, onPick: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val country = remember(iso) { DialCountries.all.firstOrNull { it.iso.equals(iso, ignoreCase = true) } }
    PickerRow(text = country?.name ?: placeholder, muted = country == null) { picking = true }
    if (picking) {
        CountryPickerSheet(
            selectedIso = country?.iso ?: "",
            onSelect = {
                onPick(it.iso)
                picking = false
            },
            onClose = { picking = false },
        )
    }
}
