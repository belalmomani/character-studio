package app.characterstudio.billing

import app.characterstudio.net.Http
import java.io.IOException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** القراءة عبر PostgREST بحماية RLS، والكتابة عبر دوال الخادم فقط (create_order وغيرها). التطبيق لا يحدد سعرًا ولا حالة. */
class SupabaseBillingApi(baseUrl: String, private val anonKey: String) : BillingApi {
    private val base = baseUrl.trimEnd('/')
    private fun h(token: String) = mapOf("apikey" to anonKey, "Authorization" to "Bearer $token", "Accept" to "application/json")

    private suspend fun call(method: String, path: String, token: String, body: String? = null): String {
        val r = Http.call(method, base + path, h(token), body)
        if (r.code in 500..599) throw IOException("server_${r.code}")
        if (!r.ok) throw BillingException(BillingErrors.fromServer(r.code, r.body))
        return r.body
    }

    private suspend fun rpc(name: String, token: String, body: String = "{}") = call("POST", "/rest/v1/rpc/$name", token, body)

    override suspend fun methods(token: String) =
        BillingParsing.methods(rpc("get_payment_methods", token)) ?: throw BillingException(BillingError.Unknown)

    override suspend fun plans(token: String) = BillingParsing.plans(call("GET",
        "/rest/v1/subscription_plans?select=id,name_ar,description_ar,price_cents,currency,duration_days,discount_percent,features,renewal_terms_ar,cancellable&published=eq.true&order=sort_order", token))

    override suspend fun packages(token: String) = BillingParsing.packages(call("GET",
        "/rest/v1/point_packages?select=id,name_ar,description_ar,points,price_cents,currency,bonus_note_ar&active=eq.true&order=sort_order", token))

    override suspend fun subscription(token: String) = BillingParsing.subscription(rpc("current_subscription", token))

    override suspend fun orders(token: String) = BillingParsing.orders(call("GET",
        "/rest/v1/payment_orders?select=id,reference,product_type,snapshot,amount_cents,currency,method,status,created_at,expires_at&order=created_at.desc&limit=30", token))

    override suspend fun createOrder(token: String, productType: String, productId: String, method: String, key: String): Order {
        val body = buildJsonObject {
            put("p_product_type", productType); put("p_product_id", productId); put("p_method", method); put("p_key", key)
        }.toString()
        return BillingParsing.singleOrder(rpc("create_order", token, body)) ?: throw BillingException(BillingError.Unknown)
    }

    override suspend fun bankDetails(token: String, orderId: String) =
        BillingParsing.bankDetails(rpc("get_bank_transfer_details", token, buildJsonObject { put("p_order", orderId) }.toString()))
            ?: throw BillingException(BillingError.Unknown)

    override suspend fun submitTransfer(token: String, orderId: String, note: String?) {
        rpc("submit_bank_transfer", token, buildJsonObject {
            put("p_order", orderId); put("p_receipt_path", JsonNull); put("p_note", if (note == null) JsonNull else JsonPrimitive(note))
        }.toString())
    }

    override suspend fun cancelOrder(token: String, orderId: String) {
        rpc("cancel_my_order", token, buildJsonObject { put("p_order", orderId) }.toString())
    }
}
