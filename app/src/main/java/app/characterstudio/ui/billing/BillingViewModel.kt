package app.characterstudio.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.characterstudio.CharacterStudioApp
import app.characterstudio.R
import app.characterstudio.auth.AuthRepository
import app.characterstudio.auth.AuthState
import app.characterstudio.billing.BankDetails
import app.characterstudio.billing.BillingError
import app.characterstudio.billing.BillingException
import app.characterstudio.billing.BillingRepository
import app.characterstudio.billing.Order
import app.characterstudio.billing.PaymentMethods
import app.characterstudio.billing.Plan
import app.characterstudio.billing.PointPackage
import app.characterstudio.billing.SubscriptionInfo
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingUi(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val signedIn: Boolean = false,
    val message: Int? = null,
    val methods: PaymentMethods? = null,
    val plans: List<Plan> = emptyList(),
    val packages: List<PointPackage> = emptyList(),
    val subscription: SubscriptionInfo? = null,
    val orders: List<Order> = emptyList(),
    val bankOrder: Order? = null,
    val bank: BankDetails? = null,
)

class BillingViewModel(
    private val repo: BillingRepository,
    private val auth: AuthRepository,
    val configured: Boolean,
) : ViewModel() {
    private val _ui = MutableStateFlow(BillingUi())
    val ui: StateFlow<BillingUi> = _ui
    /** مفتاح ثابت لكل منتج حتى تنجح المحاولة: إعادة المحاولة بعد انقطاع الشبكة لا تُنشئ طلبًا ثانيًا. */
    private val pendingKeys = HashMap<String, String>()

    init {
        viewModelScope.launch {
            auth.state.collect { s ->
                if (s is AuthState.SignedIn) { _ui.update { it.copy(signedIn = true) }; refresh() }
                else _ui.value = BillingUi(loading = false, signedIn = false)
            }
        }
    }

    private fun msgFor(e: Throwable?): Int = when ((e as? BillingException)?.error) {
        BillingError.NotSignedIn -> R.string.bill_err_signin
        BillingError.NotConfigured -> R.string.auth_err_notconfig
        BillingError.Network -> R.string.auth_err_network
        BillingError.BankDisabled -> R.string.bill_err_bank_disabled
        BillingError.CardUnavailable -> R.string.bill_err_card
        BillingError.ProductGone -> R.string.bill_err_product
        BillingError.AccountDisabled -> R.string.bill_err_disabled
        BillingError.OrderClosed -> R.string.bill_err_closed
        else -> R.string.bill_err_unknown
    }

    fun refresh() {
        _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            val m = repo.methods(); val p = repo.plans(); val k = repo.packages(); val s = repo.subscription(); val o = repo.orders()
            val firstError = listOf(m, p, k, s, o).firstNotNullOfOrNull { it.exceptionOrNull() }
            _ui.update {
                it.copy(
                    loading = false, methods = m.getOrNull() ?: it.methods, plans = p.getOrDefault(it.plans),
                    packages = k.getOrDefault(it.packages), subscription = s.getOrNull() ?: it.subscription,
                    orders = o.getOrDefault(it.orders), message = firstError?.let { e -> msgFor(e) },
                )
            }
        }
    }

    private suspend fun refreshOrders() { repo.orders().onSuccess { list -> _ui.update { it.copy(orders = list) } } }

    private suspend fun showBank(order: Order) {
        repo.bankDetails(order.id)
            .onSuccess { b -> _ui.update { it.copy(busy = false, bankOrder = order, bank = b) }; refreshOrders() }
            .onFailure { e -> _ui.update { it.copy(busy = false, message = msgFor(e)) } }
    }

    fun buy(productType: String, productId: String) {
        if (_ui.value.busy) return
        val mapKey = "$productType:$productId"
        val key = pendingKeys.getOrPut(mapKey) { UUID.randomUUID().toString() }
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.createBankOrder(productType, productId, key)
                .onSuccess { order -> pendingKeys.remove(mapKey); showBank(order) }
                .onFailure { e -> _ui.update { it.copy(busy = false, message = msgFor(e)) } }
        }
    }

    fun openOrder(order: Order) {
        if (!order.isOpenBankTransfer || _ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch { showBank(order) }
    }

    fun closeBank() {
        _ui.update { it.copy(bank = null, bankOrder = null) }
        viewModelScope.launch { refreshOrders() }
    }

    fun submitTransfer(note: String) {
        val order = _ui.value.bankOrder ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.submitTransfer(order.id, note)
                .onSuccess { _ui.update { it.copy(message = R.string.bill_transfer_sent) }; showBank(order) }
                .onFailure { e -> _ui.update { it.copy(busy = false, message = msgFor(e)) } }
        }
    }

    fun cancelOrder() {
        val order = _ui.value.bankOrder ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            repo.cancelOrder(order.id)
                .onSuccess { _ui.update { it.copy(busy = false, bank = null, bankOrder = null, message = R.string.bill_order_canceled) }; refreshOrders() }
                .onFailure { e -> _ui.update { it.copy(busy = false, message = msgFor(e)) } }
        }
    }

    fun messageShown() { _ui.update { it.copy(message = null) } }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CharacterStudioApp).container
                BillingViewModel(c.billing, c.authRepo, c.supabaseConfigured)
            }
        }
    }
}
