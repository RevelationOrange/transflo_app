package com.example.revelationorange.transflo_take2;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnCameraIdleListener {
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 8;
    private static final String SHARED_PREFS = "shared_preferences";
    private static final String KEY_TRACKING = "track_status";
    private static final String KEY_MAP_TYPE = "map_type";
    private static final String TRANSFLO_ENDPOINT_URL = "http://webapp.transflodev.com/svc1.transflomobile.com/api/v3/stations/";
    private static final String TRANSFLO_AUTH = "amNhdGFsYW5AdHJhbnNmbG8uY29tOnJMVGR6WmdVTVBYbytNaUp6RlIxTStjNmI1VUI4MnFYcEVKQzlhVnFWOEF5bUhaQzdIcjVZc3lUMitPTS9paU8=";
    private static final Double INITIAL_RADIUS = 100.;
    private static final Double METERS_TO_MILES = 1/1609.34;

    private GoogleMap mMap;
    private Marker initMarker;
    private SharedPreferences sp;
    private LocationManager locationManager;
    private FusedLocationProviderClient mFusedLocationProviderClient;
    private ArrayList<Button> searchSwitchButtons = new ArrayList<>();
    private ArrayList<EditText> searchCriteriaBoxes = new ArrayList<>();
    private ArrayList<Marker> placedStopMarkers = new ArrayList<>();
    private Location mLastKnownLocation;
    private JSONArray foundStops;
    private Long trackTimeout = (long) 0;
    private Long trackingStopTime = (long) 0;
    private Marker mLastKnownMarker;
    private boolean tracking;
    private boolean searchEntered = false;
    private boolean initDone = false;
    private boolean fromSearch = false;
    private boolean lastKnownListenerSet = false;
    private String searchCriteria = "";
    private AsyncTask<Boolean, Boolean, Boolean> trackingTask;

    private Geocoder searchGeocoder;

    // app start functions
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);

        // shared preferences keep tabs on whether tracking is on and what type of view the map is
        // set to, normal or satellite
        // here, we can just set tracking, map type comes later (once the map exists)
        // then checkLocation alerts the user if location isn't enabled
        sp = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        checkLocation();
        tracking = sp.getBoolean(KEY_TRACKING, false);

        // quick check to set the text on the toggle tracking button
        if (tracking) { ((TextView) findViewById(R.id.trackingButton)).setText(R.string.trackingOnText); }
        else { ((TextView) findViewById(R.id.trackingButton)).setText(R.string.trackingOffText); }

        // this array keeps track of the buttons that need to be hidden/shown when searching
        searchSwitchButtons.add((Button) findViewById(R.id.trackingButton));
        searchSwitchButtons.add((Button) findViewById(R.id.searchPanelButton));
        searchSwitchButtons.add((Button) findViewById(R.id.recenterButton));
        searchSwitchButtons.add((Button) findViewById(R.id.satelliteToggle));

        // this array holds the boxes text is retrieved from when searching
        searchCriteriaBoxes.add((EditText) findViewById(R.id.nameSearchBox));
        searchCriteriaBoxes.add((EditText) findViewById(R.id.citySearchBox));
        searchCriteriaBoxes.add((EditText) findViewById(R.id.stateSearchBox));
        searchCriteriaBoxes.add((EditText) findViewById(R.id.zipSearchBox));

        // start the fused location provider client and make the geocoder object
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        searchGeocoder = new Geocoder(this);
    }

    private boolean checkLocation() {
        if(!isLocationEnabled())
            showAlert();
        return isLocationEnabled();
    }

    private void showAlert() {
        // this alert is shown when the user has 'location' turned off and offers them a button to
        // go change it
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Enable Location")
                .setMessage("Your Locations Settings is set to 'Off'.\nPlease Enable Location to " +
                        "use this app")
                .setPositiveButton("Location Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {

                        Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(myIntent);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {

                    }
                });
        dialog.show();
    }

    private boolean isLocationEnabled() {
        // just a check to see if it can get a location from the phone
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // set the actual map
        mMap = googleMap;

        // set the map type from sharedPreferences
        mMap.setMapType(sp.getInt(KEY_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL));
        // set a listener for marker clicks, defined later
        mMap.setOnMarkerClickListener(this);

//        InfoWindowClasses.CustomInfoWindow ciw = new InfoWindowClasses.CustomInfoWindow(this);
//        mMap.setInfoWindowAdapter(ciw);
        // set the cam idle listener, defined later
        mMap.setOnCameraIdleListener(this);

        // check if location permission is granted and if so, set the location listener function,
        // the custom info window, and lastKnownListenerSet
        // this manual check ensures these values are set, since otherwise it's only checked when
        // the user responds to a permission request
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        mLastKnownLocation = location;
                    }
                }
            });
            InfoWindowClasses.CustomInfoWindow ciw = new InfoWindowClasses.CustomInfoWindow(this);
            mMap.setInfoWindowAdapter(ciw);
            lastKnownListenerSet = true;
        }

        // if the location listener isn't set, which happens when location permission hasn't been
        // granted, set up the initial map with a marker for Pegasus TransTech and move to it
        // also custom info window hasn't been set, so it just uses the default info window
        if (!lastKnownListenerSet) {
            initMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(27.9636421, -82.5186978)).title("Pegasus TransTech"));
            mMap.moveCamera(CameraUpdateFactory.newLatLng(initMarker.getPosition()));
        }

        // get ready to set up initial view, if location permission is granted
        setLocationListener();
    }

    private void setLocationListener() {
        // check if location permission granted; if not, ask for it, if so, launch the async
        // function to set up the initial view
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }
        else { new waitForInitLocation().execute(); }
    }

    // called whenever the user allows or denies a permission
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // only checks for fine location access (since the app only ever requests that)
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            // checks if fine location access granted
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // if so, it starts the fused location provider client, so whenever the phone's location is updated, the function call is run
                // which simply sets mLastKnownLocation
                // simple updater
                mFusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            mLastKnownLocation = location;
                        }
                    }
                });

                // the custom info window is set only when location access is allowed; if it isn't, the only marker ever set is the default one
                // and its behavior is the default behavior, with a simple title
                InfoWindowClasses.CustomInfoWindow ciw = new InfoWindowClasses.CustomInfoWindow(this);
                mMap.setInfoWindowAdapter(ciw);

                // this boolean lets other functions know they don't have to do this check again
                lastKnownListenerSet = true;

                // launch an async fxn to set up the initial map view on app launch
                new waitForInitLocation().execute();
            }
        }
    }

    // map updating
    public void updateMarkers() {
        // called from a search and after getting nearby stops, this function assumes foundStops has
        // been filled and uses those stops to set markers on the map, restricting based on
        // stopNameMatch if given

        // set up stopNameMatch
        // since an async can call this, a global boolean tracks whether a search was entered
        // if so, get stopNameMatch from the search
        String stopNameMatch = "";
        if (searchEntered) { stopNameMatch = searchCriteria;}


        JSONObject currentStop;
        String stopName;
        boolean nameMatch;
        ArrayList<Marker> newPlacedStops = new ArrayList<>();
        // nonRemovalIndices (a bit oddly named maybe) tracks the stops already placed that don't
        // need to be changed
        ArrayList<Integer> nonRemovalIndices = new ArrayList<>();
        try {
            for (int i = 0; i < foundStops.length(); i++) {
                // for each stop, assume no name match found
                nameMatch = false;
                currentStop = foundStops.getJSONObject(i);
                stopName = currentStop.getString("name");
                // if stopNameMatch (from search criteria, "" otherwise) isn't in the name, no need
                // to continue
                // (defaulting to "" means you can always just do the check, since "" will always be
                // found)
                if (stopName.contains(stopNameMatch)) {
                    for (int j = 0; j < placedStopMarkers.size(); j++) {
                        // go through the already placed stops, if there's a match, add the old
                        // marker to the new marker list, and set nameMatch = true so it knows not
                        // to place a new marker
                        if (stopName.equals(placedStopMarkers.get(j).getTitle())) {
                            nameMatch = true;
                            newPlacedStops.add(placedStopMarkers.get(j));
                            nonRemovalIndices.add(j);
                        }
                    }
                    if (!nameMatch) {
                        // if no name match from the placed stops, create and place a new marker,
                        // and add it to the new placed markers list
                        LatLng coords = new LatLng(currentStop.getDouble("lat"), currentStop.getDouble("lng"));
                        Location stopLoc = new Location("");
                        stopLoc.setLatitude(coords.latitude);
                        stopLoc.setLongitude(coords.longitude);
                        // distanceTo returns distance between stops in meters, so convert to miles
                        Float dist = (float) (mLastKnownLocation.distanceTo(stopLoc) * METERS_TO_MILES);

                        // set the info window data for the custom info window to use
                        InfoWindowClasses.InfoWindowData info = new InfoWindowClasses.InfoWindowData();
                        // address is formed from rawLine1, city, and the state
                        String addr = currentStop.getString("rawLine1") + " " + currentStop.getString("city") + ", " + currentStop.getString("state");
                        // if there is a zip, add it to the address
                        if (currentStop.getString("zip").length() > 0) {
                            addr += ", " + currentStop.getString("zip");
                        }
                        info.setAddress(addr);
                        info.setDistance("Distance from you: " + dist.toString() + " mi");
                        info.setName(currentStop.getString("name"));
                        info.setRawLine2(currentStop.getString("rawLine2"));
                        info.setRawLine3(currentStop.getString("rawLine3"));

                        MarkerOptions curStopMO = new MarkerOptions().position(coords).icon(BitmapDescriptorFactory.fromResource(R.drawable.truckstop32)).title(stopName);
                        Marker curStopM = mMap.addMarker(curStopMO);
                        // set the tag of the marker to the info window data, so the custom info
                        // window can retrieve it
                        curStopM.setTag(info);
                        newPlacedStops.add(curStopM);
                    }
                }
            }

            // for any placed stops that weren't matched to a new stop, they need to be removed from
            // the map; loop through placed stops and if an index isn't in the non removals, remove
            // the marker
            for (int i = 0; i < placedStopMarkers.size(); i++) {
                if (!nonRemovalIndices.contains(i)) {
                    Marker m = placedStopMarkers.get(i);
                    m.remove();
                }
            }

            // clear the old placed stops and set the list to the new one
            placedStopMarkers.clear();
            placedStopMarkers.addAll(newPlacedStops);

            // get the lat/lng from the last known location
            LatLng lkll = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
            // and if the marker for last known is null (which means it's drawing markers the first
            // time), just add the current location marker using last known location
            if (mLastKnownMarker == null) {
                mLastKnownMarker = mMap.addMarker(new MarkerOptions().position(lkll).icon(BitmapDescriptorFactory.fromResource(R.drawable.youuuuuuu32)).title("Current location"));
            }
            else {
                // if the marker has already been placed, then check if the new location is far
                // enough away (0.05 miles) to bother placing a new marker
                // (llkll = last last known lat/lng)
                Location lastLastKnown = new Location("");
                LatLng llkll = mLastKnownMarker.getPosition();
                lastLastKnown.setLatitude(llkll.latitude);
                lastLastKnown.setLongitude(llkll.longitude);
                float dist = (float) (lastLastKnown.distanceTo(mLastKnownLocation) * METERS_TO_MILES);
                if (dist > 0.05) {
                    mLastKnownMarker.remove();
                    mLastKnownMarker = mMap.addMarker(new MarkerOptions().position(lkll).icon(BitmapDescriptorFactory.fromResource(R.drawable.youuuuuuu32)).title("Current location"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // unset check for search
        searchEntered = false;
    }

    // draw invisible circles all over the map to get the zoom level!
    // adjust the returned value by 5% so you don't have to deal with stops on the very edge of the screen
    public float getCircleZoomLevel(Circle c) {
        float zl = 0;
        if (c != null) {
            double rad = c.getRadius();
            zl = (int) (16 - Math.log(rad) / Math.log(2));
        }
        zl *= 0.95;
        return zl +.5f;
    }

    // called from tracking and onCameraIdle, this fires when the map changes
    public void updateMap() {
        // get the bounds of the current map view, use it to determine the smaller map axis, and use
        // that for the radius when getting nearby stops
        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        LatLng cent = bounds.getCenter();
        double llNeLat = bounds.northeast.latitude;
        double llSwLat = bounds.southwest.latitude;
        double llNeLng = bounds.northeast.longitude;
        double llSwLng = bounds.southwest.longitude;
        float results0[] = new float[5];
        float results1[] = new float[5];
        Location.distanceBetween(cent.latitude, llNeLng, cent.latitude, llSwLng, results0);
        Location.distanceBetween(llNeLat, cent.longitude, llSwLat, cent.longitude, results1);
        float result = Math.min(results0[0], results1[0]);
        Double radius = (double) result * METERS_TO_MILES / (2 * 1.05);

        // with the radius and current center of the map view, call the async fxn to get nearby stops
        new getNearbyStops().execute(radius, cent.latitude, cent.longitude);
    }


    // button functions
    public void recenter(View v) {
        // checks if permission has been granted (and reasks if it can), then centers the camera
        // to where? depends on what last known can find!
        if (!lastKnownListenerSet) { setLocationListener(); }
        centerViewWork();
    }

    public void centerViewWork() {
        // if you can't find the last known location, alert the user that they may need to restart
        // that's because the only way to get to this point should go through tons of permission
        // checks and if they denied all of them they really just need to stop
        if (mLastKnownLocation == null) {
            Toast.makeText(this, "Location not found, you may need to restart the application", Toast.LENGTH_SHORT).show();
        }
        else {
            // with last known location, get the lat/lng, draw a circle to get zoom, and move the camera there
            // !!important!! this causes onCameraIdle to fire
            LatLng lkll = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
            Circle c = mMap.addCircle(new CircleOptions().center(lkll).radius(INITIAL_RADIUS*4).visible(false));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(lkll, getCircleZoomLevel(c)), 1000, null);
        }
    }

    // simple, check map type and change it, then save the type to shared preferences
    public void toggleSatellite(View v) {
        if (mMap.getMapType() == mMap.MAP_TYPE_SATELLITE) { mMap.setMapType(mMap.MAP_TYPE_NORMAL); }
        else { mMap.setMapType(mMap.MAP_TYPE_SATELLITE); }
        sp.edit().putInt(KEY_MAP_TYPE, mMap.getMapType()).apply();
    }

    public void toggleTracking(View v) {
        // recheck for permission if necessary
        if (!lastKnownListenerSet) { setLocationListener(); }

        // do the actual toggle, of course, and set the text box as necessary
        if (tracking) {
            tracking = false;
            ((TextView) findViewById(R.id.trackingButton)).setText(R.string.trackingOffText);
        }
        else {
            // once more, check if the user has location enabled and if the app has permission
            // (don't do tracking if it doesn't have permission)
            if(!isLocationEnabled()) {
                showAlert();
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
                return;
            }
            tracking = true;
            ((TextView) findViewById(R.id.trackingButton)).setText(R.string.trackingOnText);

            // launch the actual tracking process
            startTracking();
        }
        // save the status of tracking between launches
        sp.edit().putBoolean(KEY_TRACKING, tracking).apply();
    }

    public void startTracking() {
        // startTracking can be launched from places that don't check if(tracking), so it must be checked here
        if (tracking) {
            // initially, center the map (so this happens as soon as tracking is enabled) and update
            LatLng lkll = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
            Circle c = mMap.addCircle(new CircleOptions().center(lkll).radius(INITIAL_RADIUS*4).visible(false));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lkll, getCircleZoomLevel(c)));
            trackTimeout = (long) 0;
            updateMap();

            // make a new trackingUpdate async and assign it to the global trackingTask
            // this ensures it can be cancelled later if necessary
            // execute with false- no initial delay
            trackingTask = new trackingUpdate();
            trackingTask.execute(false);
        }
    }

    // when search is opened or closed, show/hide the map fragment (show or gone, actually) and the
    // 4 buttons (recenter, toggle tracking/satellite, and search)
    public void openSearchPanel(View v) { toggleButtonsForSearch(true); }

    // on close, also hide the on screen keyboard
    public void closeSearchPanel(View v) {
        toggleButtonsForSearch(false);
        hideKeyboardFrom(this, v);
    }


    public void submitSearch(View v) {
        // get the stop name, city, state, and zip from their respective search boxes
        String stopName, city, state, zip, addrStr;
        ArrayList<String> enteredValues = new ArrayList<>();
        stopName = searchCriteriaBoxes.get(0).getText().toString();
        city = searchCriteriaBoxes.get(1).getText().toString();
        state = searchCriteriaBoxes.get(2).getText().toString();
        zip = searchCriteriaBoxes.get(3).getText().toString();

        // solely for the geocode results
        List<Address> searchResults;

        // if each of city, state, and zip has actually been entered, add it to the enteredValues
        // used for creating the address string
        if (city.length() > 0) { enteredValues.add(city); }
        if (state.length() > 0) { enteredValues.add(state); }
        if (zip.length() > 0) { enteredValues.add(zip); }
        addrStr = TextUtils.join(", ", enteredValues);

        // if address string length is non-zero, then the lat/lng must be retrieved from geocoding
        // and the camera must be moved
        if (addrStr.length() > 0) {
            try {
                // get the results, just use the first one, make a new lat/lng with it
                searchResults = searchGeocoder.getFromLocationName(addrStr, 5);
                LatLng moveTo = new LatLng(searchResults.get(0).getLatitude(), searchResults.get(0).getLongitude());
                // if there's a stop name entered, set these so update markers knows to check
                if (stopName.length() > 0) {
                    searchEntered = true;
                    searchCriteria = stopName;
                }
                // set fromSearch so cam idle knows (more on that later)
                fromSearch = true;
                mMap.animateCamera(CameraUpdateFactory.newLatLng(moveTo), 1500, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            // if there's no address string, no need to move the map, just set search values and
            // update the map markers
            searchEntered = true;
            searchCriteria = stopName;
            updateMarkers();
        }

        // if tracking, set the timeout to 30 if it's less than that
        if (tracking) {
            trackingStopTime = System.currentTimeMillis() / 1000;
            if (trackTimeout < 30) { trackTimeout = (long) 30; }
        }

        // toggle buttons, hide the keyboard
        toggleButtonsForSearch(false);
        hideKeyboardFrom(this, v);
    }

    public static void hideKeyboardFrom(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void toggleButtonsForSearch(boolean search) {
        // if searching, find and show the hidden search layout (set to gone initially)
        // hide the 4 buttons and set the map to gone (so as to avoid moving it accidentally)
        if (search) {
            LinearLayout hsc = findViewById(R.id.hiddenSearchContainer);
//            LinearLayout.LayoutParams searchBoxParams = new LinearLayout.LayoutParams(this);
//            hsc.setLayoutParams();
            hsc.setVisibility(View.VISIBLE);
            findViewById(R.id.mapFragment).setVisibility(View.GONE);
            for (Button b : searchSwitchButtons) { b.setVisibility(View.INVISIBLE); }
        }
        // if not searching gone the layout, show the map and the 4 buttons
        else {
            findViewById(R.id.hiddenSearchContainer).setVisibility(View.GONE);
            findViewById(R.id.mapFragment).setVisibility(View.VISIBLE);
            for (Button b : searchSwitchButtons) { b.setVisibility(View.VISIBLE); }
        }
    }


    // camera and marker listeners
    public boolean onMarkerClick(final Marker m) {
        if (m.equals(initMarker)) {
            // the initial marker needs to just do default behavior
            return false;
        }
        // if it's not the lastKnown marker (user location), change the icon and show the info window
        if (!m.equals(mLastKnownMarker)) {
            m.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.truckgo32));
            m.showInfoWindow();
        }
        // then go through all the placed markers and, if it's not the marker that was tapped and
        // it's not the lastKnown marker, set the icon back to normal
        for (Marker othermo: placedStopMarkers) {
            if (!othermo.equals(m)) {
                if (!othermo.equals(mLastKnownMarker)) {
                    othermo.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.truckstop32));
                }
            }
        }
        // if tracking, set timeout to 15 if it's under it
        if (tracking) {
            trackingStopTime = System.currentTimeMillis() / 1000;
            if (trackTimeout < 15) { trackTimeout = (long) 15; }
        }
        return true;
    }

    @Override
    public void onCameraIdle() {
        // a hearty little function, does so much work. so, so much.
        // but only if the initial view has been set
        if (initDone) {
            // if search didn't move the camera, set tracking timeout if it's under 5
            // this is so cam idle setting trackTimeout doesn't interfere with search setting it
            if (!fromSearch) {
                trackingStopTime = System.currentTimeMillis() / 1000;
                if (trackTimeout < 5) { trackTimeout = (long) 5; }
            }
            fromSearch = false;

            // if tracking, stop the current tracking async fxn, update the map, then start a new tracking async
            // this may seem weird, but simply leaving tracking interruption to the trackingTimeout
            // checks produced WEIRD results, really funky, bizarre behavior
            // this works
            // it just does
            if (tracking) {
                trackingTask.cancel(true);
                updateMap();
                trackingTask = new trackingUpdate();
                trackingTask.execute(false);
            }
            // if not tracking, simply update the map
            else { updateMap(); }
        }
    }


    // Async functions
    private class getNearbyStops extends AsyncTask<Double, Void, Void> {
        // gets stops within given radius near given lat/lng
        @Override
        protected Void doInBackground(Double... params) {
            // set rad, lat, lng from params
            // rad must be an integer for the endpoint to return stops properly
            Float rad = (float) Math.round(params[0]);
            Integer intRad = Math.round(rad);
            Double lat = params[1];
            Double lng = params[2];
            // call the workhorse of the asyncs
            asyncGetNearby(intRad, lat, lng);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            // once the getNearby stuff is done, you know the new stops list is updated, so call
            // updateMarkers to deal with the new stops
            updateMarkers();
        }
    }

    private class waitForInitLocation extends AsyncTask<Void, Void, Void> {
        // this is just to wait for lastKnown to get updated so as not to deal with null things
        @Override
        protected Void doInBackground(Void... v) {
            try {
                while (mLastKnownLocation == null) { Thread.sleep(50); }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            // once it's there, clear the map (of that one initial marker), center view, resume
            // tracking if it was set, and set initDone so cam idle knows
            mMap.clear();
            centerViewWork();
            if (tracking) { startTracking(); }
            initDone = true;
        }
    }

    private class trackingUpdate extends AsyncTask<Boolean, Boolean, Boolean> {
        // the tracking loop fxn
        @Override
        protected Boolean doInBackground(Boolean... v) {
            try {
                if (v[0]) { Thread.sleep(5000); }
                while (tracking) {
                    // as long as tracking is set true, sleep for 1 second and poke out to the main
                    // thread to maybe update things
                    Thread.sleep((long) 1000);
                    publishProgress();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Boolean... b) {
            // after the 1s sleep cycle, if the time since tracking was stopped is > the given
            // trackTimeout (it's been trackTimeout seconds since there was a user interaction), do
            // tracking things
            if ((System.currentTimeMillis() / 1000. - trackingStopTime) > trackTimeout) {
                // tracking things: get lat/lng from lastKnown, set it and initial zoom, move map there
                LatLng lkll = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
                Circle c = mMap.addCircle(new CircleOptions().center(lkll).radius(INITIAL_RADIUS * 4).visible(false));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lkll, getCircleZoomLevel(c)));
                // setting trackTimeout here because it shouldn't actually get set, since it's
                // tracking that's doing it
                trackTimeout = (long) 0;
            }
            // if tracking is timed out, just update the map (won't move the map camera, any changes
            // will necessarily be from the user)
            else { updateMap(); }
        }

        @Override
        protected void onPostExecute(Boolean b) {
        }
    }

    // does all the work of getting stops from the API
    public void asyncGetNearby(Integer intRad, Double lat, Double lng) {
        // use radius to make the url
        String endpoint = TRANSFLO_ENDPOINT_URL + intRad.toString();
        // use header for the authorization
        String header = "Basic " + TRANSFLO_AUTH;
        JSONObject body = new JSONObject();
        try {
            URL epURL = new URL(endpoint);
            // put the lat and lng in the body of the request
            body.put("lat", lat);
            body.put("lng", lng);

            // open connection, header goes in authorization, content type is json, method is POST
            HttpURLConnection epCon = (HttpURLConnection) epURL.openConnection();
            epCon.setDoOutput(true);
            epCon.addRequestProperty("Authorization", header);
            epCon.addRequestProperty("Content-Type", "application/json");
            epCon.setRequestMethod("POST");

            // output stream the body to the request
            OutputStream out = new BufferedOutputStream(epCon.getOutputStream());
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "utf-8"));
            bw.write(body.toString());
            bw.flush();
            bw.close();
            out.flush();
            out.close();

            if (epCon.getResponseCode() == 200) {
                // if response is good, use an input stream and stringbuilder to get the json results
                InputStream in = epCon.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String tmp;
                StringBuilder resp = new StringBuilder();
                while ((tmp = br.readLine()) != null) {
                    resp.append(tmp);
                }

                // put the results into foundStops, for use with updateMarkers
                foundStops = new JSONObject(resp.toString()).getJSONArray("truckStops");
            }
            else { System.out.println(epCon.getResponseCode()); }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
