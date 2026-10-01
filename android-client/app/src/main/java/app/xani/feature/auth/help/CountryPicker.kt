package app.xani.feature.auth.help

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import app.xani.ui.components.CloseIcon
import app.xani.ui.components.LightField
import app.xani.ui.components.SearchIcon
import app.xani.ui.components.XaniText
import app.xani.ui.theme.Dark
import app.xani.ui.theme.Colors
import app.xani.ui.theme.MatchWindowTo
import app.xani.ui.theme.SystemBarIcons
import app.xani.ui.theme.Type
import app.xani.ui.theme.rdp
import app.xani.ui.system.systemContentInsets

private const val ColorText = 0xFF111111.toInt()
private const val ColorSub = 0xFF888888.toInt()
private const val ColorPrimary = 0xFF8B5CF6.toInt()
private const val TypeHeader = 0
private const val TypeCountry = 1

private sealed interface CountryRow {
    val key: String

    data class Header(val letter: Char) : CountryRow {
        override val key: String = "h-$letter"
    }

    data class Item(val country: DialCountry) : CountryRow {
        override val key: String = country.iso
    }
}

@Composable
fun CountryPickerSheet(
    selectedIso: String,
    onSelect: (DialCountry) -> Unit,
    onClose: () -> Unit,
) {
    SystemBarIcons()
    BackHandler(onBack = onClose)

    var query by remember { mutableStateOf("") }
    val sections = remember(query) { DialCountries.grouped(query) }
    val rows = remember(sections) {
        buildList {
            sections.forEach { section ->
                add(CountryRow.Header(section.letter))
                section.countries.forEach { add(CountryRow.Item(it)) }
            }
        }
    }
    val letterToIndex = remember(rows) {
        buildMap {
            rows.forEachIndexed { index, row ->
                if (row is CountryRow.Header) put(row.letter, index)
            }
        }
    }
    val letters = remember(sections) { sections.map { it.letter } }
    var listView by remember { mutableStateOf<ListView?>(null) }
    var scrubLetter by remember { mutableStateOf<Char?>(null) }

    fun jumpTo(letter: Char, dragging: Boolean) {
        val index = letterToIndex[letter] ?: return
        if (scrubLetter != letter) {
            if (dragging) scrubLetter = letter
            listView?.setSelection(index)
        }
        if (!dragging) scrubLetter = null
    }

    MatchWindowTo(Colors.Background)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Background),
    ) {
        Column(Modifier.fillMaxSize().systemContentInsets()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Colors.Background)
                    .zIndex(1f),
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rdp(52)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = rdp(12))
                        .size(rdp(36))
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClose,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CloseIcon(size = rdp(16))
                }
                XaniText(
                    text = "Select country/region",
                    style = Type.LabelStrong,
                    color = Colors.Text,
                    align = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            LightField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search countries and regions",
                imeAction = ImeAction.Search,
                maxChars = 40,
                modifier = Modifier.padding(horizontal = rdp(16), vertical = rdp(8)),
                leading = { SearchIcon(size = rdp(18), tint = Colors.Muted) },
                trailing = {
                    ClearFieldButton(visible = query.isNotEmpty()) { query = "" }
                },
            )
            }

            Box(
                Modifier
                    .weight(1f)
                    .clipToBounds()
                    .zIndex(0f),
            ) {
                if (rows.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = rdp(32)),
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
                            text = "Try a different country or region.",
                            style = Type.Caption,
                            color = Colors.Sub,
                            align = TextAlign.Center,
                        )
                    }
                } else {
                    NativeCountryList(
                        rows = rows,
                        selectedIso = selectedIso,
                        onSelect = onSelect,
                        onReady = { listView = it },
                    )
                }

                if (letters.isNotEmpty() && query.isEmpty()) {
                    LetterReel(
                        letters = letters,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = rdp(12)),
                        onScrub = ::jumpTo,
                    )
                }

                if (scrubLetter != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(rdp(52))
                            .clip(CircleShape)
                            .background(Dark.Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        XaniText(
                            text = scrubLetter.toString(),
                            style = Type.H2,
                            color = androidx.compose.ui.graphics.Color.White,
                            align = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Platform ListView, not LazyColumn. Compose items are composed and discarded
 * during a fling; ListView recycles two view types on the UI thread scroller.
 */
@Composable
private fun NativeCountryList(
    rows: List<CountryRow>,
    selectedIso: String,
    onSelect: (DialCountry) -> Unit,
    onReady: (ListView) -> Unit,
) {
    val endPad = with(LocalDensity.current) { rdp(22).toPx().toInt() }
    val bg = Colors.Background.toArgb()
    val text = Colors.Text.toArgb()
    val sub = Colors.Sub.toArgb()
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            },
        factory = { context ->
            val list = ListView(context).apply {
                adapter = CountryAdapter(context)
                divider = null
                dividerHeight = 0
                selector = ColorDrawable(Color.TRANSPARENT)
                cacheColorHint = bg
                setBackgroundColor(bg)
                isNestedScrollingEnabled = false
                isScrollingCacheEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(0, 0, endPad, 0)
                clipToPadding = true
                clipChildren = true
                outlineProvider = ViewOutlineProvider.BOUNDS
                clipToOutline = true
                isVerticalFadingEdgeEnabled = false
                onReady(this)
            }
            FrameLayout(context).apply {
                clipChildren = true
                clipToPadding = true
                addView(
                    list,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        update = { host ->
            host.setBackgroundColor(bg)
            val list = host.getChildAt(0) as ListView
            list.cacheColorHint = bg
            list.setBackgroundColor(bg)
            (list.adapter as CountryAdapter).bind(rows, selectedIso, onSelect, bg, text, sub)
        },
    )
}

private class CountryAdapter(
    context: android.content.Context,
) : BaseAdapter() {
    private val density = context.resources.displayMetrics.density
    private val rowHeight = (48f * density).toInt()
    private val hPad = (20f * density).toInt()
    private val headerPadTop = (12f * density).toInt()
    private val headerPadBottom = (4f * density).toInt()

    private var rows: List<CountryRow> = emptyList()
    private var selectedIso: String = ""
    private var onSelect: (DialCountry) -> Unit = {}
    private var bg = android.graphics.Color.WHITE
    private var text = ColorText
    private var sub = ColorSub

    fun bind(
        rows: List<CountryRow>,
        selectedIso: String,
        onSelect: (DialCountry) -> Unit,
        bg: Int,
        text: Int,
        sub: Int,
    ) {
        this.onSelect = onSelect
        val changed = this.rows !== rows
            || this.selectedIso != selectedIso
            || this.bg != bg
            || this.text != text
            || this.sub != sub
        this.rows = rows
        this.selectedIso = selectedIso
        this.bg = bg
        this.text = text
        this.sub = sub
        if (changed) notifyDataSetChanged()
    }

    override fun getCount(): Int = rows.size
    override fun getItem(position: Int): CountryRow = rows[position]
    override fun getItemId(position: Int): Long = rows[position].key.hashCode().toLong()
    override fun hasStableIds(): Boolean = true
    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int =
        if (rows[position] is CountryRow.Header) TypeHeader else TypeCountry
    override fun areAllItemsEnabled(): Boolean = false
    override fun isEnabled(position: Int): Boolean = rows[position] is CountryRow.Item

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return when (val row = rows[position]) {
            is CountryRow.Header -> headerView(row, convertView, parent)
            is CountryRow.Item -> countryView(row, convertView, parent)
        }
    }

    private fun headerView(row: CountryRow.Header, convertView: View?, parent: ViewGroup): TextView {
        val view = convertView as? TextView ?: TextView(parent.context).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ColorSub)
            setPadding(hPad, headerPadTop, hPad, headerPadBottom)
            setBackgroundColor(Color.WHITE)
        }
        view.setTextColor(sub)
        view.setBackgroundColor(bg)
        view.text = row.letter.toString()
        return view
    }

    private fun countryView(row: CountryRow.Item, convertView: View?, parent: ViewGroup): View {
        val holder: ItemHolder
        val root: LinearLayout
        if (convertView != null) {
            root = convertView as LinearLayout
            holder = root.tag as ItemHolder
        } else {
            val context = parent.context
            val name = TextView(context).apply {
                textSize = 15f
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dial = TextView(context).apply {
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            holder = ItemHolder(name, dial)
            root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
                setPadding(hPad, 0, hPad, 0)
                setBackgroundColor(bg)
                addView(name)
                addView(dial)
                tag = holder
                setOnClickListener {
                    holder.country?.let { country -> onSelect(country) }
                }
            }
        }
        holder.country = row.country
        val selected = row.country.iso == selectedIso
        root.setBackgroundColor(bg)
        holder.name.text = row.country.name
        holder.name.setTextColor(if (selected) ColorPrimary else text)
        holder.dial.text = row.country.label
        holder.dial.setTextColor(if (selected) ColorPrimary else sub)
        return root
    }

    private class ItemHolder(
        val name: TextView,
        val dial: TextView,
        var country: DialCountry? = null,
    )
}

@Composable
private fun LetterReel(
    letters: List<Char>,
    modifier: Modifier = Modifier,
    onScrub: (Char, dragging: Boolean) -> Unit,
) {
    val letterColor = Colors.Sub.toArgb()
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    Canvas(
        modifier = modifier
            .width(rdp(18))
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun letterAt(y: Float): Char {
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val index = ((y / h) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        return letters[index]
                    }
                    var lastY = down.position.y
                    onScrub(letterAt(lastY), true)
                    drag(down.id) { change ->
                        lastY = change.position.y
                        onScrub(letterAt(lastY), true)
                        change.consume()
                    }
                    onScrub(letterAt(lastY), false)
                }
            },
    ) {
        paint.color = letterColor
        paint.textSize = 10.sp.toPx()
        val slot = size.height / letters.size.coerceAtLeast(1)
        val x = size.width / 2f
        val native = drawContext.canvas.nativeCanvas
        letters.forEachIndexed { index, letter ->
            native.drawText(
                letter.toString(),
                x,
                slot * index + slot / 2f + paint.textSize / 3f,
                paint,
            )
        }
    }
}
