package com.nematjon.edd_client_season_two;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;


import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.nematjon.edd_client_season_two.receivers.GeofenceRcvr;
import com.nematjon.edd_client_season_two.services.MainService;
import com.nematjon.edd_client_season_two.services.KeyLogger;
import com.nematjon.edd_client_season_two.services.NotificationService;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import inha.nsl.easytrack.ETServiceGrpc;
import inha.nsl.easytrack.EtService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import static android.content.Context.MODE_PRIVATE;
import static com.nematjon.edd_client_season_two.EMAActivity.EMA_NOTIF_HOURS;
import static com.nematjon.edd_client_season_two.services.MainService.EMA_RESPONSE_EXPIRE_TIME;

public class Tools {

    static final String DATA_SOURCE_SEPARATOR = " ";
    static int PERMISSION_ALL = 1;
    public static String[] PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS
    };

    public static boolean hasPermissions(Context con, String... permissions) {
        Context context = con.getApplicationContext();
        if (context != null && permissions != null)
            for (String permission : permissions)
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                    return false;

        assert context != null;
        if (isAppUsageAccessNotGranted(context))
            return false;

        if (isAccessibilityServiceNotEnabled(context))
            return false;

        if (isNotificationServiceNotEnabled(context))
            return false;

        return isGPSLocationOn(context);
    }

    private static boolean isGPSLocationOn(Context con) {
        LocationManager lm = (LocationManager) con.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled;
        boolean network_enabled;
        gps_enabled = Objects.requireNonNull(lm).isProviderEnabled(LocationManager.GPS_PROVIDER);
        network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        return gps_enabled || network_enabled;
    }

    private static boolean isAppUsageAccessNotGranted(Context con) {
        try {
            PackageManager packageManager = con.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(con.getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) con.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Objects.requireNonNull(appOpsManager).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, applicationInfo.uid, applicationInfo.packageName);
            return (mode != AppOpsManager.MODE_ALLOWED);

        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private static boolean isAccessibilityServiceNotEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices;
        if (am != null) {
            enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo enabledService : enabledServices) {
                ServiceInfo enabledServiceInfo = enabledService.getResolveInfo().serviceInfo;
                if (enabledServiceInfo.packageName.equals(context.getPackageName()) && enabledServiceInfo.name.equals(KeyLogger.class.getName()))
                    return false;
            }
        }
        return true;
    }

    private static boolean isNotificationServiceNotEnabled(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (NotificationService.class.getName().equals(service.service.getClassName())) {
                    return false;
                }
            }
        }
        return true;
    }

    static AlertDialog requestPermissions(final Activity activity) {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.permissions))
                .setMessage(activity.getString(R.string.grant_permissions))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Tools.grantPermissions(activity, PERMISSIONS);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        return alertDialog.show();
    }

    private static void grantPermissions(Activity activity, String... permissions) {
        boolean simple_permissions_granted = true;
        for (String permission : permissions)
            if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                simple_permissions_granted = false;
                break;
            }

        if (isAppUsageAccessNotGranted(activity.getApplicationContext()))
            activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (!isGPSLocationOn(activity.getApplicationContext()))
            activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (isAccessibilityServiceNotEnabled(activity.getApplicationContext()))
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (isNotificationServiceNotEnabled(activity.getApplicationContext()))
            activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        if (!simple_permissions_granted)
            ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_ALL);
    }

    public static void checkAndSaveUsageAccessStats(Context con) {
        SharedPreferences loginPrefs = con.getSharedPreferences("UserLogin", MODE_PRIVATE);
        long lastSavedTimestamp = loginPrefs.getLong("lastUsageSubmissionTime", -1);

        Calendar fromCal = Calendar.getInstance();
        if (lastSavedTimestamp == -1)
            fromCal.add(Calendar.DAY_OF_WEEK, -2);
        else
            fromCal.setTime(new Date(lastSavedTimestamp));

        final Calendar tillCal = Calendar.getInstance();
        tillCal.set(Calendar.MILLISECOND, 0);

        PackageManager localPackageManager = con.getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        String launcher_packageName = Objects.requireNonNull(localPackageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)).activityInfo.packageName;

        UsageStatsManager usageStatsManager = (UsageStatsManager) con.getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> stats = Objects.requireNonNull(usageStatsManager).queryUsageStats(UsageStatsManager.INTERVAL_BEST, fromCal.getTimeInMillis(), System.currentTimeMillis());
        for (UsageStats usageStats : stats) {
            //do not include launcher's package name
            if (usageStats.getTotalTimeInForeground() > 0 && !usageStats.getPackageName().contains(launcher_packageName)) {
                AppUseDb.saveAppUsageStat(usageStats.getPackageName(), usageStats.getLastTimeUsed(), usageStats.getTotalTimeInForeground());
            }
        }
        SharedPreferences.Editor editor = loginPrefs.edit();
        editor.putLong("lastUsageSubmissionTime", tillCal.getTimeInMillis());
        editor.apply();
    }

    public static void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void enable_touch(Activity activity) {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    static void disable_touch(Activity activity) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private static boolean isReachable;

    public static boolean isNetworkAvailable() {
        try {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        InetAddress address = InetAddress.getByName("www.google.com");
                        isReachable = !address.toString().equals("");
                    } catch (Exception e) {
                        e.printStackTrace();
                        isReachable = false;
                    }
                }
            });
            thread.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
            isReachable = false;
        }

        return isReachable;
    }

    static boolean isMainServiceRunning(Context con) {
        ActivityManager manager = (ActivityManager) con.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : Objects.requireNonNull(manager).getRunningServices(Integer.MAX_VALUE)) {
            if (MainService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void sendHeartbeat(final Context con) {
        final SharedPreferences loginPrefs = con.getSharedPreferences("UserLogin", MODE_PRIVATE);

        if (Tools.isNetworkAvailable()) {
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(
                            con.getString(R.string.grpc_host),
                            Integer.parseInt(con.getString(R.string.grpc_port))
                    ).usePlaintext().build();
                    ETServiceGrpc.ETServiceBlockingStub stub = ETServiceGrpc.newBlockingStub(channel);
                    EtService.SubmitHeartbeatRequestMessage submitHeartbeatRequestMessage = EtService.SubmitHeartbeatRequestMessage.newBuilder()
                            .setUserId(loginPrefs.getInt(AuthActivity.user_id, -1))
                            .setEmail(loginPrefs.getString(AuthActivity.usrEmail, null))
                            .build();
                    try {
                        //@SuppressWarnings("unused")
                        EtService.DefaultResponseMessage responseMessage = stub.submitHeartbeat(submitHeartbeatRequestMessage);
                        if (responseMessage.getDoneSuccessfully()) {
                            Log.d("Tools", "Heartbeat sent successfully");
                        }
                    } catch (StatusRuntimeException e) {
                        Log.e("Tools", "DataCollectorService.setUpHeartbeatSubmissionThread() exception: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        channel.shutdown();
                    }
                }
            }.start();
        }
    }

    static int getEMAOrderFromRangeAfterEMA(Calendar cal) {
        long t = (cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)) * 1000;
        for (int i = 0; i < EMA_NOTIF_HOURS.length; i++)
            if ((EMA_NOTIF_HOURS[i] * 3600 * 1000) <= t && t <= (EMA_NOTIF_HOURS[i] * 3600 * 1000) + EMA_RESPONSE_EXPIRE_TIME * 1000)
                return i + 1;

        return 0;
    }

    static void perform_logout(Context con) {

        SharedPreferences loginPrefs = con.getSharedPreferences("UserLogin", MODE_PRIVATE);
        SharedPreferences locationPrefs = con.getSharedPreferences("UserLocations", MODE_PRIVATE);

        SharedPreferences.Editor editorLocation = locationPrefs.edit();
        editorLocation.clear();
        editorLocation.apply();

        SharedPreferences.Editor editorLogin = loginPrefs.edit();
        editorLogin.remove("username");
        editorLogin.remove("password");
        editorLogin.putBoolean("logged_in", false);
        editorLogin.remove("ema_btn_make_visible");
        editorLogin.clear();
        editorLogin.apply();

        GeofenceHelper.removeAllGeofences(con);
    }

    static String formatMinutes(int minutes) {
        if (minutes > 60) {
            if (minutes > 1440)
                return minutes / 60 / 24 + "days";
            else {
                int h = minutes / 60;
                float dif = (float) minutes / 60 - h;
                //Toast.makeText(MainActivity.this, dif + "", Toast.LENGTH_SHORT).show();
                int m = (int) (dif * 60);
                return h + "h " + m + "m";
            }
        } else
            return minutes + "m";
    }

    public static boolean inRange(long value, long start, long end) {
        return start < value && value < end;
    }
}

class GeofenceHelper {
    private static GeofencingClient geofencingClient;
    private static PendingIntent geofencePendingIntent;
    private static final String TAG = GeofenceHelper.class.getSimpleName();

    static void startGeofence(Context context, String location_id, LatLng position, int radius) {
        if (geofencingClient == null)
            geofencingClient = LocationServices.getGeofencingClient(context);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;


        GeofencingRequest geofencingRequest = getGeofenceRequest(createGeofence(location_id, position, radius));
        Log.e(TAG, "Setting location with ID: " + geofencingRequest.getGeofences().get(0).getRequestId());
        geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent(context))
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Geofences added
                        Log.e(TAG, "Geofence added");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Failed to add geofences
                        Log.e(TAG, "Geofence add failed: " + e.toString());
                    }
                });

    }

    private static GeofencingRequest getGeofenceRequest(Geofence geofence) {
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build();
    }

    private static Geofence createGeofence(String location_id, LatLng position, int radius) {
        return new Geofence.Builder()
                .setRequestId(location_id)
                .setCircularRegion(position.latitude, position.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)  // geofence should never expire
                .setNotificationResponsiveness(60 * 1000)          //notifying after 60sec. Can save power
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build();
    }

    private static PendingIntent getGeofencePendingIntent(Context context) {
        // Reuse the PendingIntent if we already have it.
        if (geofencePendingIntent != null)
            return geofencePendingIntent;

        Intent intent = new Intent(context, GeofenceRcvr.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return geofencePendingIntent;
    }

    static void removeGeofence(Context context, String reqID) {
        if (geofencingClient == null)
            geofencingClient = LocationServices.getGeofencingClient(context);

        ArrayList<String> reqIDs = new ArrayList<>();
        reqIDs.add(reqID);
        geofencingClient.removeGeofences(reqIDs)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Geofences removed
                        Log.e(TAG, "Geofence removed");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Failed to remove geofences
                        Log.e(TAG, "Geofence not removed: " + e.toString());
                    }
                });
    }

    static void removeAllGeofences(Context context) {
        if (geofencingClient == null)
            geofencingClient = LocationServices.getGeofencingClient(context);
        geofencingClient.removeGeofences(getGeofencePendingIntent(context));
    }
}
