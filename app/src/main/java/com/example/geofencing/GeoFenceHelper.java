package com.example.geofencing;

import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.maps.model.LatLng;

// Class defines various methods to manipulate geofences
public class GeoFenceHelper extends ContextWrapper {
    private static final String TAG = "GeoFenceHelper";
    PendingIntent pendingIntent;
    public GeoFenceHelper(Context base) {
        super(base);
    }
    
    public GeofencingRequest getGeoFencingRequest(Geofence geofence){
        return new GeofencingRequest.Builder()
                .addGeofence(geofence)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .build();
    }

    // Creates a geofence according to parameters
    public Geofence getGeofence(String ID, LatLng latLng, float radius, int transitionTypes){
        return new Geofence.Builder()
                .setCircularRegion(latLng.latitude, latLng.longitude, radius)
                .setRequestId(ID)
                .setTransitionTypes(transitionTypes)
                .setLoiteringDelay(5000) /* after how many seconds you want to be notified
                you are within a geofence (after entering)
                only effective with transitionTypes*/
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build();

    }

    public PendingIntent getPendingIntent(){
        if(pendingIntent != null)
            return pendingIntent;

        Intent intent = new Intent(this, GeoFenceBroadcastReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(this, 2607, intent, pendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    // Returns the string defining the error if one arises while retrieving a geofence
    public String getErrorString(Exception e){
        if(e instanceof ApiException){
            ApiException apiException = (ApiException) e;
            switch (apiException.getStatusCode()){
                case GeofenceStatusCodes
                        .GEOFENCE_NOT_AVAILABLE:
                    return "GEOFENCE_NOT_AVAILABLE";
                case GeofenceStatusCodes
                        .GEOFENCE_TOO_MANY_GEOFENCES:
                    return "GEOFENCE_TOO_MANY_GEOFENCES";
                case GeofenceStatusCodes
                        .GEOFENCE_TOO_MANY_PENDING_INTENTS:
                    return "GEOFENCE_TOO_MANY_PENDING_INTENTS";

            }
        }
        return e.getLocalizedMessage();
    }

}
