package app.xani.feature.auth.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import app.xani.ui.components.AuthScaffold
import app.xani.ui.components.ChevronDownIcon
import app.xani.ui.components.CloseIcon
import app.xani.ui.components.LightField
import app.xani.ui.components.PrimaryCta
import app.xani.ui.components.SearchIcon
import app.xani.ui.components.TextLink
import app.xani.ui.components.XaniText
import app.xani.ui.components.pressable
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp

@Composable
fun LookupAccountScreen(
    onBack: () -> Unit,
    onSearchByPhone: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searched by rememberSaveable { mutableStateOf(false) }

        LookupScaffold(
        title = "Look up your account",
        message = "Enter a username or email to find the account you want to recover.",
        onBack = {
            searched = false
            onBack()
        },
        searched = searched,
        searchEnabled = query.trim().isNotEmpty(),
        onSearch = { searched = true },
        altLabel = "Search by phone number",
        onAlt = onSearchByPhone,
    ) {
        LightField(
            value = query,
            onValueChange = {
                query = it
                searched = false
            },
            placeholder = "Enter an email or username",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Search,
            maxChars = 254,
            trailing = {
                ClearFieldButton(visible = query.isNotEmpty()) {
                    query = ""
                    searched = false
                }
            },
        )
    }
}

@Composable
fun LookupPhoneScreen(
    onBack: () -> Unit,
    onSearchByEmail: () -> Unit,
) {
    val context = LocalContext.current
    val suggested = remember(context) { DialCountries.suggested(context) }
    var iso by rememberSaveable { mutableStateOf(suggested.iso) }
    var number by rememberSaveable { mutableStateOf("") }
    var searched by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val country = DialCountries.all.firstOrNull { it.iso == iso } ?: suggested

    Box {
        LookupScaffold(
            title = "Look up your account",
            message = "Enter your phone number to find the account you want to recover.",
            onBack = {
                searched = false
                onBack()
            },
            searched = searched,
            searchEnabled = number.length >= 6,
            onSearch = { searched = true },
            altLabel = "Search by email or username",
            onAlt = onSearchByEmail,
        ) {
            PhoneLookupField(
                country = country,
                number = number,
                onNumberChange = {
                    number = it.filter(Char::isDigit).take(15)
                    searched = false
                },
                onPickCountry = { picking = true },
            )
        }

        if (picking) {
            CountryPickerSheet(
                selectedIso = country.iso,
                onSelect = { next ->
                    iso = next.iso
                    picking = false
                },
                onClose = { picking = false },
            )
        }
    }
}

@Composable
private fun LookupScaffold(
    title: String,
    message: String,
    onBack: () -> Unit,
    searched: Boolean,
    searchEnabled: Boolean,
    onSearch: () -> Unit,
    altLabel: String,
    onAlt: () -> Unit,
    field: @Composable () -> Unit,
) {
    SystemBarIcons()
    AuthScaffold(background = Colors.Background, scrollable = false) {
        HelpTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rdp(20)),
        ) {
            XaniText(text = title, style = Type.H2, color = Colors.Text)
            Spacer(Modifier.height(rdp(8)))
            XaniText(text = message, style = Type.Caption, color = Colors.Sub)
            Spacer(Modifier.height(rdp(24)))
            field()
        }

        if (searched) {
            NoResultsState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onAlt = onAlt,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        TextLink(
            text = altLabel,
            onClick = onAlt,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = rdp(12)),
        )

        PrimaryCta(
            text = "Search",
            enabled = searchEnabled,
            onClick = onSearch,
            modifier = Modifier.padding(horizontal = rdp(20), vertical = rdp(16)),
        )
        Spacer(Modifier.height(rdp(12)))
    }
}

@Composable
private fun PhoneLookupField(
    country: DialCountry,
    number: String,
    onNumberChange: (String) -> Unit,
    onPickCountry: () -> Unit,
) {
    LightField(
        value = number,
        onValueChange = onNumberChange,
        placeholder = "Phone number",
        keyboardType = KeyboardType.Phone,
        imeAction = ImeAction.Search,
        maxChars = 15,
        leading = {
            Row(
                modifier = Modifier.pressable(onClick = onPickCountry),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rdp(4)),
            ) {
                XaniText(text = country.label, style = Type.Body, color = Colors.Text)
                ChevronDownIcon(size = rdp(10), tint = Colors.Sub)
                Box(
                    Modifier
                        .padding(horizontal = rdp(8))
                        .size(width = rdp(1), height = rdp(18))
                        .background(Colors.BorderStrong),
                )
            }
        },
        trailing = {
            ClearFieldButton(visible = number.isNotEmpty()) { onNumberChange("") }
        },
    )
}

@Composable
private fun NoResultsState(
    modifier: Modifier = Modifier,
    onAlt: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = rdp(32)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(rdp(72))
                .clip(CircleShape)
                .background(Colors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            SearchIcon(size = rdp(28), tint = Colors.Muted)
        }
        Spacer(Modifier.height(rdp(16)))
        XaniText(
            text = "No results",
            style = Type.H2,
            color = Colors.Text,
            align = TextAlign.Center,
            fontSizeOverride = 20f,
        )
        Spacer(Modifier.height(rdp(8)))
        XaniText(
            text = "Try again or can't find account?",
            style = Type.Caption,
            color = Colors.Sub,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(rdp(16)))
        TextLink(text = "Can't find account?", onClick = onAlt)
    }
}

@Composable
internal fun ClearFieldButton(
    visible: Boolean,
    onClear: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        Box(
            modifier = Modifier
                .size(rdp(22))
                .clip(CircleShape)
                .background(Colors.BorderStrong)
                .pressable(role = Role.Button, onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            CloseIcon(size = rdp(10), tint = Color.White)
        }
    }
}
