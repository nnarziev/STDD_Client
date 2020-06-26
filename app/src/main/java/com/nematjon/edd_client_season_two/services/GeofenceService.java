package com.nematjon.edd_client_season_two.services;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.nematjon.edd_client_season_two.DbMgr;

import java.util.List;

public class GeofenceService extends IntentService {
    private static final String TAG = "Geofence";

    public GeofenceService() {
        super("GeofenceService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            String error = String.valueOf(geofencingEvent.getErrorCode());
            Log.e(TAG, "Error code: " + error);
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.e(TAG, "ENTERED");
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.e(TAG, "EXITTED");
        }
    }
}
