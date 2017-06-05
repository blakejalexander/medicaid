package teamg.csse4011.medicaid.FallDetection;

import android.content.Context;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Fall-Like Event detection finite state machine.
 *  TODO: Blake - Need to write doc explaining how it works (or how its intended to work) still.
 */
class FallLikeEventDetector {

    private FallDetectionService fallDetectionService;
    private final String TAG = this.getClass().getSimpleName();

    /* States. */
    private final int STATE_WAITING_FOR_PEAK = 0;
    private final int STATE_POST_PEAK_EVENT = 1;
    private final int STATE_POST_FALL_EVENT = 2;
    private final int STATE_WAIT_RECOVERY_EVENT = 3;

    /* Acceleration magnitude detection threshold. */
    private final double THRESHOLD_PEAK = 3.0 * SensorManager.GRAVITY_EARTH;

    /* Timeout values of particular states. */
    private final long POST_PEAK_TIMEOUT_MS = 1000;
    private final long POST_FALL_TIMEOUT_MS = 2000;

    /* The oldest data allowed in our data window _before_ our FSM is triggered. */
    private final long WINDOW_ENTRY_MAX_AGE_MS = POST_PEAK_TIMEOUT_MS + POST_FALL_TIMEOUT_MS;

    /* State 'timers', really just a relative timestamp that is checked. */
    private long postPeakTimeStart;
    private long postFallTimeStart;

    /* Current state of the FSM. */
    private int state;

    /* TODO: Blake - write comment explaining what I'm for. */
    private FallLikeEventDataWindow fallLikeEventWindow;


    /* TOOD: FIXME: Blake - note that the "timers" in this FSM assume (heavily) that data is
     * still coming in before the timer expires. If this doesn't happen for some reason then
     * here be dragons also known as bugs. */

    FallLikeEventDetector(FallDetectionService fallDetectionService) {

        this.fallDetectionService = fallDetectionService;

        /* Set the initial FSM state. */
        state = STATE_WAITING_FOR_PEAK;

        /* Reset timer values. */
        postPeakTimeStart = 0;
        postFallTimeStart = 0;

        /* Initialise internal data window. */
        fallLikeEventWindow = new FallLikeEventDataWindow();

    }

    private class FallLikeEventDataWindow extends LinkedHashMap<Long, Double> {

        void removeOlderThanBy(long timestampReference, long maxAge) {

            Iterator<Long> iterator = this.keySet().iterator();

            while (iterator.hasNext()) {

                long key = iterator.next();

                if (timestampReference - key > maxAge) {
                    iterator.remove();
                } else {

                    /* We exploit the fact that LinkedHashMap.keySet is ordered by
                     * insertion to save the cost of iterating through the whole
                     * thing to find state values. The first time we don't find one, we're done.
                     */
                    break;
                }
            }
        }
    }



    public void run(long timestamp, double G) {

        /* Quantise the timestamp into milliseconds to allow easier processing. */
        timestamp = TimeUnit.MILLISECONDS.convert(timestamp, TimeUnit.NANOSECONDS);

        switch (state) {

            /* In this state, we're waiting for an acceleration reading to exceed
             * THRESHOLD_PEAK. If it does not, we stay in this state until it does. */
            case STATE_WAITING_FOR_PEAK: /* FIXME: Aka sampling state*/
                Log.d(TAG, "STATE_WAITING_FOR_PEAK");

                if (G >= THRESHOLD_PEAK) {

                    /* Set the the next state's timer. */
                    postPeakTimeStart = timestamp;
                    state = STATE_POST_PEAK_EVENT;

                    /* Remove any entires older than WINDOW_ENTRY_MAX_AGE_MS from timestamp. */
                    fallLikeEventWindow.removeOlderThanBy(timestamp, WINDOW_ENTRY_MAX_AGE_MS);


                } else {
                    state = STATE_WAITING_FOR_PEAK;
                }

                fallLikeEventWindow.put(timestamp, G);
                break;

            case STATE_POST_PEAK_EVENT:

                Log.d(TAG, "STATE_POST_PEAK_EVENT");

                fallLikeEventWindow.put(timestamp, G);

                /* Check if timer expired. If so, the fall has finished, go to the post fall
                 * state on the next clock. */
                if (timestamp - postPeakTimeStart > POST_PEAK_TIMEOUT_MS) {
                    postFallTimeStart = timestamp;
                    state = STATE_POST_FALL_EVENT;
                    break;
                }

                /* If we detect ANOTHER acceleration peak, we reset the timer and reenter this
                 * state as we entered from STATE_WAITING_FOR_PEAK as in the first time. */
                if (G >= THRESHOLD_PEAK) {

                    /* Trim current window progress. */
                    fallLikeEventWindow.removeOlderThanBy(timestamp, WINDOW_ENTRY_MAX_AGE_MS);

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

                /* If our timer has expired. It's time to state transition out. */
                if (timestamp - postFallTimeStart > POST_FALL_TIMEOUT_MS) {
                    state = STATE_WAIT_RECOVERY_EVENT;
                    break;
                }

                /* Go back to the post-peak detection event state. Restart the timer. */
                if (G >= THRESHOLD_PEAK) {

                    /* Trim current window progress. */
                    fallLikeEventWindow.removeOlderThanBy(timestamp, WINDOW_ENTRY_MAX_AGE_MS);
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
                        wind = new File(new File(fallDetectionService.filepath), String.format(fmt, i));
                        if (!wind.exists()) {
                            break;
                        }
                    }

                    /* DEBUG Code: Fall occured, let developer know via feedback. */
                    Vibrator v = (Vibrator) fallDetectionService.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
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
                    MediaScannerConnection.scanFile(fallDetectionService, new String[] {
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
