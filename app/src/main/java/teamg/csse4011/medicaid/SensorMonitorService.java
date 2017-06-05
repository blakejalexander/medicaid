package teamg.csse4011.medicaid;

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
import android.os.Vibrator;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class SensorMonitorService extends Service implements SensorEventListener {
    /* TODO: Blake - look into IntentService instead? Is it OK if it runs on the main thread? */

    private final String TAG = SensorMonitorService.class.getSimpleName();

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private Handler sensorHandler;
    private HandlerThread sensorHandlerThread;

    private SensorDataProcessor dataProcessor;
    private Thread dataProcessingThread;

    /* FSM to detect Fall-Like events. ie. Events that look like falls. */
    private FallLikeEventDetector fallLikeFSMDetect;

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
        this.fallLikeFSMDetect = new FallLikeEventDetector();

        /* TODO: Blake - we should check the return value for success. */
        this.sensorManager.registerListener(this, this.accelerometer, SensorManager.SENSOR_DELAY_FASTEST, this.sensorHandler);
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
    }

    /** Fall-Like Event detection finite state machine.
     *  TODO: Blake - Need to write doc explaining how it works (or how its intended to work) still.
     */
    private class FallLikeEventDetector {

        /* States. */
        private final int STATE_WAITING_FOR_PEAK = 0;
        private final int STATE_POST_PEAK_EVENT = 1;
        private final int STATE_POST_FALL_EVENT = 2;
        private final int STATE_WAIT_RECOVERY_EVENT = 3;

        /* Acceleration magnitude detection threshold. */
        private final double THRESHOLD_PEAK = 3.0 * SensorManager.GRAVITY_EARTH;

        /* Timeout values of particular states. */
        private final long POST_PEAK_TIMEOUT_MS = 500;
        private final long POST_FALL_TIMEOUT_MS = 1000;

        /* State 'timers', really just a relative timestamp that is checked. */
        private long postPeakTimeStart;
        private long postFallTimeStart;

        /* Current state of the FSM. */
        private int state;

        /* TODO: Blake - write comment explaining what I'm for. */
        private LinkedHashMap<Long, Double> fallLikeEventWindow;


        /* TOOD: FIXME: Blake - note that the "timers" in this FSM assume (heavily) that data is
         * still coming in before the timer expires. If this doesn't happen for some reason then
         * here be dragons also known as bugs. */

        FallLikeEventDetector() {

            /* Set the initial FSM state. */
            state = STATE_WAITING_FOR_PEAK;

            /* Reset timer values. */
            postPeakTimeStart = 0;
            postFallTimeStart = 0;

            /* Initialise internal data window. */
            fallLikeEventWindow = new LinkedHashMap<>();

        }

        public void run(long timestamp, double G) {

            /* Quantise the timestamp into milliseconds to allow easier processing. */
            timestamp = TimeUnit.MILLISECONDS.convert(timestamp, TimeUnit.DAYS.NANOSECONDS);

            switch (state) {

                /* In this state, we're waiting for an acceleration reading to exceed
                 * THRESHOLD_PEAK. If it does not, we stay in this state until it does. */
                case STATE_WAITING_FOR_PEAK: /* FIXME: Aka sampling state*/
                    Log.d(TAG, "STATE_WAITING_FOR_PEAK");

                    if (G >= THRESHOLD_PEAK) {

                        /* Set the the next state's timer. */
                        postPeakTimeStart = timestamp;
                        state = STATE_POST_PEAK_EVENT;

                        /* Start a fresh fall-like event data window. */
                        fallLikeEventWindow.clear();
                        fallLikeEventWindow.put(timestamp, G);

                    } else {
                        state = STATE_WAITING_FOR_PEAK;
                    }

                    break;

                case STATE_POST_PEAK_EVENT:
                    Log.d(TAG, "STATE_POST_PEAK_EVENT");


                    fallLikeEventWindow.put(timestamp, G);

                    /* Check if timer expired. If so, the fall has finished, go to the post fall
                     * state. */
                    if (timestamp - postPeakTimeStart > POST_PEAK_TIMEOUT_MS) {

                        /* Set the timer for the next state, and transition to it on the next
                         * FSM clock. */
                        postFallTimeStart = timestamp;
                        state = STATE_POST_FALL_EVENT;
                        break;
                    }

                    /* If we detect ANOTHER acceleration peak, we reset the timer and reenter this
                     * state as we entered from STATE_WAITING_FOR_PEAK as in the first time. */
                    if (G >= THRESHOLD_PEAK) {

                        /* Start a fresh fall-like event data window. */
                        fallLikeEventWindow.clear();
                        fallLikeEventWindow.put(timestamp, G);

                        /* Reset the timer, set the next state to be this one. */
                        postPeakTimeStart = timestamp;
                        state = STATE_POST_PEAK_EVENT;

                        break;
                    }

                    /* Otherwise, we're still in the post-peak state, we can only exit this state
                     * if the timer expires. */
                    state = STATE_POST_PEAK_EVENT;
                    break;

                case STATE_POST_FALL_EVENT:
                    Log.d(TAG, "STATE_POST_FALL_EVENT");


                    fallLikeEventWindow.put(timestamp, G);

                    /* If our timer has expired. It's time to state transition out. */
                    if (timestamp - postFallTimeStart > POST_FALL_TIMEOUT_MS) {
                        state = STATE_WAIT_RECOVERY_EVENT;
                        break;
                    }

                    /* Go back to the post-peak detection event state. Restart the timer. */
                    if (G >= THRESHOLD_PEAK) {

                        /* Start a fresh fall-like event data window. */
                        fallLikeEventWindow.clear();
                        fallLikeEventWindow.put(timestamp, G);

                        postPeakTimeStart = timestamp;
                        state = STATE_POST_PEAK_EVENT;
                        break;
                    }

                    break;

                case STATE_WAIT_RECOVERY_EVENT:
                    Log.d(TAG, "STATE_WAIT_RECOVERY_EVENT");

                    /* Process window data. Do some sort of calculation???? */
                    if (true) {
                        state = STATE_WAITING_FOR_PEAK;

                        /* DEBUG: Dump window to a file. Each window is in its own unique file.
                         * We set the arbritrary limit of 10000 windows to be stored at any time.
                         * if theres 10000, the 10000th window will be appended to. */
                        String fmt = "window%03d.csv";
                        File wind = null;
                        for (int i = 0; i < 10000; i++) {
                            wind = new File(new File(filepath), String.format(fmt, i));
                            if (!wind.exists()) {
                                break;
                            }
                        }

                        /* DEBUG Code: Fall occured, let developer know via feedback. */
                        Vibrator v = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
                        v.vibrate(1000);

                        /* DEBUG Code: save window to flash for later analysis. */
                        try {
                            FileOutputStream out = new FileOutputStream(wind, true);
                            PrintWriter pw = new PrintWriter(out, true);

                            for (Map.Entry<Long, Double> entry : fallLikeEventWindow.entrySet()) {
                                pw.println(entry.getKey() + "," + entry.getValue());
                            }

                            pw.close();
                            out.close();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        /* DEBUG Code: Inform Android that the file exists, so it can be viewed
                         * using USB ASAP */
                        MediaScannerConnection.scanFile(SensorMonitorService.this, new String[] {
                                        wind.toString
                                                () },
                                null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    public void onScanCompleted(String path, Uri uri) {
                                    }
                                });



                        break;
                    }
                    
                    break;
            }
        }
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
                    fallLikeFSMDetect.run(event.timestamp, G);

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

            /* TODO: Blake - Samples are currently timestamped when they're written to
             * flash. This is an expensive operation, especially the way it is
             * currently implemented. Whilst this is only debug code we still need
             * to be careful since it forms the basis of our model analysis.
             *
             * We need to timestamp the samples relative to when they were actually
             * measured. The SensorEvent object has a timestampe but its relative to
             * uptime. We could convert to a (rough) unix epoch time when its received
             * and not timestampe it between file writes. We should also buffer our
             * file writes, rather than open a stream and close it for literally every
             * measurement */
                pw.println(Long.toString(timestamp) + "," + Float.toString(x) +
                        "," + Float.toString(y) + "," + Float.toString(z));

                pw.close();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            /* Inform Android that the file exists, so it can be viewed using USB. */
            MediaScannerConnection.scanFile(SensorMonitorService.this, new String[] {
                            testdata.toString
                                    () },
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                        }
                    });

        }
    }
}
