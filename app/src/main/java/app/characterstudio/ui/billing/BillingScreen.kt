package app.characterstudio.ui.billing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.characterstudio.R
import app.characterstudio.billing.BankDetails
import app.characterstudio.billing.Money
import app.characterstudio.billing.Order
import app.characterstudio.billing.Plan
import app.characterstudio.billing.PointPackage
import app.characterstudio.billing.SubscriptionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(vm: BillingViewModel, onBack: () -> Unit, onAccount: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(ctx.getString(it)); vm.messageShown() } }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bill_title)) },
                navigationIcon = { TextButton(onClick = { if (ui.bank != null) vm.closeBank() else onBack() }) { Text(stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (ui.busy || ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                !vm.configured -> Text(stringResource(R.string.account_not_configured), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                !ui.signedIn -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.bill_need_signin))
                    Button(onClick = onAccount) { Text(stringResource(R.string.home_account)) }
                }
                ui.bank != null -> BankDetailsView(ui.bank!!, ui.bankOrder, ui.busy, vm::submitTransfer, vm::cancelOrder)
                else -> {
                    TabRow(selectedTabIndex = tab) {
                        listOf(R.string.bill_tab_plans, R.string.bill_tab_points, R.string.bill_tab_orders).forEachIndexed { i, r ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(r)) })
                        }
                    }
                    val bankOn = ui.methods?.bankTransfer == true
                    when (tab) {
                        0 -> PlansTab(ui.subscription, ui.plans, bankOn, ui.methods?.card == true) { vm.buy("subscription", it) }
                        1 -> PointsTab(ui.packages, bankOn) { vm.buy("points", it) }
                        else -> OrdersTab(ui.orders, vm::openOrder)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String = stringResource(
    when (status) {
        "created" -> R.string.ord_created; "pending" -> R.string.ord_pending; "paid" -> R.string.ord_paid; "failed" -> R.string.ord_failed
        "canceled" -> R.string.ord_canceled; "expired" -> R.string.ord_expired; "refunded" -> R.string.ord_refunded
        "needs_review" -> R.string.ord_review; else -> R.string.ord_unknown
    },
)

@Composable
private fun PlansTab(sub: SubscriptionInfo?, plans: List<Plan>, bankOn: Boolean, cardOn: Boolean, onBuy: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.sub_status_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (sub?.status) {
                            "active" -> stringResource(R.string.sub_active, sub?.endsAt?.take(10).orEmpty())
                            "expired" -> stringResource(R.string.sub_expired)
                            "canceled" -> stringResource(R.string.sub_canceled)
                            "refunded" -> stringResource(R.string.sub_refunded)
                            "needs_attention" -> stringResource(R.string.sub_attention)
                            else -> stringResource(R.string.sub_none)
                        },
                    )
                    Text(stringResource(R.string.sub_expiry_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (cardOn) item { Text(stringResource(R.string.bill_card_pending), style = MaterialTheme.typography.bodySmall) }
        if (plans.isEmpty()) item { Text(stringResource(R.string.bill_no_plans)) }
        items(plans, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.nameAr, style = MaterialTheme.typography.titleMedium)
                    if (p.descriptionAr.isNotBlank()) Text(p.descriptionAr, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (p.discountPercent > 0) stringResource(R.string.bill_price_discount, Money.format(p.priceCents, p.currency), Money.format(p.finalCents, p.currency), p.discountPercent)
                        else Money.format(p.finalCents, p.currency),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(stringResource(R.string.bill_duration, p.durationDays), style = MaterialTheme.typography.bodySmall)
                    if (p.features.isEmpty()) Text(stringResource(R.string.bill_no_features), style = MaterialTheme.typography.bodySmall)
                    else p.features.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    if (p.renewalTermsAr.isNotBlank()) Text(p.renewalTermsAr, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onBuy(p.id) }, enabled = bankOn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (bankOn) R.string.bill_buy_bank else R.string.bill_bank_unavailable))
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsTab(packages: List<PointPackage>, bankOn: Boolean, onBuy: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.points_note), style = MaterialTheme.typography.bodySmall) }
        if (packages.isEmpty()) item { Text(stringResource(R.string.bill_no_packages)) }
        items(packages, key = { it.id }) { k ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(k.nameAr, style = MaterialTheme.typography.titleMedium)
                    if (k.descriptionAr.isNotBlank()) Text(k.descriptionAr)
                    Text(stringResource(R.string.bill_points_for, k.points, Money.format(k.priceCents, k.currency)), style = MaterialTheme.typography.titleSmall)
                    if (k.bonusNoteAr.isNotBlank()) Text(k.bonusNoteAr, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onBuy(k.id) }, enabled = bankOn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (bankOn) R.string.bill_buy_bank else R.string.bill_bank_unavailable))
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersTab(orders: List<Order>, onOpen: (Order) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (orders.isEmpty()) item { Text(stringResource(R.string.bill_no_orders)) }
        items(orders, key = { it.id }) { o ->
            Card(onClick = { onOpen(o) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(o.name.ifBlank { o.reference }, style = MaterialTheme.typography.titleSmall)
                    Text("${o.reference} • ${Money.format(o.amountCents, o.currency)}", style = MaterialTheme.typography.bodySmall)
                    Text(statusLabel(o.status) + " • " + o.createdAt.take(10), style = MaterialTheme.typography.bodySmall)
                    if (o.isOpenBankTransfer) Text(stringResource(R.string.bill_tap_details), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun BankDetailsView(b: BankDetails, order: Order?, busy: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    val clip = LocalClipboardManager.current
    var note by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.bank_title), style = MaterialTheme.typography.titleLarge)
        order?.let { Text(it.name, style = MaterialTheme.typography.titleSmall) }
        Text(stringResource(R.string.bank_amount, Money.format(b.amountCents, b.currency)), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.bank_beneficiary, b.beneficiary))
        Text(stringResource(R.string.bank_name, b.bank))
        Text("IBAN: " + b.iban, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { clip.setText(AnnotatedString(b.iban)) }) { Text(stringResource(R.string.bank_copy_iban)) }
        Text(stringResource(R.string.bank_reference, b.reference), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { clip.setText(AnnotatedString(b.reference)) }) { Text(stringResource(R.string.bank_copy_ref)) }
        if (b.instructions.isNotBlank()) Text(b.instructions, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.bank_expires, b.expiresAt.take(10)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.bank_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        if (b.status == "pending") {
            Text(stringResource(R.string.bank_waiting))
        } else {
            OutlinedTextField(note, { note = it.take(500) }, label = { Text(stringResource(R.string.bank_note)) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSubmit(note) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bank_i_transferred)) }
            TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.bank_cancel_order)) }
        }
    }
}
