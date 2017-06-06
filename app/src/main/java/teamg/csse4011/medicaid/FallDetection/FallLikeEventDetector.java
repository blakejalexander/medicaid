package teamg.csse4011.medicaid.FallDetection;

import android.content.Context;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.media.Ringtone;
import android.media.RingtoneManager;
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

    /* The oldest data allowed in our (raw) data window _before_ our FSM is triggered. */
    private final long WINDOW_ENTRY_MAX_AGE_MS = POST_PEAK_TIMEOUT_MS + POST_FALL_TIMEOUT_MS;

    /* */
    private final long PRE_IMPACT_JUST_BEFORE_MS = 500;


    /* State 'timers', really just a relative timestamp that is checked. */
    private long postPeakTimeStart;
    private long postFallTimeStart;

    /* Current state of the FSM. */
    private int state;

    /* TODO: Blake - write comment explaining what I'm for. */
    private FallLikeEventDataWindow fallLikeEventWindow;

    /* Impact start and end timestamps. */
    private long impactStart;
    private long impactEnd;

    /* Feature indexes. TODO: Blake - explain me. */
    public final int INDEX_IMPACT_DURATION = 0;
    public final int INDEX_PREFALL_MINIMUM_DIP = 1;
    public final int INDEX_IMPACT_VIOLENCE = 2;


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

    /**
     * Fall-like event data window.
     */
    public class FallLikeEventDataWindow extends LinkedHashMap<Long, Double> implements Cloneable {

        public final int FEATURE_COUNT = 3;

        public double[] features = { 0, 0, 0};
//        public ArrayList<Double> features;

        public FallLikeEventDataWindow() {
            super();
        }

        /* TODO: I have a feeling that I can't verify that this method takes too long. Falls seem
         * to have a ~30-100ms sampling void at the first peak and the next reading? */
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

        /* Remove any entries which are newer (numerically larger) than a certain timestamp. */
        void removeNewerThan(long timestampReference) {

            Iterator<Long> iterator = this.keySet().iterator();

            while (iterator.hasNext()) {

                long key = iterator.next();

                if (key > timestampReference) {
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

        /**
         * shallow clone implementation, returned data is in insertion order of this instance.
         * @return the shallow cloned data
         */
        public FallLikeEventDataWindow clone() {
            /* We implement this by manually going through this and putting the data in a new
             * instance. This isn't the ideal solution but we don't really care.
             * TODO: Care.
             */

            FallLikeEventDataWindow retval = new FallLikeEventDataWindow();

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = iterator.next();
                retval.put((Long)entry.getKey(), (Double)entry.getValue());
            }

            return retval;
        }

        /**
         * Returns a key, value pair (Map.Entry class) with the smallest value
         * @return the entry corresponding to the minimum
         */
        public Map.Entry<Long, Double> getMinValue() {

            Map.Entry<Long, Double> min = null;

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<Long, Double> entry = iterator.next();
                if (min == null || min.getValue() > entry.getValue() ) {
                    min = entry;
                }
            }

            return min;
        }

        /**
         * Iterates through the entries and counts how many have a value within a given range.
         * Set lower to -1 * Double.MIN_VALUE to have no lower bound. Set upper to Double.MAX_VALUE
         * to have no upper bound. If both bounds are set to those values, this call is equivalent
         * to size()
         * @param lower lower bound
         * @param upper upper bound
         * @return
         */
        public int getNumEntriesInRange(double lower, double upper) {

            if (lower == -1 * Double.MIN_VALUE && upper == Double.MAX_VALUE) {
                return this.size();
            }

            int count = 0;

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<Long, Double> entry = iterator.next();

                if (entry.getValue() >= lower && entry.getValue() <= upper) {
                    count++;
                }
            }

            return count;
        }
    }



    public void run(long timestamp, double G) {

        /* Quantise the timestamp into milliseconds to allow easier processing. */
        timestamp = TimeUnit.MILLISECONDS.convert(timestamp, TimeUnit.NANOSECONDS);

        switch (state) {

            /* In this state, we're waiting for an acceleration reading to exceed
             * THRESHOLD_PEAK. If it does not, we stay in this state until it does. */
            case STATE_WAITING_FOR_PEAK: /* FIXME: Aka sampling state*/

                if (G >= THRESHOLD_PEAK) {

                    /* Set the the next state's timer. */
                    postPeakTimeStart = timestamp;
                    state = STATE_POST_PEAK_EVENT;

                    /* Set the impact start timestamp for later use. */
                    impactStart = timestamp;

                    /* Remove any entires older than WINDOW_ENTRY_MAX_AGE_MS from timestamp. */
                    fallLikeEventWindow.removeOlderThanBy(timestamp, WINDOW_ENTRY_MAX_AGE_MS);

                } else {
                    state = STATE_WAITING_FOR_PEAK;
                }

                fallLikeEventWindow.put(timestamp, G);
                break;

            /* This state represents the time we wait for the impact event to end. */
            case STATE_POST_PEAK_EVENT:

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

                    /* Set the impact start timestamp for later use. */
                    impactStart = timestamp;

                    break;

                /* Otherwise, if the acceleration is below the threshold, but above half the
                 * threshold we set the end of impact timestamp to the current value. This may
                 * get continually updated as we add to the fall window. The final value is
                 * correct, the intermediates, are not. */
                } else if (G >= THRESHOLD_PEAK / 2.0) {
                    impactEnd = timestamp;
                }

                /* Otherwise, we're still in the post-peak state, we can only exit this state
                 * if the timer expires. */
                state = STATE_POST_PEAK_EVENT;
                break;

            case STATE_POST_FALL_EVENT:

                fallLikeEventWindow.put(timestamp, G);

                /* If our timer has expired. It's time to state transition out. */
                if (timestamp - postFallTimeStart > POST_FALL_TIMEOUT_MS) {
                    state = STATE_WAIT_RECOVERY_EVENT;
                    break;
                }

                /* Go back to the post-peak detection event state. Restart the timer. */
                if (G >= THRESHOLD_PEAK) {

                    /* Trim current window progress. */
                    fallLikeEventWindow.removeOlderThanBy(timestamp, WINDOW_ENTRY_MAX_AGE_MS);

                    postPeakTimeStart = timestamp;
                    state = STATE_POST_PEAK_EVENT;

                    /* Set the impact start timestamp for later use. */
                    impactStart = timestamp;

                    break;
                }

                break;

            case STATE_WAIT_RECOVERY_EVENT:

                /* Compute the impact duration, that is, the time from the detection peak till
                 * they settle. */
                fallLikeEventWindow.features[INDEX_IMPACT_DURATION] = impactEnd - impactStart;

                /* Compute the pre-fall phase dip magnitude, that is, the magnitude of the lowest
                 * acceleration value within the subwindow just before the impactStart timestamp and
                 * the impactEnd timestamp. */
                FallLikeEventDataWindow subset = fallLikeEventWindow.clone();
                subset.removeOlderThanBy(impactStart - PRE_IMPACT_JUST_BEFORE_MS, 0);
                subset.removeNewerThan(impactEnd);
                Map.Entry<Long, Double> minPreFall = subset.getMinValue();

                fallLikeEventWindow.features[INDEX_PREFALL_MINIMUM_DIP] = minPreFall.getValue();

                /* Compute the 'impact violence' value. The impact violence is number of values
                 * that are NOT within a +- range of 20% the value of GRAVITY_EARTH.
                 * Originally I thought of determining how many significant transitions across
                 * GRAVITY_EARTH occur (ie. bias crossing). This is a "kind of like that but not
                 * really" approach
                 */
                subset.removeOlderThanBy(impactStart, 0); /* Clobber the previous set, save time. */
                double num = subset.size() - subset.getNumEntriesInRange(
                        0.8 * SensorManager.GRAVITY_EARTH,
                        1.2 * SensorManager.GRAVITY_EARTH);
                fallLikeEventWindow.features[INDEX_IMPACT_VIOLENCE] = num / (double)subset.size();



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

                    try {
                        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        Ringtone r = RingtoneManager.getRingtone(
                                this.fallDetectionService.getApplicationContext(),
                                notification);
                        r.play();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

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



                    /* Clear the entire window now that we're done with it. */
                    this.fallLikeEventWindow.clear();

                    break;
                }

                break;
        }
    }
}
