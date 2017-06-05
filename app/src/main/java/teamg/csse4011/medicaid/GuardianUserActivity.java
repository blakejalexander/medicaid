package teamg.csse4011.medicaid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.maps.SupportMapFragment;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.security.Guard;

public class GuardianUserActivity extends AppCompatActivity {

    /* BLE Graph config */
    private static final int MARKER_SIZE = 1;
    private static final double MAP_DISABLED_ALPHA = 0.10;

    static GuardianUserActivity ThisInstance;
    GraphView bleGraph;

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
            MapFragment.ThisInstance.updatePatientLocation(null);
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
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("guardian", "hello");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_user);

        ThisInstance = this;

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
    }
}
