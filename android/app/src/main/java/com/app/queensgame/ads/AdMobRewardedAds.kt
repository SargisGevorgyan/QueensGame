//
//  AdMobRewardedAds.kt
//  QueensGame (Android)
//
//  Google AdMob rewarded ads behind [RewardedAdService]. The SDK starts
//  lazily, the first time a player asks for an ad, so nothing ad-related
//  runs at launch.
//
//  The unit id below is Google's public *test* id. Replace it (and the
//  `admobAppId` manifest placeholder in app/build.gradle.kts) with the real
//  ones from the AdMob console before shipping.
//

package com.app.queensgame.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AdMobRewardedAds : RewardedAdService {

    companion object {
        const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val UNAVAILABLE = "No video is available right now. Please try again later."
    }

    private var started = false
    private var loaded: RewardedAd? = null
    private var loading = false

    override fun preload(activity: Activity) {
        if (!started || loaded != null || loading) return
        loading = true
        RewardedAd.load(activity, AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                loading = false
                loaded = ad
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
            }
        })
    }

    override suspend fun show(activity: Activity): AdResult {
        startIfNeeded(activity)
        val ad = loaded ?: load(activity) ?: return AdResult.Failed(UNAVAILABLE)
        loaded = null

        val result = suspendCancellableCoroutine<AdResult> { cont ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(if (earned) AdResult.Rewarded else AdResult.Skipped)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(AdResult.Failed(UNAVAILABLE))
                }
            }
            ad.show(activity) { earned = true }
        }
        preload(activity)
        return result
    }

    private suspend fun startIfNeeded(activity: Activity) {
        if (started) return
        suspendCancellableCoroutine<Unit> { cont ->
            MobileAds.initialize(activity.applicationContext) { if (cont.isActive) cont.resume(Unit) }
        }
        started = true
    }

    private suspend fun load(activity: Activity): RewardedAd? = suspendCancellableCoroutine<RewardedAd?> { cont ->
        RewardedAd.load(activity, AD_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                if (cont.isActive) cont.resume(ad)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }
}
