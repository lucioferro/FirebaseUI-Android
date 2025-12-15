package com.firebase.ui.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.firebase.ui.auth.R;
import com.firebase.ui.auth.data.model.FlowParameters;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Base classes for activities that are just simple overlays.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InvisibleActivityBase extends HelperActivityBase {

    private static final String TAG = "FUI-InvisibleActivityBase";

    // Minimum time that the spinner will stay on screen, once it is shown.
    private static final long MIN_SPINNER_MS = 750;

    private Handler mHandler = new Handler();
    private CircularProgressIndicator mProgressBar;

    // Last time that the progress bar was actually shown
    private long mLastShownTime = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fui_activity_invisible);

        FlowParameters flowParams = getFlowParams();
        if (flowParams == null) {
            reportMissingFlowParams();
            finish(RESULT_CANCELED, null);
            return;
        }

        // Create an indeterminate, circular progress bar in the app's theme
        mProgressBar = new CircularProgressIndicator(new ContextThemeWrapper(this, flowParams.themeId));
        mProgressBar.setIndeterminate(true);
        mProgressBar.setVisibility(View.GONE);

        // Set bar to float in the center
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;

        // Add to the container
        FrameLayout container = findViewById(R.id.invisible_frame);
        container.addView(mProgressBar, layoutParams);
    }

    private void reportMissingFlowParams() {
        try {
            Intent intent = getIntent();
            String action = intent != null ? intent.getAction() : null;
            String data = null;
            if (intent != null && intent.getData() != null) {
                data = intent.getData().getScheme() + "://" + intent.getData().getHost() + intent.getData().getPath();
            }

            Log.e(TAG, "Missing FlowParameters. activity=" + getClass().getName()
                    + " action=" + action + " data=" + data);

            try {
                Class<?> crashlyticsClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics");
                Object crashlytics = crashlyticsClass.getMethod("getInstance").invoke(null);
                crashlyticsClass.getMethod("setCustomKey", String.class, String.class)
                        .invoke(crashlytics, "firebaseui_flowparams_missing", "true");
                crashlyticsClass.getMethod("setCustomKey", String.class, String.class)
                        .invoke(crashlytics, "firebaseui_activity", getClass().getName());
                crashlyticsClass.getMethod("setCustomKey", String.class, String.class)
                        .invoke(crashlytics, "firebaseui_intent_action", action);
                crashlyticsClass.getMethod("setCustomKey", String.class, String.class)
                        .invoke(crashlytics, "firebaseui_intent_data", data);
                crashlyticsClass.getMethod("log", String.class)
                        .invoke(crashlytics, "FirebaseUI missing FlowParameters; cancelling flow");
                crashlyticsClass.getMethod("recordException", Throwable.class)
                        .invoke(crashlytics,
                                new IllegalStateException("FirebaseUI missing FlowParameters in " + getClass().getName()));
            } catch (Throwable tr) {
                Log.e(TAG, "Crashlytics not available or failed to record non-fatal", tr);
            }
        } catch (Throwable tr) {
            Log.e(TAG, "Failed to report missing FlowParameters", tr);
        }
    }

    @Override
    public void showProgress(int message) {
        if (mProgressBar.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacksAndMessages(null);
            return;
        }

        mLastShownTime = System.currentTimeMillis();
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {
        doAfterTimeout(() -> {
            mLastShownTime = 0;
            mProgressBar.setVisibility(View.GONE);
        });
    }

    @Override
    public void finish(int resultCode, @Nullable Intent intent) {
        setResult(resultCode, intent);
        doAfterTimeout(() -> finish());
    }

    /**
     * For certain actions (like finishing or hiding the progress dialog) we want to make sure
     * that we have shown the progress state for at least MIN_SPINNER_MS to prevent flickering.
     *
     * This method performs some action after the window has passed, or immediately if we have
     * already waited longer than that.
     */
    private void doAfterTimeout(Runnable runnable) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - mLastShownTime;

        // 'diff' is how long it's been since we showed the spinner, so in the
        // case where diff is greater than our minimum spinner duration then our
        // remaining wait time is 0.
        long remaining = Math.max(MIN_SPINNER_MS - diff, 0);

        mHandler.postDelayed(runnable, remaining);
    }
}
