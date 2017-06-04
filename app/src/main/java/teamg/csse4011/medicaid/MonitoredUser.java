package teamg.csse4011.medicaid;

import android.content.Intent;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MonitoredUser extends AppCompatActivity {

    private final String TAG = "MonitoredUser";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_user);

        /* TODO: Blake - Replace me with a user friendly solution, like a radio button */
        /* Add a background data collection service for (now at least) debugging purposes. */
        Intent intent = new Intent(this, SensorMonitorService.class);
        startService(intent);

        Log.d(TAG, "onCreate called");
    }
}
