package com.nematjon.edd_client_season_two.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.nematjon.edd_client_season_two.DbMgr;

import java.util.List;

public class GeofenceRcvr extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences confPrefs = context.getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        PendingResult pendingIntent = goAsync();
        Task asyncTask = new Task(pendingIntent, intent, confPrefs);
        asyncTask.execute();
    }

    private static class Task extends AsyncTask<String, Integer, String> {

        private final PendingResult pendingResult;
        private final Intent intent;
        private final SharedPreferences confPrefs;
        boolean entered = false;

        private Task(PendingResult pendingResult, Intent intent, SharedPreferences confPrefs) {
            this.pendingResult = pendingResult;
            this.intent = intent;
            this.confPrefs = confPrefs;
        }

        @Override
        protected String doInBackground(String... string) {
            GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
            if (geofencingEvent.hasError()) {
                String error = String.valueOf(geofencingEvent.getErrorCode());
                Log.e(TAG, "Error code: " + error);
                return "Error: " + error;
            }

            // Get the transition type.
            int geofenceTransition = geofencingEvent.getGeofenceTransition();
            List<Geofence> triggeringGeofences = geofencingEvent.getTriggeringGeofences();

            int dataSourceId = confPrefs.getInt("GEOFENCE", -1);
            assert dataSourceId != -1;
            if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                entered = true;
                long nowTime = System.currentTimeMillis();
                for (Geofence geofence : triggeringGeofences)
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, geofence.getRequestId(), "ENTER");
            } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                long nowTime = System.currentTimeMillis();
                for (Geofence geofence : triggeringGeofences) {
                    DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, geofence.getRequestId(), "EXIT");
                }
            }
            return "Success";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            // Must call finish() so the BroadcastReceiver can be recycled.
            Log.e(TAG, "Geofence result: " + s);
            // sendNotification(s, entered);
            pendingResult.finish();
        }
    }
}