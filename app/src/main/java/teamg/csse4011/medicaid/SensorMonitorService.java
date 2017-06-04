package teamg.csse4011.medicaid;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Intent;
import android.content.Context;

import android.media.MediaScannerConnection;
import android.net.Uri;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;


public class SensorMonitorService extends Service implements SensorEventListener {
    /* TODO: Blake - look into IntentService instead? Is it OK if it runs on the main thread? */

    private final String TAG = SensorMonitorService.class.getSimpleName();

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private Handler sensorHandler;
    private HandlerThread sensorHandlerThread;


    /* DEBUG: Remove these ASAP when done. */
    final String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    final String dirname = "blake-csse4011";
    final String filepath = baseDir + File.separator + dirname;
    private File testdata;

    /* TODO: Blake - actually use this.*/
    private int samplingPeriodUs = (1 / 500) * 10^(-6); /* 500Hz. */

    @Override
    public void onCreate() {
        
        super.onCreate();

        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        /* Obtain a Sensor object for the accelerometer.
         * NOTE: according to getDefaultSensor API returned sensor may be a composite or
         * filtered/averaged. If we need access to raw sensor values we need to use getSensorList
         * and pick/find the accelerometer that way. If there are multiple accelerometers, any one
         * could be picked.*/
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


        /* Create the Handler and its thread that is responsible for sensor event callbacks.
         * We do this so that we keep these operations off the UI/main thread. */
        this.sensorHandlerThread = new HandlerThread("SensorThread", Thread.MAX_PRIORITY);
        this.sensorHandlerThread.start();
        this.sensorHandler = new Handler(this.sensorHandlerThread.getLooper());


        /* TODO: Blake - we should check the return value for success. */
        this.sensorManager.registerListener(this, this.accelerometer,
                SensorManager.SENSOR_DELAY_UI, this.sensorHandler);
    }

    /**
     * Called when the process is started via a call to Context.startService. This function creates
     * the internal SensorManager and registers listeners for the accelerometer.
     * @param intent
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        /* TODO: Blake - need to handle Intents properly. */

        /* DEBUG STUFF HERE, REMOVE BEFORE COMMIT */
        Log.d(this.TAG, "onHandleIntent called");
        File path = new File(filepath);
        boolean result = path.mkdirs();
        this.testdata = new File(path, "test_data.csv");


        /* We need to transform this Service into a foreground service. That is, a service which is
         * always running, even if the main activity is killed. */

        /* But first, we need to construct the notification that (must) be displayed by the
         * Android OS. */
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(getApplicationContext())
            .setContentTitle(getApplicationContext().getString(R.string.app_name))
            .setContentText("Accidental Fall Monitor - Currently Running")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true).build();

        /* Now we actually turn this service into a foreground service! */
        startForeground(101, notification); /* TODO: Blake - Use a proper notification ID !!!! */

        /* We return START_STICKY so that if Android kills our service due to OOM, the OS will
         * recreate the service and call this function (with a null intent). */
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(this.TAG, "onBind called");
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        /* Handle the sensors that we are listening for here.*/
        if (event.sensor.equals(this.accelerometer)) {
            /* TODO: Blake - Extract values for the sensor here. */

            /* Take accelerometer readings. */
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            Log.d(TAG, "read value from sensor: " + Float.toString(x));

            /* Write readings to a file. */

            boolean ex = this.testdata.exists();

            try {
                FileOutputStream out = new FileOutputStream(this.testdata, true);
                PrintWriter pw = new PrintWriter(out, true);
                pw.println(Long.toString(System.currentTimeMillis()) + "," + Float.toString(x)
                        +","+Float.toString(y)+","+ Float.toString(z));
                pw.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            /* Inform Android that the file exists, so it can be viewed using USB. */
            MediaScannerConnection.scanFile(this, new String[] { this.testdata.toString() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                        }
                    });

        } else {
            Log.w(TAG, "Sensor value changed for a sensor which has not bene handled yet!");
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Do nothing for now. At this point in time we don't care. */
    }
}
