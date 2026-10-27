package app.characterstudio.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.characterstudio.R
import app.characterstudio.auth.AuthState
import app.characterstudio.auth.PointTx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: AccountViewModel, onBack: () -> Unit) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(ctx.getString(it)); vm.messageShown() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!vm.configured) {
                Text(stringResource(R.string.account_not_configured), color = MaterialTheme.colorScheme.error)
            }
            when (val a = auth) {
                AuthState.SignedOut -> SignedOutForm(vm, ui.busy)
                is AuthState.SignedIn -> SignedInView(vm, a, ui)
            }
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SignedOutForm(vm: AccountViewModel, busy: Boolean) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text(stringResource(R.string.account_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(email, { email = it }, singleLine = true, label = { Text(stringResource(R.string.field_email)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(password, { password = it }, singleLine = true, label = { Text(stringResource(R.string.field_password)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
    Button(onClick = { vm.signIn(email, password) }, enabled = !busy && vm.configured, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_sign_in))
    }
    OutlinedButton(onClick = { vm.signUp(email, password) }, enabled = !busy && vm.configured, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_sign_up))
    }
    TextButton(onClick = { vm.recover(email) }, enabled = !busy && vm.configured) { Text(stringResource(R.string.action_forgot)) }
}

@Composable
private fun SignedInView(vm: AccountViewModel, a: AuthState.SignedIn, ui: AccountUi) {
    Text(a.email, style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                ui.balance != null -> Text(stringResource(R.string.points_balance, ui.balance), style = MaterialTheme.typography.headlineSmall)
                ui.walletFailed -> {
                    Text(stringResource(R.string.points_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::refreshWallet) { Text(stringResource(R.string.library_refresh)) }
                }
                else -> CircularProgressIndicator()
            }
            Text(stringResource(R.string.points_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(stringResource(R.string.points_history), style = MaterialTheme.typography.titleSmall)
    if (ui.txs.isEmpty()) {
        Text(stringResource(R.string.points_history_empty), style = MaterialTheme.typography.bodySmall)
    } else {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ui.txs) { TxRow(it) }
        }
    }
    OutlinedButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_sign_out)) }
}

@Composable
private fun TxRow(t: PointTx) {
    val source = stringResource(
        when (t.source) {
            "spend" -> R.string.tx_spend
            "reward_ad" -> R.string.tx_reward_ad
            "purchase" -> R.string.tx_purchase
            "admin_grant" -> R.string.tx_admin_grant
            "admin_correction" -> R.string.tx_admin_correction
            else -> R.string.tx_other
        },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(source, style = MaterialTheme.typography.bodyMedium)
            Text(t.createdAt.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall)
        }
        Text((if (t.delta > 0) "+" else "") + t.delta, style = MaterialTheme.typography.titleSmall)
    }
}
