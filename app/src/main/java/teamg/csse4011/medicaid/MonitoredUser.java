package teamg.csse4011.medicaid;

import android.content.Intent;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import teamg.csse4011.medicaid.FallDetection.FallDetectionService;

public class MonitoredUser extends AppCompatActivity {

    private final String TAG = "MonitoredUser";

    private static boolean servicedStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_user);
        Log.d("onCreate", "onCreate called");
        /* TODO: Blake - Replace me with a user friendly solution, like a radio button */
        /* Add a background data collection service for (now at least) debugging purposes. */

//        if (servicedStarted == false) {
//            servicedStarted = true;
            Intent intent = new Intent(this, FallDetectionService.class);
            startService(intent);
//        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("main", "destroy called");
    }
}
