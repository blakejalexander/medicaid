package teamg.csse4011.medicaid.FallDetection;

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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import teamg.csse4011.medicaid.MainActivity;
import teamg.csse4011.medicaid.MonitoredUser;
import teamg.csse4011.medicaid.R;


public class FallDetectionService extends Service implements SensorEventListener {
    /* TODO: Blake - look into IntentService instead? Is it OK if it runs on the main thread? */

    private final String TAG = FallDetectionService.class.getSimpleName();

    private final String classificationServerAddress = "10.89.233.128";
    private final int classificationSeverPort = 4011;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private Handler sensorHandler;
    private HandlerThread sensorHandlerThread;

    private SensorDataProcessor dataProcessor;
    private Thread dataProcessingThread;

    /* FSM to detect Fall-Like events. ie. Events that look like falls. */
    private FallLikeEventDetector fallLikeFSMDetect;

    /* Constants, output returned by classification server. */
    private final int CLASSIFICATION_FALL = 0;
    private final int CLASSIFICATION_WALKING = 1;
    private final int CLASSIFICATION_JUMPING = 2;
    private final int CLASSIFICATION_BUMP = 3;

    /* DEBUG: Remove these ASAP when done. */
    final String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    final String dirname = "CSSE4011_DATA";
    final String filepath = baseDir + File.separator + dirname;
    private File testdata;


    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("sensor", "onCreate called");

        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        /* Obtain a Sensor object for the accelerometer.
         * NOTE: according to getDefaultSensor API returned sensor may be a composite or
         * filtered/averaged. If we need access to raw sensor values we need to use getSensorList
         * and pick/find the accelerometer that way. If there are multiple accelerometers, any one
         * could be picked.*/
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        /* Create the Handler and its thread that is responsible for sensor event callbacks.
         * We do this so that we keep these operations off the UI/main thread.
         * WARNING: Must call sensorHandlerThread.quitSafely when unregistering sensors. */
        this.sensorHandlerThread = new HandlerThread("SensorThread", Thread.NORM_PRIORITY);
        this.sensorHandlerThread.start();
        this.sensorHandler = new Handler(this.sensorHandlerThread.getLooper());

        /* Create the thread responsible for processing sensor values, ie. consuming data from
         * sensorHandlerThread */
        this.dataProcessor = new SensorDataProcessor();
        this.dataProcessingThread = new Thread(this.dataProcessor);
        this.dataProcessingThread.start();

        /* Iniiialise the Fall-Like event detectioN FSM. */
        this.fallLikeFSMDetect = new FallLikeEventDetector(this);

        /* TODO: Blake - we should check the return value for success. */
        this.sensorManager.registerListener(this, this.accelerometer, SensorManager.SENSOR_DELAY_FASTEST, this.sensorHandler);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "HEEEEEREE");
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
        this.testdata = new File(path, "test_data_005.csv");


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

            if (this.dataProcessor.offer(event) == false) {
                Log.w(TAG, "Queue is full!");
            }

        } else {
            Log.w(TAG, "Sensor value changed for a sensor which has not been handled yet!");
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /* Do nothing for now. At this point in time we don't care. */
        Log.d(TAG, "onAccuracyChanged!");
    }

    private class SensorDataProcessor implements Runnable {

        /* TODO: Blake - possible but not likely that we may want to use an evicting queue
         * instead. Need to investigate the practical capacity of the queue that we need,
         * currently set to 10^6 objects (so a few megabytes of RAM. */
        private LinkedBlockingQueue<SensorEvent> sensorEventQueue;

        SensorDataProcessor() {
            this.sensorEventQueue = new LinkedBlockingQueue<SensorEvent>(10000000);
        }

        public boolean offer(SensorEvent event) {
            return this.sensorEventQueue.offer(event);
        }

        @Override
        public void run() {

            /* TODO: Blake - Fall-like event detection algorithm (Finite state machine)
             * goes here. */

            while (true) {

                /* Block, waiting for a sensor reading. */
                SensorEvent event;
                try {
                    event = this.sensorEventQueue.take();
                } catch (InterruptedException e) {
                    /* TODO: Blake - not familiar with Java enough to know if this is
                     * best practice? */
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

                /* Process the event, feed it into the FallLikeEventDetector. */
                if (event.sensor.equals(accelerometer)) {

                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];

                    /* Compute acceleration magnitude. */
                    double G = Math.sqrt(x * x + y * y + z * z);

                    /* Clock the detection FSM. */
                    FallLikeEventDetector.FallLikeEventFeatures features = fallLikeFSMDetect.run
                            (event.timestamp, G);

                    /* This would be where we run the on-board TensorFlow classifier,
                     * IF WE HAD ONE */
                    if (features != null) {

                        /* Instead, we offload the classification to a server, communicating with
                         * it by sending the features in a JSON struct over TCP. */
                        int classification = classifyFall(features);

                        switch (classification) {
                            case CLASSIFICATION_FALL:
                                Log.d(TAG, "CLASSIFICATION_FALL");

                                MonitoredUser.ThisInstance.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (MonitoredUser.ThisInstance != null) {
                                            MonitoredUser.ThisInstance.updateStatus("PENDING");
                                        }
                                    }
                                });


                                break;
                            case CLASSIFICATION_JUMPING:
                                Log.d(TAG, "CLASSIFICATION_JUMPING");
                                break;
                            case CLASSIFICATION_WALKING:
                                Log.d(TAG, "CLASSIFICATION_WALKING");
                                break;
                            case CLASSIFICATION_BUMP:
                                Log.d(TAG, "CLASSIFICATION_BUMP");
                                break;
                            default:
                                break;
                        }

                    }

                    /* DEBUG: Dump the (raw) reading to flash. */
                    // this.dumpDebugAccData(event.timestamp, x, y, z);
                }

            }

        }

        /**
         * Conveinience function, used to dump data to flash for later analysis
         */
        private void dumpDebugAccData(long timestamp, float x, float y, float z) {

            /* DEBUG Code: save results to disk. */
            try {
                FileOutputStream out = new FileOutputStream(testdata, true);
                PrintWriter pw = new PrintWriter(out, true);

                /* TODO: Blake - We should also buffer our file writes, rather than open a stream and
                 * close it for literally every measurement */
                pw.println(Long.toString(timestamp) + "," + Float.toString(x) +
                        "," + Float.toString(y) + "," + Float.toString(z));

                pw.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            /* Inform Android that the file exists, so it can be viewed using USB. */
            MediaScannerConnection.scanFile(FallDetectionService.this, new String[] {
                            testdata.toString
                                    () },
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                        }
                    });

        }
    }

    private int classifyFall(FallLikeEventDetector.FallLikeEventFeatures features) {

        Socket socket = null;
        String response = null;

        Integer classification = null;

        /* Attempt to create a socket to communicate with the classification server. */
        try {


            socket = new Socket();
//            socket.setSoTimeout(2000); /* Over generous 2 second timeout. */
            socket.connect(new InetSocketAddress(classificationServerAddress,
                    classificationSeverPort), 2000);

            /* Output stream, to send response. */
            OutputStream outSockStream = socket.getOutputStream();
            PrintWriter outFile = new PrintWriter(outSockStream);

            /* Input stream to read response */
            InputStream inSockStream = socket.getInputStream();
            BufferedReader inFile = new BufferedReader(new InputStreamReader(inSockStream));


            /* Construct the JSON to send for classification */
            String jsonString = null;
            try {
                jsonString = new JSONObject()
                        .put("impact_duration", features.impactDuration)
                        .put("impact_violence", features.impactViolence)
                        .put("impact_average", features.impactAverage)
                        .put("post_impact_average", features.postImpactAverage).toString();
            } catch (org.json.JSONException e) {
                e.printStackTrace();
            }

            if (jsonString == null) {
                /* Abort, developer error. */
            }

            /* Send! */
            outFile.print(jsonString);
            outFile.flush();

            /* Read! */
            response = inFile.readLine();
            classification = Integer.parseInt(response);

            socket.close();

        } catch (SocketTimeoutException e) {
            Log.d(TAG, "timed out asking server for classification");
            e.printStackTrace();
        } catch (ConnectException e) {
            Log.d(TAG, "connection refused, is the server running?");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (classification == null) {
            return -1;
        }
        return (int)classification;

    }

}
