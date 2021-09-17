package com.google.android.gms.example.rewardedvideoexample

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.TimeUnit

class RewardedAdWrapper {
    private sealed interface LoadStatus {
        object None : LoadStatus

        object Loading : LoadStatus

        // https://developers.google.com/admob/android/rewarded#load_a_rewarded_ad_object
        data class Loaded(val rewardedAd: RewardedAd, val adValidTime: Long) : LoadStatus
    }

    private var loadStatus: LoadStatus = LoadStatus.None

    @MainThread
    fun canLoadAd(): Boolean {
        val status = loadStatus
        if (status == LoadStatus.None) {
            return true
        }
        if (status is LoadStatus.Loaded && status.adValidTime <= System.currentTimeMillis()) {
            Log.d(TAG, "rewardedAd is stale.")
            return true
        }

        return false
    }

    @MainThread
    fun loadAd(activity: Activity, adUnitId: String) {
        loadStatus = LoadStatus.Loading

        Toast.makeText(activity, "Start loading ad", Toast.LENGTH_SHORT).show()
        // https://developers.google.com/admob/android/rewarded#load_a_rewarded_ad_object
        RewardedAd.load(
            activity, adUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Log.d(TAG, "onAdLoaded(): ${rewardedAd.responseInfo.mediationAdapterClassName}")
                    Toast.makeText(activity, "onAdLoaded(): ${rewardedAd.responseInfo.mediationAdapterClassName}", Toast.LENGTH_SHORT).show()
                    loadStatus = LoadStatus.Loaded(rewardedAd, System.currentTimeMillis() + AD_VALID_MILLIS)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "onAdFailedToLoad(): $error")
                    Toast.makeText(activity, "onAdFailedToLoad(): $error", Toast.LENGTH_SHORT).show()
                    loadStatus = LoadStatus.None
                }
            }
        )
    }

    @MainThread
    fun canShowAd(): Boolean {
        val adValidTime = (loadStatus as? LoadStatus.Loaded)?.adValidTime ?: return false
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "adValidTime: $adValidTime, currentTime: $currentTime")
        return adValidTime < System.currentTimeMillis()
    }

    @MainThread
    fun showAd(activity: Activity, onEvent: (RewardedAdEvent) -> Unit) {
        val rewardedAd = (loadStatus as? LoadStatus.Loaded)?.rewardedAd ?: return

        // https://developers.google.com/admob/android/rewarded#set_the_fullscreencontentcallback
        rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadStatus = LoadStatus.None
                onEvent(RewardedAdEvent.Failed(error))
            }

            override fun onAdShowedFullScreenContent() {
                onEvent(RewardedAdEvent.Opened)
            }

            override fun onAdDismissedFullScreenContent() {
                loadStatus = LoadStatus.None
                onEvent(RewardedAdEvent.Closed)
            }
        }

        // https://developers.google.com/admob/android/rewarded#show_the_ad
        rewardedAd.show(activity) {
            onEvent(RewardedAdEvent.EarnedReward(it))
        }
    }

    companion object {
        private const val TAG = "RewardedAdWrapper"
        private val AD_VALID_MILLIS = TimeUnit.HOURS.toMillis(1)
    }
}

sealed interface RewardedAdEvent {
    data class EarnedReward(val rewardItem: RewardItem) : RewardedAdEvent
    object Opened : RewardedAdEvent
    object Closed : RewardedAdEvent
    data class Failed(val error: AdError) : RewardedAdEvent
}
