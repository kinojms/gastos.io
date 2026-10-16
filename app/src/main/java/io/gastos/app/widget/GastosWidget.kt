package io.gastos.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.gastos.app.GastosApp
import io.gastos.app.data.LabelEntity
import io.gastos.app.data.LastUsed
import io.gastos.app.data.PaymentMethodEntity
import io.gastos.app.domain.AmountInput
import io.gastos.app.domain.Money

/** Home-screen numpad: label and payment-method tickers, amount, 4-column pad with Enter and today's total (mockup screen 7). */
class GastosWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Exact so the pad can give the Enter key exactly two rows of height.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as GastosApp).container
        provideContent {
            // Live flows read inside the composition: data and label changes re-render without
            // re-running provideGlance, and keypad taps never touch the database.
            val labels by remember { container.labels.observeAll() }.collectAsState(emptyList())
            val lastLabelId by remember { container.prefs.lastLabelId }.collectAsState(null)
            val methods by remember { container.paymentMethods.observeActive() }.collectAsState(emptyList())
            val lastMethodId by remember { container.prefs.lastPaymentMethodId }.collectAsState(null)
            val today = container.expenses.today() // device zone, at render time
            val total by remember(today) { container.expenses.observeDayTotal(today) }.collectAsState(0L)
            val input = currentState<Preferences>()[INPUT] ?: ""
            val label = labels.firstOrNull { it.id == lastLabelId } ?: labels.firstOrNull()
            // Same rule as AppPrefs.resolvePaymentMethod, which Enter uses.
            val method = LastUsed.method(methods, lastMethodId)
            WidgetContent(input, label, method, total)
        }
    }

    companion object {
        val INPUT = stringPreferencesKey("input")
    }
}

private val Background = ColorProvider(Color(0xEB121820))
private val KeyBg = ColorProvider(Color(0x1AFFFFFF))
private val Ink = ColorProvider(Color(0xFFE8EDF2))
private val Muted = ColorProvider(Color(0x99E8EDF2))
private val Amber = ColorProvider(Color(0xFFF2B33A))
private val AmberInk = ColorProvider(Color(0xFF1C1300))

private val OuterPadding = 12.dp
private val TopRowHeight = 40.dp
private val Gap = 3.dp // half the spacing between keys

@Composable
private fun WidgetContent(input: String, label: LabelEntity?, method: PaymentMethodEntity?, total: Long) {
    val padHeight = (LocalSize.current.height - OuterPadding * 2 - TopRowHeight).coerceAtLeast(0.dp)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(Background).cornerRadius(24.dp)
            .padding(OuterPadding - Gap),
    ) {
        TopRow(input, label, method)
        // Row 1
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            DigitKey(1)
            DigitKey(2)
            DigitKey(3)
            PadKey("⌫", actionRunCallback<BackspaceAction>(), description = "Backspace", fontSize = 14)
        }
        // Rows 2-3: three two-key digit columns plus a two-row Enter.
        Row(GlanceModifier.fillMaxWidth().height(padHeight / 2)) {
            DigitColumn(4, 7)
            DigitColumn(5, 8)
            DigitColumn(6, 9)
            PadKey("Enter", actionRunCallback<EnterAction>(), background = Amber, color = AmberInk, fontSize = 14)
        }
        // Row 4: dot, zero, and today's total across two columns.
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Row(GlanceModifier.defaultWeight().fillMaxHeight()) {
                PadKey(".", actionRunCallback<DotAction>(), description = "Decimal point")
                DigitKey(0)
            }
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(Gap),
                contentAlignment = Alignment.Center,
            ) {
                Text("Today ${Money.format(total)}", style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun TopRow(input: String, label: LabelEntity?, method: PaymentMethodEntity?) {
    val amount = "₱" + AmountInput.display(input)
    val fit = WidgetLogic.fitTopRow(LocalSize.current.width.value, label?.name ?: "—", method?.name ?: "—", amount)
    val nameMax = fit.nameMax
    val methodInk = method?.let { ColorProvider(Color(WidgetLogic.onMethodColor(it.colorArgb))) } ?: Ink
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(TopRowHeight).padding(horizontal = Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickerPill(
            name = label?.name?.let { WidgetLogic.shortName(it, nameMax) } ?: "—",
            background = KeyBg,
            ink = Ink,
            chevron = Muted,
            action = actionRunCallback<NextLabelAction>(),
            description = "Label ${label?.name ?: "none"}, tap for next",
        )
        Spacer(GlanceModifier.width(4.dp))
        TickerPill(
            name = method?.name?.let { WidgetLogic.shortName(it, nameMax) } ?: "—",
            background = method?.let { ColorProvider(Color(it.colorArgb)) } ?: KeyBg,
            ink = methodInk,
            chevron = methodInk,
            action = actionRunCallback<NextMethodAction>(),
            description = "Payment ${method?.name ?: "none"}, tap for next",
        )
        Text(
            amount,
            modifier = GlanceModifier.defaultWeight().padding(start = 4.dp),
            style = TextStyle(
                color = Ink,
                fontSize = fit.amountSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            ),
            maxLines = 1,
        )
    }
}

/** A tappable rounded ticker showing [name] with a trailing chevron. */
@Composable
private fun TickerPill(
    name: String,
    background: ColorProvider,
    ink: ColorProvider,
    chevron: ColorProvider,
    action: Action,
    description: String,
) {
    Row(
        modifier = GlanceModifier
            .background(background).cornerRadius(999.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .clickable(action)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Text(" ›", style = TextStyle(color = chevron, fontSize = 11.sp))
    }
}

@Composable
private fun RowScope.DigitColumn(top: Int, bottom: Int) {
    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
        DigitKey(top)
        DigitKey(bottom)
    }
}

@Composable
private fun RowScope.DigitKey(n: Int) = PadKey(n.toString(), digitAction(n))

@Composable
private fun ColumnScope.DigitKey(n: Int) {
    Box(GlanceModifier.fillMaxWidth().defaultWeight().padding(Gap)) {
        KeyFace(n.toString(), digitAction(n), KeyBg, Ink, 16, null)
    }
}

@Composable
private fun RowScope.PadKey(
    text: String,
    action: Action,
    description: String? = null,
    background: ColorProvider = KeyBg,
    color: ColorProvider = Ink,
    fontSize: Int = 16,
) {
    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(Gap)) {
        KeyFace(text, action, background, color, fontSize, description)
    }
}

@Composable
private fun KeyFace(
    text: String,
    action: Action,
    background: ColorProvider,
    color: ColorProvider,
    fontSize: Int,
    description: String?,
) {
    var modifier = GlanceModifier.fillMaxSize().background(background).cornerRadius(10.dp).clickable(action)
    if (description != null) modifier = modifier.semantics { contentDescription = description }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = TextStyle(color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

private fun digitAction(n: Int): Action =
    actionRunCallback<DigitAction>(actionParametersOf(DigitAction.DIGIT to n))
