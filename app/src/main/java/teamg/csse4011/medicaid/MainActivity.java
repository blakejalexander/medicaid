package teamg.csse4011.medicaid;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = getApplicationContext();
        CharSequence text = "Please enable Location sharing if not already!";
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    public void guardianModeButtonCallback(View view) {
        Intent intent = new Intent(this, GuardianUserActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityIfNeeded(intent, 0);

        Button button = (Button)findViewById(R.id.btnMonitoredUser);
        button.setEnabled(false);
    }

    public void monitoredModeButtonCallback(View view) {
        Intent intent = new Intent(this, MonitoredUser.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivityIfNeeded(intent, 0);

        Button button = (Button)findViewById(R.id.btnGuardian);
        button.setEnabled(false);
    }
}
