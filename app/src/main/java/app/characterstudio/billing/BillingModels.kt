package app.characterstudio.billing

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

object Money {
    /** السعر النهائي بعد الخصم: نفس معادلة الخادم (round(price*(100-discount)/100)). للعرض فقط؛ الخادم هو من يحدد المبلغ. */
    fun discounted(priceCents: Int, discountPercent: Int): Int =
        Math.round(priceCents * (100 - discountPercent.coerceIn(0, 100)) / 100.0).toInt()

    fun format(cents: Int, currency: String): String =
        String.format(Locale.US, "%d.%02d %s", cents / 100, cents % 100, currency)
}

data class Plan(
    val id: String, val nameAr: String, val descriptionAr: String, val priceCents: Int, val currency: String,
    val durationDays: Int, val discountPercent: Int, val features: List<String>, val renewalTermsAr: String, val cancellable: Boolean,
) { val finalCents: Int get() = Money.discounted(priceCents, discountPercent) }

data class PointPackage(
    val id: String, val nameAr: String, val descriptionAr: String, val points: Int, val priceCents: Int, val currency: String, val bonusNoteAr: String,
)

data class PaymentMethods(val card: Boolean, val bankTransfer: Boolean)
data class SubscriptionInfo(val status: String, val endsAt: String?)

data class Order(
    val id: String, val reference: String, val productType: String, val name: String, val amountCents: Int, val currency: String,
    val method: String, val status: String, val createdAt: String, val expiresAt: String,
) {
    /** طلب تحويل لم يُغلق بعد: يمكن عرض تفاصيله وإرسال إشعار التحويل. */
    val isOpenBankTransfer: Boolean get() = method == "bank_transfer" && (status == "created" || status == "pending")
}

data class BankDetails(
    val beneficiary: String, val bank: String, val iban: String, val instructions: String,
    val amountCents: Int, val currency: String, val reference: String, val status: String, val expiresAt: String,
)

enum class BillingError { NotSignedIn, NotConfigured, Network, BankDisabled, CardUnavailable, ProductGone, AccountDisabled, OrderClosed, Unknown }
class BillingException(val error: BillingError) : Exception(error.name)

object BillingErrors {
    fun fromServer(httpCode: Int, body: String): BillingError = when {
        httpCode == 401 || httpCode == 403 || "not_authenticated" in body -> BillingError.NotSignedIn
        "bank_transfer_disabled" in body -> BillingError.BankDisabled
        "card_not_configured" in body -> BillingError.CardUnavailable
        "product_not_available" in body -> BillingError.ProductGone
        "account_disabled" in body -> BillingError.AccountDisabled
        "order_not_open" in body || "order_not_cancelable" in body || "order_not_found" in body -> BillingError.OrderClosed
        else -> BillingError.Unknown
    }
}

object BillingParsing {
    private val json = Json { ignoreUnknownKeys = true }
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    private fun rows(body: String): List<JsonObject> =
        (runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }

    fun plans(body: String): List<Plan> = rows(body).mapNotNull { o ->
        Plan(
            id = o["id"].str() ?: return@mapNotNull null, nameAr = o["name_ar"].str() ?: return@mapNotNull null,
            descriptionAr = o["description_ar"].str().orEmpty(), priceCents = o["price_cents"].int() ?: return@mapNotNull null,
            currency = o["currency"].str() ?: return@mapNotNull null, durationDays = o["duration_days"].int() ?: return@mapNotNull null,
            discountPercent = o["discount_percent"].int() ?: 0,
            features = (o["features"] as? JsonArray)?.mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it.isString }?.content }.orEmpty(),
            renewalTermsAr = o["renewal_terms_ar"].str().orEmpty(), cancellable = o["cancellable"].bool() ?: false,
        )
    }

    fun packages(body: String): List<PointPackage> = rows(body).mapNotNull { o ->
        PointPackage(
            id = o["id"].str() ?: return@mapNotNull null, nameAr = o["name_ar"].str() ?: return@mapNotNull null,
            descriptionAr = o["description_ar"].str().orEmpty(), points = o["points"].int() ?: return@mapNotNull null,
            priceCents = o["price_cents"].int() ?: return@mapNotNull null, currency = o["currency"].str() ?: return@mapNotNull null,
            bonusNoteAr = o["bonus_note_ar"].str().orEmpty(),
        )
    }

    private fun order(o: JsonObject): Order? {
        val id = o["id"].str() ?: return null
        val productType = o["product_type"].str() ?: return null
        val amount = o["amount_cents"].int() ?: return null
        val currency = o["currency"].str() ?: return null
        val method = o["method"].str() ?: return null
        val status = o["status"].str() ?: return null
        return Order(
            id = id, reference = o["reference"].str().orEmpty(), productType = productType,
            name = ((o["snapshot"] as? JsonObject)?.get("name")).str().orEmpty(), amountCents = amount, currency = currency,
            method = method, status = status, createdAt = o["created_at"].str().orEmpty(), expiresAt = o["expires_at"].str().orEmpty(),
        )
    }

    fun orders(body: String): List<Order> = rows(body).mapNotNull { order(it) }
    fun singleOrder(body: String): Order? = runCatching { order(json.parseToJsonElement(body).jsonObject) }.getOrNull()

    fun methods(body: String): PaymentMethods? = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        PaymentMethods(card = o["card"].bool() ?: false, bankTransfer = o["bank_transfer"].bool() ?: false)
    }.getOrNull()

    fun subscription(body: String): SubscriptionInfo = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        SubscriptionInfo(o["status"].str() ?: "none", o["ends_at"].str())
    }.getOrDefault(SubscriptionInfo("none", null))

    fun bankDetails(body: String): BankDetails? {
        val o = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val iban = o["iban"].str() ?: return null
        val amount = o["amount_cents"].int() ?: return null
        val currency = o["currency"].str() ?: return null
        val reference = o["reference"].str() ?: return null
        return BankDetails(
            beneficiary = o["beneficiary"].str().orEmpty(), bank = o["bank"].str().orEmpty(), iban = iban,
            instructions = o["instructions"].str().orEmpty(), amountCents = amount, currency = currency, reference = reference,
            status = o["status"].str().orEmpty(), expiresAt = o["expires_at"].str().orEmpty(),
        )
    }
}
