package com.example.geofencing;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.geofencing.databinding.ActivityMapsBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.ar.core.Anchor;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.StorageReference;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

// Main thread which runs the application
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener, LocationListener {

    private static final String TAG = "MapsActivity";

    private int FINE_LOCATION_ACCESS_REQUEST_CODE = 100001;
    private int BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 100002;
    public static ArFragment arFragment;
    public static HashMap<String, ModelRenderable> modelMap = new HashMap<>();

    private LocationManager locationManager;
    public Location userLoc;
    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    private GeofencingClient geofencingClient;
    private GeoFenceHelper geoFenceHelper;

    ArrayList<String> modelNames = new ArrayList<>();
    ArrayList<Float> latitude = new ArrayList<>();
    ArrayList<Float> longitude = new ArrayList<>();
    ArrayList<Float> radius = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        geofencingClient = LocationServices.getGeofencingClient(this);
        geoFenceHelper = new GeoFenceHelper(this);

        // Initialising a connection with Firebase storage
        FirebaseApp.initializeApp(this);
        FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);

        // When clicking the download button
        findViewById(R.id.downloadBtn)
                .setOnClickListener(v -> {
                    // retrieving JSON file
                    StorageReference jsonStorageReference = firebaseStorage.getReference().child("geofences.json");

                    // Ensures local variables are clear if download button is clicked more than once
                    modelNames = new ArrayList<>();
                    latitude = new ArrayList<>();
                    longitude = new ArrayList<>();
                    radius = new ArrayList<>();
                    mMap.clear();

                    try {
                        // Retrieving JSON file
                        File jsonFile = File.createTempFile("geofences", "json");

                        jsonStorageReference.getFile(jsonFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                try {
                                    JSONObject jsonObject = new JSONObject(getJsonData(jsonFile));
                                    JSONArray jsonArray = jsonObject.getJSONArray("geofence_list");

                                    // Iterates through each object in geofence_list, extracting their properties
                                    for (int i = 0; i < jsonArray.length(); ++i) {
                                        JSONObject singleItemData = jsonArray.getJSONObject(i);
                                        String model_name = singleItemData.getString("model_name");
                                        modelNames.add(model_name); // may delete this
                                        latitude.add(((float) singleItemData.getDouble("lat")));
                                        longitude.add(((float) singleItemData.getDouble("long")));
                                        radius.add(((float) singleItemData.getDouble("radius")));

                                        // Accessing relevant 3D model
                                        StorageReference modelStorageReference = firebaseStorage.getReference().child("models/" + model_name + ".glb");
                                        File modelFile = File.createTempFile(model_name, "glb");
                                        modelStorageReference.getFile(modelFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                                            @Override
                                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                                // Convert retrieved model into a renderable object using sceneform
                                                buildModel(model_name, modelFile);
                                            }
                                        }).addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(MapsActivity.this, "Failed to load the model " + model_name + ".glb", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                } catch (JSONException | IOException e) {
                                    Toast.makeText(MapsActivity.this, "Error occurred while downloading files", Toast.LENGTH_SHORT).show();
                                    e.printStackTrace();
                                }

                                // Creates geofences on the map according to the properties retrieved from the JSON file
                                addFixedGeofences();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to retrieve files", Toast.LENGTH_SHORT).show();
                    }

                });
    }

    // Retrieves JSON file content as a string
    private String getJsonData(File file) {
        String json = null;
        try {
            InputStream inputStream = new FileInputStream(file);
            int sizeOfFile = inputStream.available();
            byte[] bufferData = new byte[sizeOfFile]; // defines the buffer
            inputStream.read(bufferData);
            inputStream.close();
            json = new String(bufferData, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        Toast.makeText(this, "Successfully downloaded JSON file.", Toast.LENGTH_SHORT).show();
        return json;
    }

    // Converts the passed file to a renderable model using sceneform
    // and adds it to the modelMap hashmap with the passed key
    private void buildModel(String key,File file) {
        // Converts GLB file to a renderable source
        RenderableSource renderableSource = RenderableSource
                .builder()
                .setSource(this, Uri.parse(file.getPath()), RenderableSource.SourceType.GLB)
                .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                .build();

        // Converting the renderable source to a renderable model and is passed to modelMap
        // modelMap is used to load relevant renderable models for each geofence
        ModelRenderable
                .builder()
                .setSource(this, renderableSource)
                .setRegistryId(file.getPath())
                .build()
                .thenAccept(modelRenderable -> {
                    Log.d("Model Built", file.getPath());
                    MapsActivity.modelMap.put(key, modelRenderable);
                });

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Defines the starting location displayed to the user when the map is loaded
        enableUserLocation();
        setUserLoc();
        LatLng userLatLng = new LatLng(userLoc.getLatitude(), userLoc.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 16)); // zooms into user location
        //mMap.setOnMapLongClickListener(this); // enables adding geofences by long clicking map
    }

    private void enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            // ask for permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                // show dialog why permission is needed and then ask for it
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @org.jetbrains.annotations.NotNull String[] permissions, @NonNull @org.jetbrains.annotations.NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FINE_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //We have permission
                mMap.setMyLocationEnabled(true);
            } else {
                // We don't have permission
                Toast.makeText(MapsActivity.this, "No fine location permisson",Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == BACKGROUND_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //We have permission
                Toast.makeText(this, "You can add geofences", Toast.LENGTH_SHORT).show();
            } else {
                // We don't have permission
                Toast.makeText(this,
                        "Background location access is necessary to trigger geofences",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Adds a geofence to the scene when long clicking on the map
    // The functionality of the method was created for testing purposes
    // It is commented out as it is not part of the main application
    @Override
    public void onMapLongClick(@NonNull @NotNull LatLng latLng) {
        /*
        if (Build.VERSION.SDK_INT >= 29) {
            // We need background permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                tryAddingGeofence("maze",latLng, 200f);
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    //show dialog and ask for permission
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                            BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                }
            }
        } else {
            tryAddingGeofence("maze",latLng, 200f);
        }

         */
    }

    // Creates and adds geofences to the map according to the information
    // retrieved from the JSON file currently stored in the ArrayLists
    private void addFixedGeofences(){
        for(int i=0; i<modelNames.size(); ++i){
            LatLng latLng = new LatLng(latitude.get(i), longitude.get(i));
            tryAddingGeofence(modelNames.get(i), latLng, radius.get(i));
        }
    }

    // Adds geofence to map so that it is displayed
    private void tryAddingGeofence(String geoId, LatLng latLng, float radius) {
        addMarker(latLng);
        addCircle(latLng, radius);
        addGeoFence(geoId, latLng, radius);
    }

    // Creates a geofence to be added to the map
    private void addGeoFence(String geoId, LatLng latLng, float radius) {
        Geofence geofence = geoFenceHelper.getGeofence(geoId, latLng, radius,
                Geofence.GEOFENCE_TRANSITION_ENTER |
                        Geofence.GEOFENCE_TRANSITION_DWELL |
                        Geofence.GEOFENCE_TRANSITION_EXIT);
        PendingIntent pendingIntent = geoFenceHelper.getPendingIntent();
        GeofencingRequest geofencingRequest = geoFenceHelper.getGeoFencingRequest(geofence);
        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "onSuccess: Geofence Added");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull @NotNull Exception e) {
                        String errorMessage = geoFenceHelper.getErrorString(e);
                        Log.d(TAG, "onFailure: " + errorMessage);
                    }
                });
    }

    // Visual elements of the geofence which the user can see
    private void addMarker(LatLng latLng) {
        MarkerOptions markerOptions = new MarkerOptions().position(latLng);
        mMap.addMarker(markerOptions);
    }
    private void addCircle(LatLng latLng, float radius) {
        // Circle defines the area of the geofence
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.radius(radius);
        circleOptions.center(latLng);
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0));
        circleOptions.fillColor(Color.argb(64, 255, 0, 0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
    }

    public void setUserLoc() {
        // Fetching user location
        locationManager = (LocationManager) getSystemService(Service.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        // Gets user location every second
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 60, 10, this);
        if(locationManager!=null){
            userLoc=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
    }
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }
    @Override
    public void onProviderEnabled(String s) {

    }
    @Override
    public void onProviderDisabled(String s) {

    }
}