package org.c99.dashclockfit;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Goal;
import com.google.android.gms.fitness.request.GoalsReadRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.GoalsResult;

import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;

/**
 * Created by sam on 11/20/16.
 */

public class FitExtension extends DashClockExtension implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "FitExtension";
    private GoogleApiClient mClient = null;

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);

        buildGoogleClient();

        setUpdateWhenScreenOn(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mClient != null && mClient.isConnected()) {
            mClient.disconnect();
        }

        mClient = null;
    }

    private void buildGoogleClient() {
        if (mClient == null) {
            mClient = new GoogleApiClient.Builder(this)
                    .addApi(Fitness.HISTORY_API)
                    .addApi(Fitness.GOALS_API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        onUpdateData(0);
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
            Log.i(TAG, "Connection lost.  Cause: Network Lost.");
        } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
            Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
        } else {
            Log.i(TAG, "Connection lost.");
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Connection failed: " + connectionResult.toString());
        if(connectionResult.getErrorCode() == ConnectionResult.SIGN_IN_REQUIRED) {
            Intent i = new Intent(this, SettingsActivity.class);
            i.putExtra("doLogin", true);
            publishUpdate(new ExtensionData()
                    .visible(true)
                    .icon(R.drawable.ic_dashclock)
                    .status("?")
                    .expandedTitle("Not logged in")
                    .expandedBody("Tap to login to Google")
                    .clickIntent(i)
            );
        }
    }

    @Override
    protected void onUpdateData(int reason) {
        if(mClient == null) {
            buildGoogleClient();
        } else if(mClient.isConnected()) {
            PendingResult<DailyTotalResult> result = Fitness.HistoryApi.readDailyTotal(mClient, DataType.TYPE_STEP_COUNT_DELTA);
            result.setResultCallback(new ResultCallback<DailyTotalResult>() {
                @Override
                public void onResult(@NonNull DailyTotalResult totalResult) {
                    final long total;
                    if (totalResult.getStatus().isSuccess()) {
                        DataSet totalSet = totalResult.getTotal();
                        total = (totalSet == null || totalSet.isEmpty())
                                ? 0
                                : totalSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
                    } else {
                        total = 0;
                        Log.w(TAG, "There was a problem getting the step count: " + totalResult.getStatus());
                    }

                    PendingResult<GoalsResult> pendingResult =
                            Fitness.GoalsApi.readCurrentGoals(mClient,
                                    new GoalsReadRequest.Builder()
                                            .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                                            .build());

                    pendingResult.setResultCallback(new ResultCallback<GoalsResult>() {
                        @Override
                        public void onResult(@NonNull GoalsResult goalsResult) {
                            String body = null;

                            if(goalsResult.getStatus().isSuccess() && goalsResult.getGoals().size() > 0) {
                                double goal = goalsResult.getGoals().get(0).getMetricObjective().getValue();
                                body = (int) (((float) total / (float) goal) * 100.0) + "% of your " + NumberFormat.getInstance().format(goal) + " step goal";
                            }

                            publishUpdate(new ExtensionData()
                                    .visible(true)
                                    .icon(R.drawable.ic_dashclock)
                                    .status(NumberFormat.getInstance().format(total))
                                    .expandedTitle(NumberFormat.getInstance().format(total) + " Steps Today")
                                    .expandedBody(body)
                                    .clickIntent(getPackageManager().getLaunchIntentForPackage("com.google.android.apps.fitness"))
                            );
                        }
                    });
                }
            });
        } else {
            Log.d(TAG, "API client not connected");
            mClient.connect();
        }
    }
}
