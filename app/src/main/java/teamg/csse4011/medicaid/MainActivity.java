package teamg.csse4011.medicaid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void guardianModeButtonCallback(View view) {
        Intent intent = new Intent(this, GuardianUserActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityIfNeeded(intent, 0);
    }

    /*
     *
     */
    public void monitoredModeButtonCallback(View view) {
        Log.d("mainActivity", "button callback");
        Intent intent = new Intent(this, MonitoredUser.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityIfNeeded(intent, 0);
    }
}
