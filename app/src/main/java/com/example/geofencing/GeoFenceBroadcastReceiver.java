package com.example.geofencing;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Sun;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.util.ArrayList;
import java.util.List;

// this method may be killed in if it is running in the background to conserve energy
public class GeoFenceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "GeoFenceBroadcastReceiver";

    private ArFragment arFragment;
    public static AnchorNode anchorNode; // current anchor node corresponding to the geofence
    private Session session;

    @SuppressLint("LongLogTag")
    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.

        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        this.arFragment = MapsActivity.arFragment;

        if(geofencingEvent.hasError()){
            Log.d(TAG, "Error receiving geofence event");
            return;
        }

        List<Geofence> geofenceList = geofencingEvent.getTriggeringGeofences(); // gets a list of geofences
        int transitionType = geofencingEvent.getGeofenceTransition();

        // Geofence id corresponds to model name
        String modelKey = "";
        for(Geofence g : geofenceList){
            Log.d(TAG, "onReceive: "+g.getRequestId());
            modelKey = g.getRequestId(); // model name
        }

        // Performs actions according to whether user has entered, exited or dwelled in geofence area.
        switch (transitionType){
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                Log.d(TAG, "GEOFENCE_TRANSITION_ENTER");
                Toast.makeText(context, "Entered Area", Toast.LENGTH_SHORT).show();
                if(MapsActivity.modelMap != null){
                    // build model from geofence id by getting the appropriate
                    // renderable model stored in the modelMap hash map
                    ModelRenderable modelRenderable = MapsActivity.modelMap.get(modelKey);
                    Log.d(TAG, modelKey);
                    if(modelRenderable != null) {
                        Toast.makeText(context, "retrieved a model", Toast.LENGTH_SHORT).show();
                        positionModel(MapsActivity.modelMap.get(modelKey));
                    }else {
                        Toast.makeText(context, "ERROR: Geofence refers to model which does not exist",
                                Toast.LENGTH_SHORT).show();
                    }

                }else{
                    Toast.makeText(context, "ERROR: No models have been loaded", Toast.LENGTH_SHORT).show();
                }
                break;
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                Log.d(TAG, "GEOFENCE_TRANSITION_DWELL");
                break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                Toast.makeText(context, "Exited area", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "GEOFENCE_TRANSITION_EXIT");
                clearModels(); // implement some queue where item at 0 is deleted if list is not null
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + transitionType);
        }
    }

    // Places model a fixed distance from the user
    public void positionModel(ModelRenderable modelRenderable){

        session = arFragment.getArSceneView().getSession();

        // Parameters which define the pose of the anchor to be placed
        float[] position = { 0, -0.5f, -1};
        float[] rotation = { 0, 0, 0, 1 };

        assert session != null;
        Anchor anchor =  session.createAnchor(new Pose(position, rotation));

        // Creating and adding geofence to scene
        anchorNode = new AnchorNode(anchor);
        anchorNode.setRenderable(modelRenderable);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
    }

    // Clears all models within a scene
    public void clearModels(){
        List<Node> children = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());

        // removes all anchor nodes from the scene
        for (Node node : children) {
            if (node instanceof AnchorNode) {
                if (((AnchorNode) node).getAnchor() != null) {
                    arFragment.getArSceneView().getScene().removeChild(node);

                    ((AnchorNode) node).getAnchor().detach();
                }
            }
        }
    }
}