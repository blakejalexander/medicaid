package teamg.csse4011.medicaid;

import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.security.Guard;

/**
 * Created by Andrew on 4/06/2017.
 */

public class MapFragment extends com.google.android.gms.maps.MapFragment implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleMap.OnInfoWindowClickListener,
        GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        LocationListener {

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private LocationRequest mLocationRequest;
    private long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */
    public static MapFragment ThisInstance;
    private final int[] MAP_TYPES = { GoogleMap.MAP_TYPE_SATELLITE,
            GoogleMap.MAP_TYPE_NORMAL,
            GoogleMap.MAP_TYPE_HYBRID,
            GoogleMap.MAP_TYPE_TERRAIN,
            GoogleMap.MAP_TYPE_NONE };
    private int curMapTypeIndex = 2;

    private Marker posMarker = null;


    public void toggleGoogleMap(Boolean flag) {

    }
    static int counter = 0;
    /* Update the camera view and marker position on the map for given patient location. */
    public void updatePatientLocation(Location location) {
        // New location has now been determined
        if (location == null) {
            return;
        }
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + ", " +
                Double.toString(location.getLongitude());
        Log.d("UpdateMap", msg);

        // You can now create a LatLng Object for use with maps
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (posMarker != null) {
            posMarker.setPosition(latLng);
        }

        /* Update camera position */
        CameraUpdate center = CameraUpdateFactory.newLatLng(latLng);
//        CameraUpdate zoom = CameraUpdateFactory.zoomTo(24);

        if (getMap() != null) {
            getMap().moveCamera(center);
//            getMap().animateCamera(zoom);
        }

    }

    public void onLocationChanged(Location location) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onInfoWindowClick(Marker marker) {

    }

    @Override
    public void onMapLongClick(LatLng latLng) {

    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        return true;
    }

    private void initListeners() {
        getMap().setOnMarkerClickListener(this);
        getMap().setOnMapLongClickListener(this);
        getMap().setOnInfoWindowClickListener( this );
        getMap().setOnMapClickListener(this);
    }

    /* Entry point of class */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(true);

        mGoogleApiClient = new GoogleApiClient.Builder( getActivity() )
                .addConnectionCallbacks( this )
                .addOnConnectionFailedListener( this )
                .addApi( LocationServices.API )
                .build();

        initListeners();
    }

    @Override
    public void onStart() {
        ThisInstance = this;
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if( mGoogleApiClient != null && mGoogleApiClient.isConnected() ) {
            mGoogleApiClient.disconnect();
        }
    }

    //
    private void initCamera( Location location ) {
        Log.d("map", "init cam");
        LatLng mapPos = new LatLng( location.getLatitude(), // latitude
                location.getLongitude());
        CameraPosition position = CameraPosition.builder()
                .target( mapPos )
                .zoom( 50f )
                .bearing( 0.0f )
                .tilt( 0.0f )
                .build();

        getMap().animateCamera( CameraUpdateFactory
                .newCameraPosition( position ), null );

        getMap().setMapType( MAP_TYPES[curMapTypeIndex] );
        getMap().setTrafficEnabled( false );
        getMap().setMyLocationEnabled( false ); // Draws blue dot and circle at current position; we want only the position from the other device
        getMap().getUiSettings().setZoomControlsEnabled( true );

        if (posMarker == null) {
            Log.d("map", "new marker");
            MarkerOptions options = new MarkerOptions().position(mapPos);

            options.icon(BitmapDescriptorFactory.defaultMarker());
            posMarker = getMap().addMarker(options);
        }
    }


    @Override
    public void onConnected(Bundle bundle) {
        Log.d("map", "onConnected");
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation( mGoogleApiClient );
        // Note that this can be NULL if last location isn't already known.
        if (mCurrentLocation != null) {
            // Print current location if not null
            Log.d("DEBUG", "current location: " + mCurrentLocation.toString());
            LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        }

        // Begin polling for new location updates.
        initCamera( mCurrentLocation );
    }

    @Override
    public void onMapClick(LatLng latLng) {

    }
}