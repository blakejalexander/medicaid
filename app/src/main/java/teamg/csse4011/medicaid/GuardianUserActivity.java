package teamg.csse4011.medicaid;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class GuardianUserActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("guardian", "hello");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_user);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();


    }
}
