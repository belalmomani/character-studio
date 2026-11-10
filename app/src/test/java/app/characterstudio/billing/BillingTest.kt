package app.characterstudio.billing

import app.characterstudio.auth.AuthRepository
import app.characterstudio.auth.Session
import app.characterstudio.auth.SessionStore
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BillingTest {
    @Test fun moneyFormatsAndDiscountMatchesServerFormula() {
        assertEquals("15.00 USD", Money.format(1500, "USD")); assertEquals("0.05 USD", Money.format(5, "USD")); assertEquals("100.00 USD", Money.format(10000, "USD"))
        assertEquals(1350, Money.discounted(1500, 10)); assertEquals(1500, Money.discounted(1500, 0))
        assertEquals(0, Money.discounted(1500, 100)); assertEquals(1500, Money.discounted(1500, -5)); assertEquals(0, Money.discounted(1500, 500))
        assertEquals(167, Money.discounted(333, 50)) // 166.5 تُقرَّب لأعلى كما في round() في Postgres
    }

    @Test fun parsesPlansAndSkipsBrokenRows() {
        val body = """[
          {"id":"p1","name_ar":"شهري","description_ar":"d","price_cents":1500,"currency":"USD","duration_days":30,"discount_percent":10,
           "features":["أ","ب",3],"renewal_terms_ar":"","cancellable":false},
          {"id":"p2","name_ar":"بلا سعر"},
          {"name_ar":"بلا معرّف","price_cents":1,"currency":"USD","duration_days":1}]"""
        val plans = BillingParsing.plans(body)
        assertEquals(1, plans.size); val p = plans[0]
        assertEquals("p1", p.id); assertEquals(listOf("أ", "ب"), p.features); assertEquals(1350, p.finalCents); assertFalse(p.cancellable)
        assertTrue(BillingParsing.plans("not json").isEmpty()); assertTrue(BillingParsing.plans("{}").isEmpty())
    }

    @Test fun parsesPackagesOrdersAndBank() {
        val pk = BillingParsing.packages("""[{"id":"k1","name_ar":"باقة","points":100,"price_cents":500,"currency":"USD"}]""")
        assertEquals(100, pk[0].points); assertEquals("", pk[0].bonusNoteAr)
        val order = """{"id":"o1","reference":"CS-ABC","product_type":"points","snapshot":{"name":"باقة","points":100},
          "amount_cents":500,"currency":"USD","method":"bank_transfer","status":"created","created_at":"2026-01-02T00:00:00Z","expires_at":"2026-01-09T00:00:00Z"}"""
        val o = BillingParsing.singleOrder(order)!!
        assertEquals("باقة", o.name); assertTrue(o.isOpenBankTransfer)
        assertFalse(BillingParsing.singleOrder(order.replace("created", "paid"))!!.isOpenBankTransfer)
        assertFalse(BillingParsing.singleOrder(order.replace("bank_transfer", "card"))!!.isOpenBankTransfer)
        assertNull(BillingParsing.singleOrder("""{"id":"x"}"""))
        val b = BillingParsing.bankDetails("""{"beneficiary":"م","bank":"ب","iban":"JO94CBJO0010000000000131000302","instructions":"i",
          "amount_cents":500,"currency":"USD","reference":"CS-ABC","status":"created","expires_at":"2026-01-09T00:00:00Z"}""")!!
        assertEquals("CS-ABC", b.reference); assertNull(BillingParsing.bankDetails("""{"iban":"x"}"""))
    }

    @Test fun parsesMethodsAndSubscription() {
        assertEquals(PaymentMethods(card = false, bankTransfer = true), BillingParsing.methods("""{"card":false,"bank_transfer":true}"""))
        assertNull(BillingParsing.methods("garbage"))
        assertEquals(SubscriptionInfo("active", "2026-02-01T00:00:00Z"), BillingParsing.subscription("""{"status":"active","ends_at":"2026-02-01T00:00:00Z"}"""))
        assertEquals("none", BillingParsing.subscription("""{"status":"none"}""").status)
        assertEquals("none", BillingParsing.subscription("broken").status)
    }

    @Test fun serverErrorsMap() {
        assertEquals(BillingError.BankDisabled, BillingErrors.fromServer(400, """{"message":"bank_transfer_disabled"}"""))
        assertEquals(BillingError.CardUnavailable, BillingErrors.fromServer(400, """{"message":"card_not_configured"}"""))
        assertEquals(BillingError.ProductGone, BillingErrors.fromServer(400, """{"message":"product_not_available"}"""))
        assertEquals(BillingError.AccountDisabled, BillingErrors.fromServer(400, """{"message":"account_disabled"}"""))
        assertEquals(BillingError.OrderClosed, BillingErrors.fromServer(400, """{"message":"order_not_open"}"""))
        assertEquals(BillingError.NotSignedIn, BillingErrors.fromServer(401, "{}"))
        assertEquals(BillingError.Unknown, BillingErrors.fromServer(400, "x"))
    }

    private class Api : BillingApi {
        var token: String? = null; var key: String? = null; var fail: Throwable? = null
        override suspend fun methods(token: String) = PaymentMethods(false, true)
        override suspend fun plans(token: String) = emptyList<Plan>()
        override suspend fun packages(token: String) = emptyList<PointPackage>()
        override suspend fun subscription(token: String) = SubscriptionInfo("none", null)
        override suspend fun orders(token: String) = emptyList<Order>()
        override suspend fun createOrder(token: String, productType: String, productId: String, method: String, key: String): Order {
            this.token = token; this.key = key; fail?.let { throw it }
            return Order("o", "CS-1", productType, "n", 100, "USD", method, "created", "", "")
        }
        override suspend fun bankDetails(token: String, orderId: String) = throw UnsupportedOperationException()
        override suspend fun submitTransfer(token: String, orderId: String, note: String?) { this.key = note }
        override suspend fun cancelOrder(token: String, orderId: String) {}
    }
    private class Store(var s: Session?) : SessionStore { override fun load() = s; override fun save(session: Session) { s = session }; override fun clear() { s = null } }
    private fun repo(api: BillingApi?, signedIn: Boolean) =
        BillingRepository(AuthRepository(null, Store(if (signedIn) Session("TOK", "R", 9_999_999_999, "u", "e") else null)) { 1L }, api)

    @Test fun requiresSessionAndConfiguration() = runTest {
        assertEquals(BillingError.NotSignedIn, (repo(Api(), false).plans().exceptionOrNull() as BillingException).error)
        assertEquals(BillingError.NotConfigured, (repo(null, true).plans().exceptionOrNull() as BillingException).error)
    }

    @Test fun orderUsesTokenKeyAndBankMethod() = runTest {
        val api = Api(); val o = repo(api, true).createBankOrder("points", "k1", "KEY-12345678").getOrThrow()
        assertEquals("TOK", api.token); assertEquals("KEY-12345678", api.key); assertEquals("bank_transfer", o.method)
    }

    @Test fun networkAndUnknownFailuresAreMapped() = runTest {
        val api = Api(); api.fail = IOException("down")
        assertEquals(BillingError.Network, (repo(api, true).createBankOrder("points", "k", "KEY-12345678").exceptionOrNull() as BillingException).error)
        api.fail = IllegalStateException("x")
        assertEquals(BillingError.Unknown, (repo(api, true).createBankOrder("points", "k", "KEY-12345678").exceptionOrNull() as BillingException).error)
        api.fail = BillingException(BillingError.ProductGone)
        assertEquals(BillingError.ProductGone, (repo(api, true).createBankOrder("points", "k", "KEY-12345678").exceptionOrNull() as BillingException).error)
    }

    @Test fun noteIsTrimmedCappedAndBlankBecomesNull() = runTest {
        val api = Api(); val r = repo(api, true)
        r.submitTransfer("o", "   "); assertNull(api.key)
        r.submitTransfer("o", "  " + "x".repeat(600)); assertEquals(500, api.key!!.length)
    }
}
