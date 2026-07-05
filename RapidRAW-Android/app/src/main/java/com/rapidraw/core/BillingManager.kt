package com.rapidraw.core

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google Play Billing 计费管理器 — v1.7.0 正式版。
 *
 * 管理 LUT 包、预设包、订阅等应用内购买。
 * 基于 Google Play Billing Library 7.x。
 *
 * 特性：
 * - 支持一次性购买（inapp）和订阅（subs）
 * - 自动恢复购买（restore purchases）
 * - 购买状态持久化
 * - 离线缓存：购买后先本地标记，联网后校验
 * - 沙箱模式：无 Google Play 环境时自动降级为免费模式
 *
 * 使用方式：
 * 1. BillingManager.init(this) 在 Application.onCreate 中
 * 2. BillingManager.purchase(activity, productId) 发起购买
 * 3. BillingManager.isPurchased(productId) 检查是否已购买
 *
 * @since v1.7.0
 */
class BillingManager private constructor(
    private val context: Context,
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        private const val PREFS_NAME = "billing"
        private const val KEY_PURCHASED = "purchased_"

        @Volatile
        private var instance: BillingManager? = null

        fun getInstance(): BillingManager {
            return instance ?: throw IllegalStateException("BillingManager not initialized. Call init() first.")
        }

        fun init(context: Context): BillingManager {
            return instance ?: synchronized(this) {
                instance ?: BillingManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = SafePreferences.get(context, PREFS_NAME)
    private val initialized = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val userDisconnected = AtomicBoolean(false)

    private var billingClient: BillingClient? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSandbox = MutableStateFlow(false)
    val isSandbox: StateFlow<Boolean> = _isSandbox.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private var pendingPurchaseCallback: ((Boolean, String?) -> Unit)? = null

    /**
     * 连接到 Google Play Billing 服务。
     * 若设备不支持（如非 Play 版本），自动降级为沙箱模式。
     */
    fun connect() {
        if (destroyed.get()) {
            Log.w(TAG, "BillingManager already destroyed, recreate instance to reconnect")
            return
        }
        if (initialized.compareAndSet(false, true)) {
            userDisconnected.set(false)
            if (!(scope.coroutineContext[Job]?.isActive == true)) {
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            scope.launch { doConnect() }
        }
    }

    private suspend fun doConnect() {
        try {
            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build()

            startConnection()
        } catch (e: Exception) {
            Log.w(TAG, "Billing not available, entering sandbox mode", e)
            _isSandbox.value = true
            _isReady.value = true
        }
    }

    private suspend fun startConnection() {
        val client = billingClient ?: return
        try {
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.i(TAG, "Billing connected")
                            _isReady.value = true
                            _isSandbox.value = false
                            scope.launch { restorePurchases() }
                            cont.resume(Unit)
                        } else {
                            Log.w(TAG, "Billing setup failed: ${billingResult.responseCode}")
                            _isSandbox.value = true
                            _isReady.value = true
                            cont.resume(Unit)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (destroyed.get() || userDisconnected.get()) {
                            Log.d(TAG, "Skipping billing reconnect after disconnect/destroy")
                            return
                        }
                        Log.w(TAG, "Billing service disconnected")
                        _isReady.value = false
                        scope.launch {
                            kotlinx.coroutines.delay(5_000)
                            if (!destroyed.get() && !userDisconnected.get()) {
                                startConnection()
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            _isSandbox.value = true
            _isReady.value = true
        }
    }

    /**
     * 查询产品详情。
     * @param productIds 产品 ID 列表
     * @param productType 产品类型：BillingClient.ProductType.INAPP 或 BillingClient.ProductType.SUBS
     */
    suspend fun queryProductDetails(
        productIds: List<String>,
        productType: String = BillingClient.ProductType.INAPP,
    ): List<ProductDetails> {
        if (_isSandbox.value) return emptyList()

        val client = billingClient ?: return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(productType)
                        .build()
                }
            )
            .build()

        return suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { billingResult, details ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
                    val map = details.associateBy { it.productId }
                    _productDetails.value = _productDetails.value + map
                    cont.resume(details)
                } else {
                    cont.resume(emptyList())
                }
            }
        }
    }

    /**
     * 发起购买。
     * @param activity 当前 Activity
     * @param productId 产品 ID
     * @param offerToken 订阅优惠 token（可选）
     * @param onResult 回调 (success: Boolean, errorMessage: String?)
     */
    fun purchase(
        activity: Activity,
        productId: String,
        offerToken: String? = null,
        onResult: (Boolean, String?) -> Unit = { _, _ -> },
    ) {
        if (_isSandbox.value) {
            // 沙箱模式：直接标记为已购买
            markPurchased(productId)
            onResult(true, null)
            return
        }

        val client = billingClient ?: run {
            onResult(false, "Billing not available")
            return
        }
        val details = _productDetails.value[productId] ?: run {
            onResult(false, "Product not found")
            return
        }

        pendingPurchaseCallback = onResult

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        client.launchBillingFlow(activity, params)
    }

    /**
     * 检查产品是否已购买。
     */
    fun isPurchased(productId: String): Boolean {
        if (_isSandbox.value) return SafePreferences.getBoolean(prefs, KEY_PURCHASED + productId, false)
        return SafePreferences.getBoolean(prefs, KEY_PURCHASED + productId, false)
    }

    /**
     * 恢复购买。
     */
    suspend fun restorePurchases() {
        val client = billingClient ?: return
        suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            markPurchased(purchase.products.firstOrNull() ?: continue)
                            // 确认购买（非消耗品）
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchase(purchase)
                            }
                        }
                    }
                }
                cont.resume(Unit)
            }
        }

        // 恢复订阅
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        markPurchased(purchase.products.firstOrNull() ?: continue)
                    }
                }
            }
        }
    }

    /**
     * 消耗产品（用于消耗型商品，如金币）。
     */
    fun consumePurchase(purchaseToken: String) {
        val client = billingClient ?: return
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        client.consumeAsync(params) { _, _ -> }
    }

    /**
     * 确认购买（非消耗品必须确认）。
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { _ -> }
    }

    private fun markPurchased(productId: String) {
        if (productId.isBlank()) return
        SafePreferences.putBoolean(prefs, KEY_PURCHASED + productId, true)
    }

    // ── PurchasesUpdatedListener ──────────────────────────────────────

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val callback = pendingPurchaseCallback
        pendingPurchaseCallback = null

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    markPurchased(purchase.products.firstOrNull() ?: return@forEach)
                    if (!purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }
                callback?.invoke(true, null)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                callback?.invoke(false, "User cancelled")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                purchases?.forEach { markPurchased(it.products.firstOrNull() ?: return@forEach) }
                callback?.invoke(true, null)
            }
            else -> {
                callback?.invoke(false, billingResult.debugMessage ?: "Purchase failed")
            }
        }
    }

    fun disconnect() {
        userDisconnected.set(true)
        billingClient?.endConnection()
        initialized.set(false)
    }

    /**
     * v1.10.6: 关闭 BillingManager，取消协程并释放连接。
     * 应在 Application.onTerminate() 或长生命周期结束时调用，防止 BillingClient 泄漏。
     */
    fun shutdown() {
        if (!destroyed.compareAndSet(false, true)) return
        disconnect()
        scope.coroutineContext[Job]?.cancel()
        instance = null
        Log.i(TAG, "BillingManager shutdown")
    }
}