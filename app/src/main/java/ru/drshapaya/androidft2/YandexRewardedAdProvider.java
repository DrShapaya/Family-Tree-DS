package ru.drshapaya.androidft2;

import android.app.Activity;

import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoader;

final class YandexRewardedAdProvider {
    interface Listener {
        void onRewardedAdReadyChanged();
        void onRewardedAdEarned();
        void onRewardedAdMessage(String message);
    }

    private final Activity activity;
    private final String adUnitId;
    private final Listener listener;
    private RewardedAdLoader loader;
    private RewardedAd rewardedAd;
    private boolean loading;
    private boolean disposed;
    private boolean rewardDelivered;

    YandexRewardedAdProvider(Activity activity, String adUnitId, Listener listener) {
        this.activity = activity;
        this.adUnitId = adUnitId == null || adUnitId.trim().isEmpty()
            ? "demo-rewarded-yandex"
            : adUnitId.trim();
        this.listener = listener;
        loader = new RewardedAdLoader(activity);
    }

    boolean isReady() {
        return rewardedAd != null;
    }

    boolean isLoading() {
        return loading;
    }

    void load() {
        if (disposed || loading || rewardedAd != null || loader == null) return;
        loading = true;
        notifyReadyChanged();
        loader.loadAd(new AdRequest.Builder(adUnitId).build(), new RewardedAdLoadListener() {
            @Override public void onAdLoaded(RewardedAd ad) {
                if (disposed) return;
                loading = false;
                rewardedAd = ad;
                AnalyticsReporter.event("rewarded_ad_loaded");
                notifyReadyChanged();
            }

            @Override public void onAdFailedToLoad(AdRequestError error) {
                if (disposed) return;
                loading = false;
                rewardedAd = null;
                AnalyticsReporter.event("rewarded_ad_load_failed");
                notifyMessage("Сейчас рекламы нет. Попробуйте позже.");
                notifyReadyChanged();
            }
        });
    }

    void show() {
        if (disposed) return;
        if (rewardedAd == null) {
            load();
            notifyMessage("Реклама загружается");
            return;
        }
        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        rewardDelivered = false;
        notifyReadyChanged();
        ad.setAdEventListener(new RewardedAdEventListener() {
            @Override public void onAdShown() {
                AnalyticsReporter.event("rewarded_ad_shown");
            }

            @Override public void onAdFailedToShow(AdError error) {
                AnalyticsReporter.event("rewarded_ad_show_failed");
                notifyMessage("Не удалось показать рекламу");
                load();
            }

            @Override public void onAdDismissed() {
                AnalyticsReporter.event("rewarded_ad_dismissed");
                load();
            }

            @Override public void onAdClicked() {
                AnalyticsReporter.event("rewarded_ad_clicked");
            }

            @Override public void onAdImpression(ImpressionData impressionData) {
                AnalyticsReporter.event("rewarded_ad_impression");
            }

            @Override public void onRewarded(Reward reward) {
                if (rewardDelivered) return;
                rewardDelivered = true;
                if (listener != null) listener.onRewardedAdEarned();
            }
        });
        ad.show(activity);
    }

    void close() {
        disposed = true;
        rewardedAd = null;
        if (loader != null) loader.cancelLoading();
        loader = null;
    }

    private void notifyReadyChanged() {
        if (listener != null) listener.onRewardedAdReadyChanged();
    }

    private void notifyMessage(String message) {
        if (listener != null) listener.onRewardedAdMessage(message);
    }
}
