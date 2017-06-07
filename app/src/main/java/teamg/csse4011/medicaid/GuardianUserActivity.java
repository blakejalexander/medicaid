package teamg.csse4011.medicaid;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

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
import java.util.Random;

public class GuardianUserActivity extends AppCompatActivity {

    private static final int NUMBER_BEACONS = 4;

    /* BLE Graph config */
    private static final int MARKER_SIZE = 1;
    private static final double MAP_DISABLED_ALPHA = 0.3;
    private TextView textViewConnectFlag, statusTextView, textViewLastUpdated;

    public static GuardianUserActivity ThisInstance;
    GraphView bleGraph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_user);

        ThisInstance = this;
        editTextAddress = (EditText)findViewById(R.id.ipAddrEditText);
        editTextPort = (EditText)findViewById(R.id.portEditText);
        textViewConnectFlag = (TextView) findViewById(R.id.textViewConnectFlag);
        statusTextView = (TextView) findViewById(R.id.statusTextView);
        textViewLastUpdated = (TextView) findViewById(R.id.lastUpdatedTextView);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();

        statusTextView.requestFocus();
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
    private PointsGraphSeries<DataPoint> bleMarkerSeries;
    public static void updateBleMarkerPosition(Double x, Double y) {
        if (ThisInstance != null) {
            if (ThisInstance.bleGraph != null) {
                /* Clear map first */
                ThisInstance.bleGraph.removeSeries(ThisInstance.bleMarkerSeries);

                PointsGraphSeries<DataPoint> series = new PointsGraphSeries<>(new DataPoint[] {
                        new DataPoint(x, y),
                });


                ThisInstance.bleMarkerSeries = series;

                /* Set size and display on the grid */
                series.setSize((float)(MARKER_SIZE * 0.5 * Math.PI * Math.PI));
                series.setColor(Color.argb(255, 255, 0, 0));
                ThisInstance.bleGraph.addSeries(series);
            }
        }
    }

    public void updateBleNearestNode(int id) {
        updateBleMarkerPosition(this.pair[id].X(), this.pair[id].Y());
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

    public String currIpAddr = "10.90.185.225";
    public int currPort = 8080;
    public void updateConnection(View view) {
        currIpAddr = editTextAddress.getText().toString();
        currPort = Integer.parseInt(editTextPort.getText().toString());
        view.clearFocus();
    }

    public class XYPair
    {
        private final Double xIn;
        private final Double yIn;

        public XYPair(Double x, Double y)
        {
            xIn = x;
            yIn = y;
        }

        public Double X()   { return xIn; }
        public Double Y() { return yIn; }
    }

    /*
     * Setup function to create the BLE grid.
     */
    XYPair[] pair = new XYPair[18];
    // 10 beacons, xy pos
    public void setupBleMap() {
        GraphView graph = (GraphView) findViewById(R.id.graph);
        PointsGraphSeries<DataPoint> series = new PointsGraphSeries<>(new DataPoint[] {
                new DataPoint(0.0, 1.0),
        });
        bleMarkerSeries = series;
        series.setSize((float)(MARKER_SIZE * Math.PI * Math.PI * 0.5));

        graph.addSeries(series);
        series.setShape(PointsGraphSeries.Shape.POINT);
        int i = 0;

        /* Position of beacons */
        pair[i++] = new XYPair(0d, 0d);     // 0 - KbwM
        pair[i++] = new XYPair(3.0, 3.0);   // 1 - x0j4
        pair[i++] = new XYPair(0.0, 3.0);   // 2 - 7CmJ
        pair[i++] = new XYPair(3.0, 0.0);   // 3 - hKNK
        pair[i++] = new XYPair(1d, 3d);   // 4 - wwtU
        pair[i++] = new XYPair(2d, 3d);   // 5 - gxJj
        pair[i++] = new XYPair(1d, 1.5d);   // 6 - ko0j
        pair[i++] = new XYPair(2d, 1.5d);   // 7 - hhAz
        pair[i++] = new XYPair(1.5d, 0d);   // 8 -

        i = 0;
        PointsGraphSeries<DataPoint> series2 = new PointsGraphSeries<>(new DataPoint[] {
                new DataPoint(pair[i].X(), pair[i++].Y()), // 0
                new DataPoint(pair[i].X(), pair[i++].Y()),
                new DataPoint(pair[i].X(), pair[i++].Y()), // 2
                new DataPoint(pair[i].X(), pair[i++].Y()),
                new DataPoint(pair[i].X(), pair[i++].Y()), // 4
                new DataPoint(pair[i].X(), pair[i++].Y()),
                new DataPoint(pair[i].X(), pair[i++].Y()), // 6
                new DataPoint(pair[i].X(), pair[i++].Y()),
                new DataPoint(pair[i].X(), pair[i++].Y()), // 8
        });
        series2.setSize((float)(MARKER_SIZE * Math.PI * Math.PI));
        series2.setColor(Color.argb(100, 0, 0, 0));

        graph.addSeries(series2);
        series2.setShape(PointsGraphSeries.Shape.POINT);

        setBleMapBounds(graph);
        this.bleGraph = graph;
    }


    private boolean usingGPSFlag = false;


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


    void tellPeople() {
        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog);
        dialog.setTitle("Medicaid");

        // set the custom dialog components - text, image and button
        TextView text = (TextView) dialog.findViewById(R.id.text);
        text.setText("A fall has been detected. Are you OKAY?");
        ImageView image = (ImageView) dialog.findViewById(R.id.image);
        image.setImageResource(android.R.drawable.ic_dialog_alert);

        Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);

        // if button is clicked, close the custom dialog
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();

//
//        NotificationCompat.Builder mBuilder =
//                new NotificationCompat.Builder(this)
//                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
//                        .setContentTitle("My notification")
//                        .setContentText("Hello World!");
//
//// Creates an explicit intent for an Activity in your app
//        Intent resultIntent = new Intent(this, GuardianUserActivity.class);
//
//// The stack builder object will contain an artificial back stack for the
//// started Activity.
//// This ensures that navigating backward from the Activity leads out of
//// your application to the Home screen.
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
//// Adds the back stack for the Intent (but not the Intent itself)
//        stackBuilder.addParentStack(GuardianUserActivity.class);
//// Adds the Intent that starts the Activity to the top of the stack
//        stackBuilder.addNextIntent(resultIntent);
//        PendingIntent resultPendingIntent =
//                stackBuilder.getPendingIntent(
//                        0,
//                        PendingIntent.FLAG_UPDATE_CURRENT
//                );
//        mBuilder.setContentIntent(resultPendingIntent);
//        NotificationManager mNotificationManager =
//                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//// mId allows you to update the notification later on.
//        int mId = 1;
//        mNotificationManager.notify(mId, mBuilder.build());
    }

    private long prevTime = 0;
    /*
     * CLIENT-SIDE CONNECTION CODE
     */
    public void interpretJson(String msg) {
        long currTime = android.os.SystemClock.uptimeMillis();
        prevTime = currTime;
        String status = "";
        boolean jsonUsingGPSFlag = false;
        double jsonGpsLatitude = 153, jsonGpsLongitude = -27.;

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

        /*
         * Handle logic for non-normal statuses.
         */
        if (status == "FALLEN") {
            tellPeople();
            statusTextView.setTextColor(Color.parseColor("#ff0000"));
        } else if (status == "WAITING") {
            statusTextView.setTextColor(Color.parseColor("#ffff00"));
        } else if (status == "OKAY") {
            statusTextView.setTextColor(Color.parseColor("#118800"));
        }
        statusTextView.setText(status);

        /* Toggle relative visibility of map showing relative position */
//        if (jsonUsingGPSFlag != this.usingGPSFlag) {
            this.usingGPSFlag = jsonUsingGPSFlag;
            this.toggleGoogleMapVisibility(this.usingGPSFlag);
            this.toggleBleMapVisibility(this.usingGPSFlag);
//        }

        if (this.usingGPSFlag) {
            Location targetLocation = new Location("");
            targetLocation.setLatitude(jsonGpsLatitude);
            targetLocation.setLongitude(jsonGpsLongitude);
            MapFragment.ThisInstance.updatePatientLocation(targetLocation);
        } else { /* Update with new BLE position */
            Random r = new Random();
            int i1 = r.nextInt(NUMBER_BEACONS - 0 + 1) + 0;
            this.updateBleNearestNode(i1);
        }
        Random r = new Random();
        int i1 = r.nextInt(NUMBER_BEACONS - 0 + 1) + 0;
        this.updateBleNearestNode(i1);
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

        /*
         * Attempts to connect to current given port and IP address, with a timeout of 1 second.
         * Data is interpreted on the main UI thread if a message is received.
         */
        @Override
        protected Void doInBackground(Void... arg0) {
            Socket socket = null;
            try {
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

                /* Handle interpretation logic in main UI thread */
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
                /* Close socket */
                if (socket != null){
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            /* Update status text field */
            GuardianUserActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textViewLastUpdated.setText(String.format("%.02f s ago", (android.os.SystemClock.uptimeMillis() - prevTime) / 1000f));
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
