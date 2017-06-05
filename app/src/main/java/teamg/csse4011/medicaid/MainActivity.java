package teamg.csse4011.medicaid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import static android.provider.AlarmClock.EXTRA_MESSAGE;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "teamg.csse4011.medicaid.MESSAGE";
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start service
//        Intent intent = new Intent(this, AccelerometerService.class);
//        //Start Service
//        startService(intent);
//
        Intent intentGPS = new Intent(this, GPSService.class);
        //Start Service
        startService(intentGPS);

    }

    public void guardianModeButtonCallback(View view) {
        Intent intent = new Intent(this, GuardianUserActivity.class);
        startActivity(intent);
    }

    /* */
    public void monitoredModeButtonCallback(View view) {

        Intent intent = new Intent(this, MonitoredUser.class);
        startActivity(intent);
    }
}
