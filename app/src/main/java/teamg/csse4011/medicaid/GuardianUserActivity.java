package teamg.csse4011.medicaid;

import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.maps.SupportMapFragment;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.PointsGraphSeries;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.Guard;
import java.util.concurrent.TimeoutException;

public class GuardianUserActivity extends AppCompatActivity {

    /* BLE Graph config */
    private static final int MARKER_SIZE = 1;
    private static final double MAP_DISABLED_ALPHA = 0.3;
    private TextView textViewConnectFlag;

    static GuardianUserActivity ThisInstance;
    GraphView bleGraph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_user);

        ThisInstance = this;
        editTextAddress = (EditText)findViewById(R.id.ipAddrEditText);
        editTextPort = (EditText)findViewById(R.id.portEditText);
        textViewConnectFlag = (TextView) findViewById(R.id.textViewConnectFlag);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
    }

    /*
     * Toggles the relative visibility of the Google Map, whose location is determine by the device
     * GPS position.
     */
    public void toggleGoogleMapVisibility(Boolean flag) {
        // Hide layout
        FrameLayout map = (FrameLayout) findViewById(R.id.mapLayout);
//        map.setVisibility(flag ? View.VISIBLE : View.INVISIBLE);
        map.setAlpha((float)(flag ? 1.0 : MAP_DISABLED_ALPHA));

    }

    /*
     * Toggles the relative visibility of the BLE multilateration estimated position.
     */
    public void toggleBleMapVisibility(Boolean flag) {
        // Hide layout
        FrameLayout map = (FrameLayout) findViewById(R.id.bleLayout);
//        map.setVisibility(flag ? View.VISIBLE : View.INVISIBLE);
        map.setAlpha((float)(flag ? 1.0 : MAP_DISABLED_ALPHA));
    }

    /*
     * Update the marker position on the BLE map.
     *
     * Clears the map first then adds a new marker.
     *
     */
    public static void updateBleMarkerPosition(Double x, Double y) {
        if (ThisInstance != null) {
            if (ThisInstance.bleGraph != null) {
                /* Clear map first */
                ThisInstance.bleGraph.removeAllSeries();
                PointsGraphSeries<DataPoint> series = new PointsGraphSeries<>(new DataPoint[] {
                        new DataPoint(x, y),
                });

                /* Set size and display on the grid */
                series.setSize((float)(MARKER_SIZE * Math.PI * Math.PI));
                ThisInstance.bleGraph.addSeries(series);
            }
        }
    }

    /*
     * Sets the boundary of the cartesian grid (x and y-axis limits).
     */
    public void setBleMapBounds(GraphView graph) {
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(3);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(3);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
    }

    public String currIpAddr = "10.89.184.67";
    public int currPort = 8080;
    public void updateConnection(View view) {
        currIpAddr = editTextAddress.getText().toString();
        currPort = Integer.parseInt(editTextPort.getText().toString());
    }

    /*
     * Setup function to create the BLE grid.
     */
    public void setupBleMap() {
        GraphView graph = (GraphView) findViewById(R.id.graph);
        PointsGraphSeries<DataPoint> series = new PointsGraphSeries<>(new DataPoint[] {
                new DataPoint(1.5, 1.5),
        });

        series.setSize((float)(MARKER_SIZE * Math.PI * Math.PI));

        graph.addSeries(series);
        series.setShape(PointsGraphSeries.Shape.POINT);
        setBleMapBounds(graph);
        this.bleGraph = graph;
    }


    private boolean usingGPSFlag = false;

    // stub metohd only - received packet/JSON msg from monitoring device
    public void stub () {
        boolean jsonUsingGPSFlag = false;
        double jsonBleX = 0., jsonBleY = 0.;
        float jsonGpsLatitude, jsonGpsLongitude;

        /* Extract data from arrived JSON packet */

        /* Toggle relative visibility of map showing relative position */
        if (jsonUsingGPSFlag != this.usingGPSFlag) {
            this.usingGPSFlag = jsonUsingGPSFlag;
            this.toggleGoogleMapVisibility(this.usingGPSFlag);
            this.toggleBleMapVisibility(!this.usingGPSFlag);
        }

        /* Update with new GPS latitude and longitude */
        if (this.usingGPSFlag) {
//            MapFragment.ThisInstance.updatePatientLocation();
        } else { /* Update with new BLE position */
            this.updateBleMarkerPosition(jsonBleX, jsonBleY);
        }

        /* Monitored user status is FALLEN or NEEDS HELP. Send notification to guardian user and
         * emphasise it in the screen.
         */
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.setupBleMap();
        this.toggleBleMapVisibility(false);
        this.toggleGoogleMapVisibility(false);
// Start the initial runnable task by posting through the handler
        handler.post(runnableCode);
    }
    EditText editTextAddress, editTextPort;

    public void requestData() {
        /* Request packet from server */
        MyClientTask myClientTask = new MyClientTask(currIpAddr, currPort);
        myClientTask.execute();
    }

    // Create the Handler object (on the main thread by default)
    Handler handler = new Handler();

    // Define the code block to be executed
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
            requestData();

            /* Periodically repeat action */
            handler.postDelayed(runnableCode, 5000);
        }
    };


    /*
     * CLIENT-SIDE CONNECTION CODE
     */

    public void interpretJson(String msg) {
        String status = "";
        boolean jsonUsingGPSFlag = false;
        double jsonGpsLatitude = 0., jsonGpsLongitude = 0.;

        JSONObject jObject = null;
        Log.d("4011json", "parsing " + msg);
        try {
            jObject = new JSONObject(msg);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        try {
            status = jObject.getString("status");
            jsonUsingGPSFlag = jObject.getBoolean("usingGps");
            jsonGpsLatitude = jObject.getDouble("latitude");
            jsonGpsLongitude = jObject.getDouble("longitude");

        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            Log.d("4011json", "Status: " + status);
            Log.d("4011json", "FLAG: " + jsonUsingGPSFlag);
            Log.d("4011json", "Latitude: " + jsonGpsLatitude);
            Log.d("4011json", "Longitude: " + jsonGpsLongitude);
        }

        if (MapFragment.ThisInstance != null) {
            //MapFragment.ThisInstance.updatePatientLocation(location);
        }

        /* Extract data from arrived JSON packet */


        /* Toggle relative visibility of map showing relative position */
//        if (jsonUsingGPSFlag != this.usingGPSFlag) {
            this.usingGPSFlag = jsonUsingGPSFlag;
            this.toggleGoogleMapVisibility(this.usingGPSFlag);
            this.toggleBleMapVisibility(!this.usingGPSFlag);
//        }

        if (this.usingGPSFlag) {
            Location targetLocation = new Location("");//provider name is unnecessary
            targetLocation.setLatitude(jsonGpsLatitude);//your coords of course
            targetLocation.setLongitude(jsonGpsLongitude);
            MapFragment.ThisInstance.updatePatientLocation(targetLocation);
        } else { /* Update with new BLE position */
//            this.updateBleMarkerPosition(jsonBleX, jsonBleY);
        }
    }

    public class MyClientTask extends AsyncTask<Void, Void, Void> {
        String dstAddress;
        int dstPort;
        String response = "";

        MyClientTask(String addr, int port){
            Log.d("4011guardian", "Requesting data from " + currIpAddr + " @ " + Integer.toString(currPort));
            dstAddress = addr;
            dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            Socket socket = null;
            try {
                Log.d("4011guardian", "try socket");
                socket = new Socket();
                socket.connect(new InetSocketAddress(dstAddress, dstPort), 1000);
                socket.setSoTimeout(1000);

                ByteArrayOutputStream byteArrayOutputStream =
                        new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];

                int bytesRead;
                InputStream inputStream = socket.getInputStream();

				/*
				 * notice:
				 * inputStream.read() will block if no data return
				 */
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    response += byteArrayOutputStream.toString("UTF-8");
                }
//                Log.d("4011guardian", "Received: [" + byteArrayOutputStream.toString() + "]");


                GuardianUserActivity.this.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        interpretJson(response);
                    }
                });

            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "UnknownHostException: " + e.toString();
            } catch (SocketTimeoutException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "SocketTimeout: " + e.toString();
                Log.d("4011guardian", "timeout");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "IOException: " + e.toString();
            } finally{
                if (socket != null){
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            GuardianUserActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    textViewConnectFlag.setText(response);
                }
            });
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
        }
    }
}
