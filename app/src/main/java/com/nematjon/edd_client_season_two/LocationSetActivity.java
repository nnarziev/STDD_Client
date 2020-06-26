package com.nematjon.edd_client_season_two;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Objects;


public class LocationSetActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener, GoogleMap.OnMapClickListener, GoogleMap.OnMarkerClickListener {

    //region Constants
    private static final String TAG = LocationSetActivity.class.getSimpleName();
    public static final String ID_HOME = "HOME";
    public static final String ID_DORM = "DORM";
    public static final String ID_UNIV = "UNIV";
    public static final String ID_LIBRARY = "LIBRARY";
    public static final String ID_ADDITIONAL = "ADDITIONAL";

    private String TITLE_HOME;
    private String TITLE_DORM;
    private String TITLE_UNIV;
    private String TITLE_LIBRARY;
    private String TITLE_ADDITIONAL;

    public static final int GEOFENCE_RADIUS_DEFAULT = 100;
    static final StoreLocation[] ALL_LOCATIONS = new StoreLocation[]
            {
                    new StoreLocation(ID_HOME, null),
                    new StoreLocation(ID_DORM, null),
                    new StoreLocation(ID_UNIV, null),
                    new StoreLocation(ID_LIBRARY, null),
                    new StoreLocation(ID_ADDITIONAL, null)
            };

    //endregion

    private GoogleMap mMap;
    private Marker currentGeofenceMarker;
    private StoreLocation currentStoringLocation;
    private Circle geoLimits;


    LinearLayout loadingLayout;

    Toolbar toolbar;

    //region Inner classes
    static class StoreLocation {
        LatLng mLatLng;
        String mId;
        int rad;

        StoreLocation(String id, LatLng latlng/*, int radius*/) {
            mLatLng = latlng;
            mId = id;
            //rad = radius;
        }

        LatLng getmLatLng() {
            return mLatLng;
        }

        String getmId() {
            return mId;
        }

        int getRadius() {
            return rad;
        }

        void setRadius(int radius) {
            rad = radius;
        }
    }
    //endregion


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locations_setting);
        toolbar = findViewById(R.id.my_toolbar);
        setActionBar(toolbar);
        toolbar.setNavigationIcon(getDrawable(R.drawable.back_press));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });


        loadingLayout = findViewById(R.id.loading_frame);
        loadingLayout.setVisibility(View.VISIBLE);
        loadingLayout.bringToFront();
        Tools.disable_touch(this);

        TITLE_HOME = getString(R.string.set_home_location);
        TITLE_DORM = getString(R.string.set_dorm_location);
        TITLE_UNIV = getString(R.string.set_university_location);
        TITLE_LIBRARY = getString(R.string.set_library_location);
        TITLE_ADDITIONAL = getString(R.string.set_additional_location);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null)
            mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.e(TAG, "onMapReady: ");

        mMap = googleMap;
        mMap.clear();
        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerClickListener(this);

        for (StoreLocation location : ALL_LOCATIONS) {
            if (getLocationData(getApplicationContext(), location) != null) {
                addLocationMarker(Objects.requireNonNull(getLocationData(getApplicationContext(), location)));
            }
        }

        try {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 20, this);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        markerForGeofence(latLng);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        removeLocation(marker.getTitle());
        drawGeofence(marker, Integer.parseInt(marker.getSnippet()));
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.e(TAG, "onLocationChanged: ");
        loadingLayout.setVisibility(View.GONE);
        Tools.enable_touch(this);

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        markerForGeofence(latLng);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void drawGeofence(Marker marker, int radius) {
        if (geoLimits != null) {
            geoLimits.remove();
        }

        CircleOptions circleOptions = new CircleOptions()
                .center(marker.getPosition())
                .strokeColor(Color.argb(50, 70, 70, 70))
                .strokeWidth(1)
                .fillColor(Color.argb(100, 150, 150, 150))
                .radius(radius);

        geoLimits = mMap.addCircle(circleOptions);
    }

    private void markerForGeofence(LatLng latLng) {
        MarkerOptions optionsMarker = new MarkerOptions()
                .snippet(String.valueOf(GEOFENCE_RADIUS_DEFAULT))
                .position(latLng)
                .title("Current Location");
        if (mMap != null) {
            if (currentGeofenceMarker != null) {
                currentGeofenceMarker.remove();
            }
            currentGeofenceMarker = mMap.addMarker(optionsMarker);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17));
            drawGeofence(currentGeofenceMarker, GEOFENCE_RADIUS_DEFAULT);
        }
    }

    private void addLocationMarker(StoreLocation location) {
        Drawable iconDrawable;
        String location_title;
        switch (location.getmId()) {
            case ID_HOME:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.home);
                location_title = TITLE_HOME;
                break;
            case ID_DORM:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.dormitory);
                location_title = TITLE_DORM;
                break;
            case ID_UNIV:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.university);
                location_title = TITLE_UNIV;
                break;
            case ID_LIBRARY:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.library);
                location_title = TITLE_LIBRARY;
                break;
            case ID_ADDITIONAL:
                iconDrawable = ContextCompat.getDrawable(this, R.drawable.additional);
                location_title = TITLE_ADDITIONAL;
                break;
            default:
                iconDrawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher_no_bg);
                location_title = "My location";
                break;
        }

        assert iconDrawable != null;
        Bitmap iconBmp = ((BitmapDrawable) iconDrawable).getBitmap();
        mMap.addMarker(new MarkerOptions()
                .title(location_title)
                .snippet(String.valueOf(GEOFENCE_RADIUS_DEFAULT))
                .position(location.getmLatLng())
                .icon(BitmapDescriptorFactory.fromBitmap(iconBmp)));
    }

    static StoreLocation getLocationData(Context context, StoreLocation location) {
        SharedPreferences locationPrefs = context.getSharedPreferences("UserLocations", MODE_PRIVATE);
        float lat = locationPrefs.getFloat(location.getmId() + "_LAT", 0);
        float lng = locationPrefs.getFloat(location.getmId() + "_LNG", 0);
        if (lat != 0 && lng != 0) {
            return new StoreLocation(location.getmId(), new LatLng(lat, lng));
        }
        return null;
    }

    private void setLocation(String locationText, StoreLocation location) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        SharedPreferences.Editor editor = locationPrefs.edit();
        editor.putFloat(location.getmId() + "_LAT", (float) location.getmLatLng().latitude);
        editor.putFloat(location.getmId() + "_LNG", (float) location.getmLatLng().longitude);
        editor.apply();
        Toast.makeText(getApplicationContext(), locationText + getString(R.string.location_set), Toast.LENGTH_SHORT).show();
        currentStoringLocation.setRadius(GEOFENCE_RADIUS_DEFAULT);

        String location_id = currentStoringLocation.getmId();
        LatLng position = currentStoringLocation.getmLatLng();
        int radius = currentStoringLocation.getRadius();

        GeofenceHelper.startGeofence(getApplicationContext(), location_id, position, radius);


        SharedPreferences confPrefs = getSharedPreferences("Configurations", Context.MODE_PRIVATE);
        int dataSourceId = confPrefs.getInt("LOCATIONS_MANUAL", -1);
        assert dataSourceId != -1;
        long nowTime = System.currentTimeMillis();
        DbMgr.saveMixedData(dataSourceId, nowTime, 1.0f, nowTime, location.getmId(), (float) location.getmLatLng().latitude, (float) location.getmLatLng().longitude);

        onMapReady(mMap);
        drawGeofence(currentGeofenceMarker, currentStoringLocation.getRadius());
    }

    private void removeLocation(String markerTitle) {
        if (markerTitle.equals(TITLE_HOME))
            displayRemoveDialog(ID_HOME);
        else if (markerTitle.equals(TITLE_DORM))
            displayRemoveDialog(ID_DORM);
        else if (markerTitle.equals(TITLE_UNIV))
            displayRemoveDialog(ID_UNIV);
        else if (markerTitle.equals(TITLE_LIBRARY))
            displayRemoveDialog(ID_LIBRARY);
        else if (markerTitle.equals(TITLE_ADDITIONAL))
            displayRemoveDialog(ID_ADDITIONAL);
    }

    public void displayRemoveDialog(final String locationId) {
        final SharedPreferences locationPrefs = getSharedPreferences("UserLocations", MODE_PRIVATE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_remove_confirmation));
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                GeofenceHelper.removeGeofence(getApplicationContext(), locationId);
                SharedPreferences.Editor editor = locationPrefs.edit();
                editor.remove(locationId + "_LAT");
                editor.remove(locationId + "_LNG");
                editor.apply();
                Toast.makeText(LocationSetActivity.this, getString(R.string.location_removed), Toast.LENGTH_SHORT).show();
                onMapReady(mMap);
                drawGeofence(currentGeofenceMarker, GEOFENCE_RADIUS_DEFAULT);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    //region Buttons click listeners
    public void setHomeClick(View view) {
        currentStoringLocation = new StoreLocation(ID_HOME, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
        displayDialog(TITLE_HOME, currentStoringLocation);
    }

    public void setDormClick(View view) {
        currentStoringLocation = new StoreLocation(ID_DORM, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
        displayDialog(TITLE_DORM, currentStoringLocation);
    }

    public void setUnivClick(View view) {
        currentStoringLocation = new StoreLocation(ID_UNIV, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
        displayDialog(TITLE_UNIV, currentStoringLocation);
    }

    public void setLibraryClick(View view) {
        currentStoringLocation = new StoreLocation(ID_LIBRARY, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
        displayDialog(TITLE_LIBRARY, currentStoringLocation);
    }

    public void setAdditionalPlaceClick(View view) {
        currentStoringLocation = new StoreLocation(ID_ADDITIONAL, new LatLng(currentGeofenceMarker.getPosition().latitude, currentGeofenceMarker.getPosition().longitude));
        displayDialog(TITLE_ADDITIONAL, currentStoringLocation);
    }
    //endregion

    public void displayDialog(final String locationText, final StoreLocation location) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getString(R.string.location_set_confirmation, locationText));
        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setLocation(locationText, location);
            }
        });

        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }
}