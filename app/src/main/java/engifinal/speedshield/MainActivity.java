package engifinal.speedshield;

import android.content.Intent;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import javax.net.ssl.HttpsURLConnection;
import org.greenrobot.eventbus.EventBus;

import io.nlopez.smartlocation.OnActivityUpdatedListener;
import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.OnReverseGeocodingListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesProvider;

/**
 * TODO: in this order
 * 1. keep speed lim on
 * 2. if app is on, check if moving at sufficient speed
 * 3. if so, get speed limit of area and track velo
 *    a. speed limit checked every minute. velo checked every 1/4 sec
 * 4. if velocity is below 10mph for 4 minutes, stop tracking
 * 8. Done
 */
public class MainActivity extends AppCompatActivity implements  OnMyLocationButtonClickListener,
        OnMapReadyCallback,
        ActivityCompat.OnRequestPermissionsResultCallback,
        OnLocationUpdatedListener, OnActivityUpdatedListener{

    /**
     * Request code for location permission request.
     *
     * @see #onRequestPermissionsResult(int, String[], int[])
     */
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mPermissionDenied = false;

    private GoogleMap mMap;
    private LocationGooglePlayServicesProvider provider;
    private Location currentLocation;
    private int speedLimit;
    private double lastTimeUpdatedLimit;
    private double lastTimeDriving;
    private long drivingPoints = 0;
    private boolean streak = false;
    private long lastReward = 0;
    private int penaltyCounter = 20;

    private static final int LOCATION_PERMISSION_ID = 1001;
    private TextView speedLimitTextView;
    private TextView currentSpeedTextView;
    private TextView pointsTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        speedLimit = -1;
        lastTimeUpdatedLimit = System.currentTimeMillis();
        speedLimitTextView = (TextView) findViewById(R.id.speedLimitNumView);
        currentSpeedTextView = (TextView) findViewById(R.id.currentSpeedNumView);
        pointsTextView = (TextView) findViewById(R.id.pointsView);

        //--------loco---------

        //Stall until user enables location
        while (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_ID);
        }
        startLocation();

        //-----------end loco------------

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    private void startLocation() {

        provider = new LocationGooglePlayServicesProvider();
        provider.setCheckLocationSettings(true);

        SmartLocation smartLocation = new SmartLocation.Builder(this).logging(true).build();

        smartLocation.location(provider).start(this);
        smartLocation.activity().start(this);

    }

    private void stopLocation() {
        SmartLocation.with(this).location().stop();

        SmartLocation.with(this).activity().stop();


    }

    @Override
    public void onActivityUpdated(DetectedActivity detectedActivity) {
        showActivity(detectedActivity);
    }

    private void showActivity(DetectedActivity detectedActivity) {
        if (detectedActivity != null) {
            System.out.println(
                    String.format("Activity %s with %d%% confidence",
                            getNameFromType(detectedActivity),
                            detectedActivity.getConfidence())
            );
        } else {
            System.out.println("Null activity");
        }
    }

    private void showLocation(Location location) {
        if (location != null) {
            final String text = String.format("Latitude %.6f, Longitude %.6f",
                    location.getLatitude(),
                    location.getLongitude());
            System.out.println("Location: " + text);

            // We are going to get the address for the current position
            SmartLocation.with(this).geocoding().reverse(location, new OnReverseGeocodingListener() {
                @Override
                public void onAddressResolved(Location original, List<Address> results) {
                    if (results.size() > 0) {
                        Address result = results.get(0);
                        StringBuilder builder = new StringBuilder(text);
                        builder.append("\n[Reverse Geocoding] ");
                        List<String> addressElements = new ArrayList<>();
                        for (int i = 0; i <= result.getMaxAddressLineIndex(); i++) {
                            addressElements.add(result.getAddressLine(i));
                        }
                        builder.append(TextUtils.join(", ", addressElements));
                        System.out.println(builder.toString());
                    }
                }
            });
        } else {
            System.out.println("Null location");
        }
    }

    @Override
    public void onLocationUpdated(Location location) {
        currentLocation = location;

        if (currentLocation.getSpeed() >= 6.7056) //20 mph threshold
        {
            lastTimeDriving = System.currentTimeMillis();
            int currentSpeedMph = (int) (currentLocation.getSpeed() * 2.2369);
            currentSpeedTextView.setText(Integer.toString(currentSpeedMph) + " MPH");

            if (System.currentTimeMillis() - lastTimeUpdatedLimit >= 120000) {
                Thread thread = new Thread(new Runnable(){
                    @Override
                    public void run(){
                        try {
                            updateSpeedLimit();
                        }
                        catch(Exception e) {
                            System.out.println("something bad happened with speed lim");
                        }
                    }
                });
                thread.start();
            }
            rewardPoints();
        }
        //stop driving mode if stopped for 1.5 minutes or longer
        else if (System.currentTimeMillis() - lastTimeDriving >= 90000) {
            currentSpeedTextView.setText("<20 MPH");
        }
    }

    private void rewardPoints() {
        double currentSpeedMph = currentLocation.getSpeed() * 2.2369;
        //optimal speed
        if (currentSpeedMph < (speedLimit * 1.1) && currentSpeedMph > (speedLimit *0.9))
        {
            if (streak)
            {
                drivingPoints += lastReward +1;
                lastReward = lastReward +1;
            }
            else if (penaltyCounter == 1)
            {
                drivingPoints += 1;
                streak = true;
                lastReward = 1;
            }
            else {
                penaltyCounter--;
            }
        }
        else if (currentSpeedMph > 5)
        {
            streak = false;
            if (penaltyCounter <= 5)
                penaltyCounter = 20;
            else
                penaltyCounter += penaltyCounter /10;
        }

        String pointsString = Long.toString(drivingPoints);
        pointsTextView.setText("Points: " + pointsString);
    }

    private String getNameFromType(DetectedActivity activityType) {
        switch (activityType.getType()) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.TILTING:
                return "tilting";
            default:
                return "unknown";
        }
    }











    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.setOnMyLocationButtonClickListener(this);
        enableMyLocation();
    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (mMap != null) {
            // Access to the location has been granted to the app.
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show();
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }

    public void updateSpeedLimit() throws Exception {
        int speedLim = -1;
        try {
            String latString = Double.toString(currentLocation.getLatitude());
            String longString = Double.toString(currentLocation.getLongitude());

            System.out.println("trying to get location of latlong: " + latString + ", " + longString);
            String url = "https://route.cit.api.here.com/routing/7.2/getlinkinfo.json?waypoint=" + latString +"%2C" + longString + "&app_id=ZQJA8fSBp8MZzFFMFcF8&app_code=Hobr4vTPttml3HNkFByD2g";

            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // optional default is GET
            con.setRequestMethod("GET");

            //add request header
            //con.setRequestProperty("User-Agent", USER_AGENT);

            int responseCode = con.getResponseCode();
            System.out.println("\nSending 'GET' request to URL : " + url);
            System.out.println("Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String speedLimitString = new String();
            //get speed limit as string
            for (int i = response.length() - 1; i > 0; i--)
            {
                if (response.charAt(i) == ':' && Character.isDigit(response.charAt(i+1)) &&
                    response.charAt(i-1) == '"')
                {
                    for (int j = i+1; response.charAt(j) != '}'; j++)
                    {
                        speedLimitString += response.charAt(j);
                    }
                    i = -1;
                }
                if (i == 0)
                    speedLimitString = "-1";
            }

            //convert string to double
            Double speedLimAsDouble = Double.parseDouble(speedLimitString) * 2.2369; //convert m/s to mph
            speedLim = speedLimAsDouble.intValue();

            //print result
            System.out.println("Speed limit was: " + speedLim +  " mph!");
            System.out.println("our speed is: " + Double.toString(currentLocation.getSpeed()));

        }
        catch(Exception e) {
            System.out.println("exception caught undhandled");
            System.out.println(e + " was the exception");
        }
        //update speed limit on UI and for this object
        lastTimeUpdatedLimit = System.currentTimeMillis();
        speedLimit = speedLim;
        speedLimitTextView.setText(Integer.toString(speedLimit));
    }
    public void start_profile_activity(View view) {
        Intent intent = new Intent(this, profile.class);
        startActivity(intent);
    }

    public void start_rewards_activity(View view) {
        Intent intent = new Intent(this, rewards.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
