//
//  RewardedAds.kt
//  QueensGame (Android)
//
//  The app's only view of rewarded video ads — the Android twin of the iOS
//  `RewardedAdService`. Everything that touches the Google Mobile Ads SDK
//  lives in `AdMobRewardedAds`, so swapping the network (or stubbing it)
//  means providing another implementation.
//

package com.app.queensgame.ads

import android.app.Activity

sealed interface AdResult {
    /** Watched to the end: grant the reward. */
    data object Rewarded : AdResult
    /** Closed early: no reward. */
    data object Skipped : AdResult
    data class Failed(val message: String) : AdResult
}

interface RewardedAdService {
    /** Starts loading the next ad in the background so it's ready on tap. */
    fun preload(activity: Activity)

    /** Shows one rewarded ad over [activity]. */
    suspend fun show(activity: Activity): AdResult
}
