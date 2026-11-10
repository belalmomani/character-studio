package app.characterstudio.billing

import app.characterstudio.auth.AuthRepository
import java.io.IOException

interface BillingApi {
    suspend fun methods(token: String): PaymentMethods
    suspend fun plans(token: String): List<Plan>
    suspend fun packages(token: String): List<PointPackage>
    suspend fun subscription(token: String): SubscriptionInfo
    suspend fun orders(token: String): List<Order>
    /** السعر يحسبه الخادم؛ المفتاح يمنع تكرار الطلب عند إعادة المحاولة. */
    suspend fun createOrder(token: String, productType: String, productId: String, method: String, key: String): Order
    suspend fun bankDetails(token: String, orderId: String): BankDetails
    suspend fun submitTransfer(token: String, orderId: String, note: String?)
    suspend fun cancelOrder(token: String, orderId: String)
}

class BillingRepository(private val auth: AuthRepository, private val api: BillingApi?) {
    private suspend fun <T> call(block: suspend (BillingApi, String) -> T): Result<T> {
        val a = api ?: return Result.failure(BillingException(BillingError.NotConfigured))
        val token = auth.accessToken() ?: return Result.failure(BillingException(BillingError.NotSignedIn))
        return runCatching { block(a, token) }.recoverCatching { e ->
            throw when (e) {
                is BillingException -> e
                is IOException -> BillingException(BillingError.Network)
                else -> BillingException(BillingError.Unknown)
            }
        }
    }

    suspend fun methods() = call { a, t -> a.methods(t) }
    suspend fun plans() = call { a, t -> a.plans(t) }
    suspend fun packages() = call { a, t -> a.packages(t) }
    suspend fun subscription() = call { a, t -> a.subscription(t) }
    suspend fun orders() = call { a, t -> a.orders(t) }
    suspend fun createBankOrder(productType: String, productId: String, key: String) =
        call { a, t -> a.createOrder(t, productType, productId, "bank_transfer", key) }
    suspend fun bankDetails(orderId: String) = call { a, t -> a.bankDetails(t, orderId) }
    suspend fun submitTransfer(orderId: String, note: String?) = call { a, t -> a.submitTransfer(t, orderId, note?.trim()?.take(500)?.ifEmpty { null }) }
    suspend fun cancelOrder(orderId: String) = call { a, t -> a.cancelOrder(t, orderId) }
}
