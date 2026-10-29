package app.characterstudio.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.characterstudio.CharacterStudioApp
import app.characterstudio.R
import app.characterstudio.auth.AccountRepository
import app.characterstudio.auth.AuthError
import app.characterstudio.auth.AuthException
import app.characterstudio.auth.AuthRepository
import app.characterstudio.auth.AuthState
import app.characterstudio.auth.AuthValidation
import app.characterstudio.auth.PointTx
import app.characterstudio.auth.SignUpOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUi(
    val busy: Boolean = false,
    val message: Int? = null,
    val balance: Int? = null,
    val walletFailed: Boolean = false,
    val txs: List<PointTx> = emptyList(),
)

class AccountViewModel(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    val configured: Boolean,
) : ViewModel() {
    val authState: StateFlow<AuthState> = auth.state
    private val _ui = MutableStateFlow(AccountUi())
    val ui: StateFlow<AccountUi> = _ui

    init {
        viewModelScope.launch {
            auth.state.collect { s -> if (s is AuthState.SignedIn) refreshWallet() else _ui.update { AccountUi() } }
        }
    }

    private fun msgFor(e: Throwable?): Int = when ((e as? AuthException)?.error) {
        AuthError.InvalidCredentials -> R.string.auth_err_invalid
        AuthError.EmailNotConfirmed -> R.string.auth_err_unconfirmed
        AuthError.AlreadyRegistered -> R.string.auth_err_exists
        AuthError.WeakPassword -> R.string.auth_err_weak
        AuthError.RateLimited -> R.string.auth_err_rate
        AuthError.Network -> R.string.auth_err_network
        AuthError.NotConfigured -> R.string.auth_err_notconfig
        else -> R.string.auth_err_unknown
    }

    private fun validate(email: String, password: String?): Int? = when {
        !AuthValidation.emailOk(email) -> R.string.auth_err_email_format
        password != null && !AuthValidation.passwordOk(password) -> R.string.auth_err_password_format
        else -> null
    }

    fun signIn(email: String, password: String) {
        validate(email, password)?.let { m -> _ui.update { it.copy(message = m) }; return }
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = auth.signIn(email, password)
            _ui.update { it.copy(busy = false, message = if (r.isFailure) msgFor(r.exceptionOrNull()) else null) }
        }
    }

    fun signUp(email: String, password: String) {
        validate(email, password)?.let { m -> _ui.update { it.copy(message = m) }; return }
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = auth.signUp(email, password)
            _ui.update {
                it.copy(
                    busy = false,
                    message = when {
                        r.isFailure -> msgFor(r.exceptionOrNull())
                        r.getOrNull() == SignUpOutcome.ConfirmEmail -> R.string.auth_confirm_email
                        else -> null
                    },
                )
            }
        }
    }

    fun recover(email: String) {
        validate(email, null)?.let { m -> _ui.update { it.copy(message = m) }; return }
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = auth.recover(email)
            _ui.update { it.copy(busy = false, message = if (r.isFailure) msgFor(r.exceptionOrNull()) else R.string.auth_recover_sent) }
        }
    }

    fun signOut() { viewModelScope.launch { auth.signOut() } }

    fun refreshWallet() {
        viewModelScope.launch {
            val b = account.balance()
            val t = account.transactions()
            _ui.update { it.copy(balance = b.getOrNull(), walletFailed = b.isFailure, txs = t.getOrDefault(emptyList())) }
        }
    }

    fun messageShown() { _ui.update { it.copy(message = null) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CharacterStudioApp).container
                AccountViewModel(c.authRepo, c.account, c.supabaseConfigured)
            }
        }
    }
}
