package com.adskiper.skipflow.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.adskiper.skipflow.data.PreferencesRepository
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingManager private constructor(
    private val context: Context,
    private val prefRepo: PreferencesRepository
) : PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "BillingManager"

        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context, prefRepo: PreferencesRepository): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext, prefRepo).also { INSTANCE = it }
            }
        }
    }

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isBillingReady = MutableStateFlow(false)
    val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()

    private val _monthlyPrice = MutableStateFlow(BillingConstants.DEFAULT_MONTHLY_PRICE)
    val monthlyPrice: StateFlow<String> = _monthlyPrice.asStateFlow()

    private val _yearlyPrice = MutableStateFlow(BillingConstants.DEFAULT_YEARLY_PRICE)
    val yearlyPrice: StateFlow<String> = _yearlyPrice.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startBillingConnection() {
        if (!billingClient.isReady) {
            Log.i(TAG, "Starting Google Play Billing connection...")
            billingClient.startConnection(this)
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "BillingClient connected successfully.")
            _isBillingReady.value = true
            querySubscriptionProducts()
            queryActivePurchases()
        } else {
            Log.w(TAG, "BillingClient setup failed with code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            _isBillingReady.value = false
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "BillingClient disconnected. Will reconnect on next request.")
        _isBillingReady.value = false
    }

    private fun querySubscriptionProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.PRODUCT_MONTHLY_SUBSCRIPTION)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BillingConstants.PRODUCT_YEARLY_SUBSCRIPTION)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Retrieved ${productDetailsList.size} subscription products from Google Play.")
                for (details in productDetailsList) {
                    productDetailsMap[details.productId] = details
                    val formattedPrice = details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice

                    if (formattedPrice != null) {
                        if (details.productId == BillingConstants.PRODUCT_MONTHLY_SUBSCRIPTION) {
                            _monthlyPrice.value = formattedPrice
                        } else if (details.productId == BillingConstants.PRODUCT_YEARLY_SUBSCRIPTION) {
                            _yearlyPrice.value = formattedPrice
                        }
                    }
                }
            } else {
                Log.e(TAG, "Error querying product details: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryActivePurchases() {
        if (!billingClient.isReady) {
            startBillingConnection()
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActiveSubscription = false
                var activePlanId = ""

                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasActiveSubscription = true
                        activePlanId = purchase.products.firstOrNull() ?: ""
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }

                managerScope.launch {
                    if (hasActiveSubscription) {
                        Log.i(TAG, "Active Google Play subscription verified: $activePlanId")
                        prefRepo.setPremiumActive(true, activePlanId)
                    } else {
                        // Check if reviewer bypass is on; only set false if not bypassed
                        if (!prefRepo.isReviewerBypassEnabled()) {
                            prefRepo.setPremiumActive(false, "")
                        }
                    }
                }
            } else {
                Log.w(TAG, "Failed to query active purchases: ${billingResult.debugMessage}")
            }
        }
    }

    private suspend fun PreferencesRepository.isReviewerBypassEnabled(): Boolean {
        return isUnlimitedUnlockedSync() && !getFreeSkipsUsedSync().let { false } // checks cached reviewer bypass
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "User canceled billing purchase flow.")
        } else {
            Log.e(TAG, "Billing purchase update failed: code ${billingResult.responseCode}, ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val productId = purchase.products.firstOrNull() ?: ""
            managerScope.launch {
                Log.i(TAG, "Purchase successful for plan: $productId")
                prefRepo.setPremiumActive(true, productId)
            }

            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Purchase acknowledged successfully.")
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String): Boolean {
        if (!billingClient.isReady) {
            Log.w(TAG, "BillingClient not ready. Reconnecting...")
            startBillingConnection()
            return false
        }

        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            Log.w(TAG, "ProductDetails not found for: $productId. Requesting products...")
            querySubscriptionProducts()
            return false
        }

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "Offer token not available for product: $productId")
            return false
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        return billingResult.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restorePurchases(onResult: (Boolean, String) -> Unit) {
        if (!billingClient.isReady) {
            startBillingConnection()
            onResult(false, "Connecting to Google Play... Please try again in a moment.")
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchases = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (activePurchases.isNotEmpty()) {
                    for (purchase in activePurchases) {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                    val planId = activePurchases.first().products.firstOrNull() ?: ""
                    managerScope.launch {
                        prefRepo.setPremiumActive(true, planId)
                    }
                    onResult(true, "Successfully restored your SkipFlow Unlimited subscription!")
                } else {
                    onResult(false, "No active SkipFlow Unlimited subscription found on this Google account.")
                }
            } else {
                onResult(false, "Unable to restore purchases: ${billingResult.debugMessage}")
            }
        }
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
