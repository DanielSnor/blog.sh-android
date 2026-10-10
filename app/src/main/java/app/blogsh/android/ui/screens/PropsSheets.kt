package app.blogsh.android.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blogsh.android.R
import app.blogsh.android.model.ActionAnswer
import app.blogsh.android.model.Engine
import app.blogsh.android.model.EngineError
import app.blogsh.android.model.Markdown
import app.blogsh.android.model.PropsAnswer
import app.blogsh.android.model.TagStore
import app.blogsh.android.model.VersionsAnswer
import app.blogsh.android.model.engineInstant
import app.blogsh.android.model.isCalledOff
import app.blogsh.android.model.said
import app.blogsh.android.ui.Asks
import app.blogsh.android.ui.Busy
import app.blogsh.android.ui.Choice
import app.blogsh.android.ui.ChoiceRow
import app.blogsh.android.ui.DialogKey
import app.blogsh.android.ui.EmptyNote
import app.blogsh.android.ui.EngineLabel
import app.blogsh.android.ui.Faces
import app.blogsh.android.ui.FieldRow
import app.blogsh.android.ui.Hint
import app.blogsh.android.ui.Mark
import app.blogsh.android.ui.Menu
import app.blogsh.android.ui.MenuKey
import app.blogsh.android.ui.PaperRow
import app.blogsh.android.ui.PaperSheet
import app.blogsh.android.ui.Partial
import app.blogsh.android.ui.Plate
import app.blogsh.android.ui.Pressable
import app.blogsh.android.ui.PrimaryButton
import app.blogsh.android.ui.ProblemLine
import app.blogsh.android.ui.Room
import app.blogsh.android.ui.Said
import app.blogsh.android.ui.Says
import app.blogsh.android.ui.SwitchRow
import app.blogsh.android.ui.Symbols
import app.blogsh.android.ui.TagSuggestions
import app.blogsh.android.ui.Theme
import app.blogsh.android.ui.dialogGround
import app.blogsh.android.ui.gap
import app.blogsh.android.ui.mono
import app.blogsh.android.ui.ui
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * The schedule dialog: "publish when?", with the slot the engine would
 * offer already in the picker. `schedule <slug> --at <time> --json`.
 *
 * `current`: when the post is planned for now: rescheduling starts from
 * there, an hour or a day away from it, not from an hour from now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheet(slug: String, offered: String?, current: String? = null, scheduled: Boolean, onDismiss: () -> Unit, done: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    // The time it is planned for, when that is still to come; or the slot
    // offered, when that is; or an hour from now.
    var date by remember {
        val now = Instant.now()
        val planned = engineInstant(current)?.takeIf { it.isAfter(now) }
        val slot = engineInstant(offered)?.takeIf { it.isAfter(now) }
        mutableStateOf(planned ?: slot ?: now.plusSeconds(3600))
    }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<Said?>(null) }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingHour by remember { mutableStateOf(false) }
    val partial = Partial.words
    val scheduleAnyway = stringResource(R.string.schedule_anyway)

    /**
     * `anyway`: on a site of more than one language, a post not written
     * in all of them is scheduled only when told to be.
     */
    suspend fun schedule(anyway: Boolean = false) {
        busy = true
        try {
            val args = mutableListOf("schedule", slug, "--at", engineStamp(date))
            if (anyway) args.add("--allow-partial")
            Engine.call<ActionAnswer>(args)
            done()
            onDismiss()
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            if ((e as? EngineError.Refused)?.refusal?.error == Partial.CODE && !anyway) {
                // The answer is the sheet's to carry out: the dialog that asked is closed before it runs.
                said = Said(text = partial, ask = Said.Ask(scheduleAnyway) { scope.launch { schedule(anyway = true) } })
            } else {
                problem = e.said
            }
        } finally {
            busy = false
        }
    }

    /** Not before now, as the picker on iOS will not go: what is earlier than now is now. */
    fun pick(wanted: ZonedDateTime) {
        val now = Instant.now()
        date = wanted.toInstant().let { if (it.isBefore(now)) now else it }
    }

    val title = stringResource(if (scheduled) R.string.reschedule else R.string.schedule)
    PaperSheet(onDismiss, name = title) {
        Plate {
            row {
                // One picker on iOS, a day and an hour side by side; here the two
                // halves of it are two keys, each opening the system's own.
                FlowRow(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    EngineLabel(stringResource(R.string.publish_when))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val moment = Date.from(date)
                        PickerKey(DateFormat.getMediumDateFormat(context).format(moment)) { pickingDay = true }
                        PickerKey(DateFormat.getTimeFormat(context).format(moment)) { pickingHour = true }
                    }
                }
            }
        }
        if (offered != null) Hint(stringResource(R.string.the_time_offered_is_the_next_free))
        PrimaryButton(title, Modifier.gap(22), busy = busy) { scope.launch { schedule() } }
        problem?.let { ProblemLine(it) }
    }

    if (pickingDay) {
        DayPicker(
            date.atZone(zone).toLocalDate(), floor = LocalDate.now(zone),
            onPick = { day -> pick(date.atZone(zone).with(day).withSecond(0).withNano(0)) },
            onDismiss = { pickingDay = false },
        )
    }
    if (pickingHour) {
        val at = date.atZone(zone)
        HourPicker(
            at.hour, at.minute,
            onPick = { hour, minute -> pick(date.atZone(zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0)) },
            onDismiss = { pickingHour = false },
        )
    }
    Says(said) { said = null }
}

/**
 * A moment as `--at` takes it: with the device's own offset, as the
 * picker showed it. The engine files a post under the year of the time
 * as written, and in UTC the first hour of a year is still the old one.
 */
private fun engineStamp(date: Instant): String = Markdown.stamp(date.toEpochMilli())

/** One half of the picker as a row shows it: what is chosen, in a frame, opening the choice. */
@Composable
private fun PickerKey(text: String, onClick: () -> Unit) {
    Pressable(onClick) {
        Text(
            text, color = Theme.ink, style = ui(15f), maxLines = 1,
            modifier = Modifier.border(1.dp, Theme.line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * The system's pickers in the app's own dress: the washes Material would
 * fill with colours of its own are the accent's, and the words are in
 * the plain face.
 */
@Composable
private fun Dressed(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val wash = Theme.accent.copy(alpha = 0.14f).compositeOver(scheme.surfaceContainerHigh)
    val type = MaterialTheme.typography
    fun TextStyle.plain(): TextStyle = copy(fontFamily = Faces.sans)
    MaterialTheme(
        colorScheme = scheme.copy(
            primaryContainer = wash, onPrimaryContainer = Theme.ink,
            secondaryContainer = wash, onSecondaryContainer = Theme.ink,
            tertiaryContainer = wash, onTertiaryContainer = Theme.ink,
        ),
        typography = type.copy(
            displayLarge = type.displayLarge.plain(), displayMedium = type.displayMedium.plain(), displaySmall = type.displaySmall.plain(),
            headlineLarge = type.headlineLarge.plain(), headlineMedium = type.headlineMedium.plain(), headlineSmall = type.headlineSmall.plain(),
            titleLarge = type.titleLarge.plain(), titleMedium = type.titleMedium.plain(), titleSmall = type.titleSmall.plain(),
            bodyLarge = type.bodyLarge.plain(), bodyMedium = type.bodyMedium.plain(), bodySmall = type.bodySmall.plain(),
            labelLarge = type.labelLarge.plain(), labelMedium = type.labelMedium.plain(), labelSmall = type.labelSmall.plain(),
        ),
        content = content,
    )
}

/** The day: Material's calendar, with no day before `floor` to be had. It counts days in UTC, so that is how they are handed over. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(day: LocalDate, floor: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun millis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val first = millis(floor)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = millis(maxOf(day, floor)),
        yearRange = floor.year..maxOf(DatePickerDefaults.YearRange.last, day.year),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= first
            override fun isSelectableYear(year: Int): Boolean = year >= floor.year
        },
    )
    Dressed {
        val colors = DatePickerDefaults.colors(containerColor = dialogGround())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                DialogKey(stringResource(R.string.ok), enabled = state.selectedDateMillis != null) {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                }
            },
            dismissButton = { DialogKey(stringResource(R.string.cancel), quiet = true, onClick = onDismiss) },
            colors = colors,
        ) {
            DatePicker(state = state, colors = colors, title = null, showModeToggle = false)
        }
    }
}

/** The hour: Material's clock, by the twelve or the twenty-four as the device has it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourPicker(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    Dressed {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
            confirmButton = {
                DialogKey(stringResource(R.string.ok)) {
                    onPick(state.hour, state.minute)
                    onDismiss()
                }
            },
            dismissButton = { DialogKey(stringResource(R.string.cancel), quiet = true, onClick = onDismiss) },
            containerColor = dialogGround(),
        )
    }
}

private val types = listOf("-", "document", "video", "audio", "image", "chat", "quote", "link", "text")
private val threeStates = listOf("default", "yes", "no")

/**
 * The [e] screen: what the post IS. Each row is written only when it
 * changed, as one `--set key=value`; the words are the screen's own.
 */
@Composable
fun PropertiesForm(props: PropsAnswer, onDismiss: () -> Unit, done: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var series by remember { mutableStateOf(props.series ?: "") }
    var seriesPart by remember { mutableStateOf(props.seriesPart ?: "") }
    var tags by remember { mutableStateOf(props.tags.joinToString(", ")) }
    var type by remember { mutableStateOf(props.typeSet ?: "-") }
    var unlisted by remember { mutableStateOf(props.unlisted) }
    var hero by remember { mutableStateOf(props.hero) }
    var toc by remember { mutableStateOf(props.toc) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { TagStore.loadIfNeeded() }

    /** Only what changed, in the words `--set` takes. */
    fun sets(): List<String> {
        val out = mutableListOf<String>()
        val wantedSeries = series.trim(' ', '\t')
        if (wantedSeries != (props.series ?: "")) out.add("series=${wantedSeries.ifEmpty { "-" }}")
        val part = seriesPart.trim(' ', '\t')
        if (part != (props.seriesPart ?: "")) out.add("series_part=${part.ifEmpty { "-" }}")
        val wantedTags = tags.trim(' ', '\t')
        if (wantedTags != props.tags.joinToString(", ")) out.add("tags=${wantedTags.ifEmpty { "-" }}")
        if (type != (props.typeSet ?: "-")) out.add("type=$type")
        if (unlisted != props.unlisted) out.add("unlisted=${if (unlisted) "yes" else "no"}")
        if (hero != props.hero) out.add("hero=$hero")
        if (toc != props.toc) out.add("toc=$toc")
        return out
    }

    suspend fun save() {
        busy = true
        try {
            val args = mutableListOf("props", props.slug)
            for (set in sets()) args += listOf("--set", set)
            Engine.call<PropsAnswer>(args)
            done()
            onDismiss()
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            busy = false
        }
    }

    // A type as the menu says it: `-` is "let the content decide".
    val fromContent = stringResource(R.string.from_the_content, props.type)
    fun typeWord(value: String): String = if (value == "-") fromContent else value

    // A flag as the menu says it: yes, no, or whatever the site does.
    val yes = stringResource(R.string.flag_yes)
    val no = stringResource(R.string.flag_no)
    val sitesOwn = stringResource(R.string.the_site_s_own)
    fun flagWord(value: String): String = when (value) {
        "yes" -> yes
        "no" -> no
        else -> sitesOwn
    }

    // The pills are a row of the plate only while there is one to offer:
    // an empty row would still leave its rule behind.
    val (taken, typing) = TagStore.parts(tags)
    val suggesting = TagStore.suggest(typing, taken, from = TagStore.tags).isNotEmpty()

    PaperSheet(onDismiss, name = stringResource(R.string.properties)) {
        Plate {
            row { FieldRow(stringResource(R.string.series), series, { series = it }, labelWidth = 84.dp) }
            row {
                FieldRow(
                    stringResource(R.string.part_of_series), seriesPart, { seriesPart = it }, labelWidth = 84.dp,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            row {
                FieldRow(
                    stringResource(R.string.tags), tags, { tags = it }, labelWidth = 84.dp,
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
            }
            if (suggesting) row { TagSuggestions(tags, { tags = it }) }
            row {
                ChoiceRow(stringResource(R.string.type), typeWord(type), type, types.map { it to typeWord(it) }) { type = it }
            }
        }
        Plate(Modifier.gap(10)) {
            row { SwitchRow(stringResource(R.string.unlisted), unlisted, { unlisted = it }, property = true) }
            row {
                ChoiceRow(stringResource(R.string.lead_image), flagWord(hero), hero, threeStates.map { it to flagWord(it) }) { hero = it }
            }
            row {
                ChoiceRow(stringResource(R.string.chapter_list), flagWord(toc), toc, threeStates.map { it to flagWord(it) }) { toc = it }
            }
        }
        Hint(stringResource(R.string.properties_are_what_the_post_is_not))
        PrimaryButton(stringResource(R.string.save), Modifier.gap(22), enabled = sets().isNotEmpty(), busy = busy) {
            scope.launch { save() }
        }
        problem?.let { ProblemLine(it) }
    }
}

/**
 * The [a] screen: the addresses the post used to answer at, and the one
 * way to drop one. `props <slug> --drop-address <address> --json`.
 *
 * On iOS a row is swiped to be dropped; here it is pressed -- long, or
 * just pressed, since a row has nothing else to do -- and offers it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddressesSheet(props: PropsAnswer, onDismiss: () -> Unit, done: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf(props.addresses) }
    var offering by remember { mutableStateOf<PropsAnswer.OldAddress?>(null) }
    var dropping by remember { mutableStateOf<PropsAnswer.OldAddress?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    suspend fun drop(address: PropsAnswer.OldAddress) {
        try {
            val answer = Engine.call<PropsAnswer>(address.dropArgs(props.slug, addresses))
            addresses = answer.addresses
            done()
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    PaperSheet(
        onDismiss, scrolls = false, name = stringResource(R.string.old_links),
        count = if (addresses.isEmpty()) null else NumberFormat.getIntegerInstance().format(addresses.size),
        actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize()) {
                problem?.let { words ->
                    item { PaperRow { Box(Modifier.padding(bottom = 10.dp)) { ProblemLine(words) } } }
                }
                // A row is a list and an address: the same address can stand in two lists.
                items(addresses, key = { it.id }) { address ->
                    PaperRow(Modifier.combinedClickable(onClick = { offering = address }, onLongClick = { offering = address })) {
                        Column(Modifier.padding(vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(address.value, color = Theme.ink, style = mono(14f, bold = false))
                            Text(
                                // A former slug of the post, or of one of its languages (`translations.<lang>.former_slugs`).
                                stringResource(if (address.kind.endsWith("former_slugs")) R.string.a_former_slug_redirects_here else R.string.redirects_here),
                                color = Theme.muted, style = ui(13f),
                            )
                        }
                        Menu(offering == address, { offering = null }) {
                            MenuKey(stringResource(R.string.drop), danger = true) {
                                offering = null
                                dropping = address
                            }
                        }
                    }
                }
            }
            if (addresses.isEmpty()) {
                EmptyNote(Symbols.link, stringResource(R.string.this_post_has_no_old_addresses), room = Room.Part, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
    dropping?.let { address ->
        Asks(
            stringResource(R.string.drop_it_no_longer_redirects_anywhere, address.value),
            choices = listOf(Choice(stringResource(R.string.drop), danger = true) { scope.launch { drop(address) } }),
            onDismiss = { dropping = null },
        )
    }
}

/**
 * The [v] screen: what the post said before one of its recent saves.
 * `--versions` lists them, `--restore-version <name> --yes` restores one;
 * the text it replaces is kept as a version first.
 */
@Composable
fun VersionsSheet(slug: String, onDismiss: () -> Unit, done: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf(emptyList<VersionsAnswer.Version>()) }
    var restoring by remember { mutableStateOf<VersionsAnswer.Version?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    // One restoring at a time, and seen to be going: a second tap
    // meanwhile would restore over the first.
    var working by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            val answer = Engine.call<VersionsAnswer>("props", slug, "--versions")
            versions = answer.versions
            loaded = true
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        }
    }

    suspend fun restore(version: VersionsAnswer.Version) {
        if (working) return
        working = true
        try {
            Engine.call<PropsAnswer>("props", slug, "--restore-version", version.name, "--yes")
            done()
            onDismiss()
        } catch (e: Throwable) {
            if (e.isCalledOff) throw e
            problem = e.said
        } finally {
            working = false
        }
    }

    LaunchedEffect(Unit) { load() }

    PaperSheet(
        onDismiss, scrolls = false, name = stringResource(R.string.earlier_versions),
        count = if (versions.isEmpty()) null else NumberFormat.getIntegerInstance().format(versions.size),
        actions = { DialogKey(stringResource(R.string.done), onClick = onDismiss) },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize()) {
                problem?.let { words ->
                    item { PaperRow { Box(Modifier.padding(bottom = 10.dp)) { ProblemLine(words) } } }
                }
                items(versions, key = { it.id }) { version ->
                    PaperRow {
                        Pressable({ restoring = version }, enabled = !working) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(version.label, color = Theme.ink, style = ui(15f), modifier = Modifier.weight(1f))
                                Mark(Symbols.arrowUturnBackward, 17.dp)
                            }
                        }
                    }
                }
                if (versions.isNotEmpty()) {
                    item {
                        PaperRow(rule = false) { Hint(stringResource(R.string.pictures_are_not_versioned_only_the_text), Modifier.padding(bottom = 10.dp)) }
                    }
                }
            }
            if (working) Busy(modifier = Modifier.align(Alignment.Center))
            if (loaded && versions.isEmpty()) {
                EmptyNote(Symbols.clockArrowCirclepath, stringResource(R.string.no_earlier_versions_yet), room = Room.Part, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
    restoring?.let { version ->
        Asks(
            stringResource(R.string.restore_this_version_the_current_text_is),
            choices = listOf(Choice(stringResource(R.string.restore)) { scope.launch { restore(version) } }),
            onDismiss = { restoring = null },
        )
    }
}
