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

        Intent intentGPS = new Intent(this, GPSService.class);
        //Start Service
        startService(intentGPS);
    }


    public void guardianModeButtonCallback(View view) {
        Intent intent = new Intent(this, GuardianUserActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityIfNeeded(intent, 0);
    }

    /* */
    public void monitoredModeButtonCallback(View view) {
        Log.d("mainActivity", "button callback");
        Intent intent = new Intent(this, MonitoredUser.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityIfNeeded(intent, 0);
    }
}
