package teamg.csse4011.medicaid;

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import teamg.csse4011.medicaid.FallDetection.FallLikeEventDetector;

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

    /**
     * DEBUG ONLY. Callback for a dev button. When called, this function will search on the external
     * storage root for the CSSE4011_DATA folder and for every windowXXX.csv file, it will generate
     * a featureXXX.csv file by replaying the fall-like event detection finite state machine and
     * extracting features.
     * @param view not needed.
     */
    public void debugButtonCallback(View view) {
        Log.d("DEBUG_BUTTON", "starting feature extraction from on-flash windows... ");

        final String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        final String dirname = "CSSE4011_DATA";
        final String filepath = baseDir + File.separator + dirname;

        File path = new File(filepath);
        boolean result = path.mkdirs();

        FallLikeEventDetector fallLikeFSMDetect = new FallLikeEventDetector(null);

        String fmt = "window%03d.csv";
        String fmt2 = "feature%03d.csv";

        /* Iterate through possible file names and if they exist ...*/
        for (int i = 0; i < 999; i++) {
            File wind = new File(new File(filepath), String.format(fmt, i));

            /* then read the file line by line, extract timestamp and G */
            if (wind.exists()) {

                try {

                    /* for each file */
                    FallLikeEventDetector.FallLikeEventFeatures features = null;

                    BufferedReader br = null;
                    FileReader fr = new FileReader(wind);
                    br = new BufferedReader(fr);

                    /* for each line */
                    String line = null;
                    while ((line = br.readLine()) != null) {

                        String[] row = line.split(",");
                        long timestamp = Long.parseLong(row[0]);
                        double G = Double.parseDouble(row[1]);

                        features = fallLikeFSMDetect.replay(timestamp, G);

                        if (features != null) {

                            File feat = new File(new File(filepath), String.format(fmt2, i));

                            FileOutputStream out = new FileOutputStream(feat, true);
                            PrintWriter pw = new PrintWriter(out, true);

                            pw.println(features.impactDuration + "," + features.impactViolence +
                                            "," + features.impactAverage + "," +
                                    features.postImpactAverage);

                            pw.close();
                            out.close();

                            Log.d("DEBUG_BUTTON", "extracted features to " + feat.toString());

                            /* Inform Android that the feature file exists, so it can be viewed
                             * using USB. */
                            MediaScannerConnection.scanFile(this, new String[] {
                                            feat.toString() },
                                    null,
                                    new MediaScannerConnection.OnScanCompletedListener() {
                                        public void onScanCompleted(String path, Uri uri) {
                                        }
                                    });


                        }

                    }

                    /* TODO: FIXME: Blake - handle better, but its debug code so don't care. */
                } catch (FileNotFoundException e) {
                } catch (IOException e) {
                }
            }
        }

    }
}
