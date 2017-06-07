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
import java.io.FileNotFoundException;
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
public class FallLikeEventDetector {

    /* A helpful alias. */
    private final double GRAVITY_EARTH = SensorManager.GRAVITY_EARTH;

    private FallDetectionService fallDetectionService;
    private final String TAG = this.getClass().getSimpleName();

    /* States. */
    private final int STATE_WAITING_FOR_PEAK = 0;
    private final int STATE_POST_PEAK_EVENT = 1;
    private final int STATE_POST_FALL_EVENT = 2;
    private final int STATE_EVENT_FINISHED = 3;

    /* Acceleration magnitude detection threshold. */
    private final double THRESHOLD_PEAK = 3.0 * GRAVITY_EARTH;

    /* Timeout values of particular states. */
    private final long POST_PEAK_TIMEOUT_MS = 1000;
    private final long POST_FALL_TIMEOUT_MS = 2000;

    /* The oldest data allowed in our (raw) data window _before_ our FSM is triggered. */
    private final long WINDOW_ENTRY_MAX_AGE_MS = POST_PEAK_TIMEOUT_MS + POST_FALL_TIMEOUT_MS;

    /* This constant is used to define the boundary to search for the pre-impact phase. */
    private final long PRE_IMPACT_JUST_BEFORE_MS = 500;

    /* The time to look back from the end of an impact to find the start of it. */
    private final long IMPACT_END_LOOKBACK_MS = POST_PEAK_TIMEOUT_MS + PRE_IMPACT_JUST_BEFORE_MS;

    /* State 'timers', really just a relative timestamp that is checked. */
    private long postPeakTimeStart;
    private long postFallTimeStart;

    /* Current state of the FSM. */
    private int state;

    /* TODO: Blake - write comment explaining what I'm for. */
    private FallLikeEventDataWindow fallLikeEventWindow;

    /* Impact start and end timestamps. */
    private long triggerPeakTime;
    private long impactStart;
    private long impactEnd;

    /* The last reading by the FSM. Used for edge cases. */
    private long lastReadingTimestamp;

    /* Feature indexes. TODO: Blake - explain me. */
    public final int INDEX_IMPACT_DURATION = 0;
    public final int INDEX_PREFALL_MINIMUM_DIP = 1;
    public final int INDEX_IMPACT_VIOLENCE = 2;
    public final int INDEX_IMPACT_AVERAGE_VALUE = 3;


    /* TOOD: FIXME: Blake - note that the "timers" in this FSM assume (heavily) that data is
     * still coming in before the timer expires. If this doesn't happen for some reason then
     * here be dragons also known as bugs. */

    public FallLikeEventDetector(FallDetectionService fallDetectionService) {

        this.fallDetectionService = fallDetectionService;

        /* Set the initial FSM state. */
        state = STATE_WAITING_FOR_PEAK;

        /* Reset timer values. */
        postPeakTimeStart = 0;
        postFallTimeStart = 0;

        /* Reset values. */
        impactStart = 0;
        impactEnd = 0;
        triggerPeakTime = 0;
        lastReadingTimestamp = 0;

        /* Initialise internal data window. */
        fallLikeEventWindow = new FallLikeEventDataWindow();

    }

    /**
     * Storage class, containing features extracted from a fall-like event data window.
     */
    public class FallLikeEventFeatures {

        public double impactDuration;
        //public double prefallDuration; /* TODO: Get working. */
        //public double prefallMinimum;
        public double impactViolence;
        public double impactAverage;
        public double postImpactAverage;

        /**
         * Initialises the storage class by setting the public feature values. Once initialised,
         * the features should be accessed directly via the public double primitives. See
         * extractFeatures for specifics as to the implementation of feature extraction.
         * @param window The data window to extract features from.
         */
        public FallLikeEventFeatures(FallLikeEventDataWindow window) {
            extractFeatures(window);
        }

        /**
         * Feature extraction. Takes a data window and sets the private values of this class
         * (which correspond to features). Note that every feature will be populated. This function
         * assumed that the window is well formed. This assumption is met via
         * the FallLikeEventDetector state machine, which ensures the provided window is suitable
         * for feature extraction.
         *
         * The following features are extracted:
         *  impact duration     - defined as the time difference between the last value above half
         *                        the PEAK_THRESHOLD value and the first value at or above half the
         *                        PEAK_THRESHOLD value after a dip of 70% GRAVITY_EARTH. If the
         *                        later value cannot be found, the impact duration will be the time
         *                        from the trigger peak until the aforementioned end value.
         *
         *  prefall duration    - defined as the time difference between the dip before the
         *                        impact start time (above) and the last value above 90%
         *                        GRAVITY_EARTH
         *                        TODO: FIXME: GET ME WORKING, IT DOESN'T ATM
         *
         *
         *  prefall minimum     - the minimum value within the aforementioned prefall duration
         *                        TODO: FIXME: ME TOO
         *
         *  impact violence     - defined as the number of values not within 20% of GRAVITY_EARTH
         *                        divided by the total amount of values.
         *
         *  impact average      - average of all values within the impact phase
         *
         *  post impact average - average of all values after the impact phase, ie. everything after
         *                        the impact duration.
         *
         *
         * @param window The data window to extract features from.
         */
        private void extractFeatures(FallLikeEventDataWindow window) {

            /* Attempt to extract the "real" impact time by analyzing the subwindow in the region
             * [ impactEnd - justBeforeIt , triggerPeakTime ]. The real impact time is determined by
             * finding the 'first' dip below 70% of GRAVITY_EARTH and then the next measurement of
             * at least half the trigger peak threshold, this measurement is the impact start,
             * immediately after free fall. If the impact start event cant be found, it is 'blindly'
             * set to the triggerPeakTime value.
             */
            FallLikeEventDataWindow subset = window.clone();
            subset.removeOlderThan(impactEnd - IMPACT_END_LOOKBACK_MS);
            subset.removeNewerThan(triggerPeakTime);

            Map.Entry<Long, Double> dip = subset.getFirstEntryLt(0.7 * GRAVITY_EARTH);
            if (dip == null) {

                impactStart = triggerPeakTime;
            } else {

                subset.removeOlderThan(dip.getKey());
                Map.Entry<Long, Double> start = subset.getFirstEntryGt(THRESHOLD_PEAK / 2.0);
                if (start == null) {
                    impactStart = triggerPeakTime;
                } else {
                    impactStart = start.getKey();
                }
            }
            this.impactDuration = impactEnd - impactStart;



            /* Compute the 'impact violence' value. The impact violence is number of values
             * that are NOT within a +- range of 20% the value of GRAVITY_EARTH. This is
             * determined using the "impact" subwindow.
             * Originally I thought of determining how many significant transitions across
             * GRAVITY_EARTH occur (ie. bias crossing). This is a "kind of like that but not really"
             * alternative approach.
             */
            FallLikeEventDataWindow impactWindow = window.clone();
            impactWindow.removeOlderThan(impactStart);
            impactWindow.removeNewerThan(impactEnd);
            double num = impactWindow.size() - impactWindow.getNumEntriesInRange(
                    0.8 * GRAVITY_EARTH,
                    1.2 * GRAVITY_EARTH);
            this.impactViolence = num / (double)impactWindow.size();



            /* Extract the average of all events in the impact event region (before post-fall). */
            double impactSum = 0;
            for (double value : impactWindow.values()) {
                impactSum = impactSum + value;
            }
            this.impactAverage = impactSum / impactWindow.size();



            /* Extract the average of all activity after the impact event. */
            FallLikeEventDataWindow postImpactWindow = window.clone();
            postImpactWindow.removeOlderThan(impactEnd);

            double postImpactSum = 0;
            for (double value : postImpactWindow.values()) {
                postImpactSum = postImpactSum + value;
            }
            this.postImpactAverage = postImpactSum / postImpactWindow.size();

        }
    }

    /**
     * Fall-like event data window.
     */
    public class FallLikeEventDataWindow extends LinkedHashMap<Long, Double> implements Cloneable {

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

        /**
         * Equivalent to removeOlderThanBy with maxAge = 0. Removes anything older than the
         * reference value.
         * @param timestampReference reference value
         */
        void removeOlderThan(long timestampReference) {
            removeOlderThanBy(timestampReference, 0);
        }

        /* Remove any entries which are newer (numerically larger) than a certain timestamp. */
        void removeNewerThan(long timestampReference) {
            /* TODO: Can improve performance by constructing a new window and adding to it
             * whenever the if statement is false, breaking when its true. The new window can be
             * set to this. whatever was in this before that point is trashed. */

            Iterator<Long> iterator = this.keySet().iterator();

            while (iterator.hasNext()) {

                long key = iterator.next();

                if (key > timestampReference) {
                    iterator.remove();
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

        /**
         * Returns the first Entry object with a value above a certain threshold.
         * @param threshold
         * @return the first entry found with Map.Entry.getValue() > threshold or null if none
         */
        public Entry<Long, Double> getFirstEntryGt(double threshold) {

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<Long, Double> entry = iterator.next();

                if (entry.getValue() > threshold) {
                    return entry;
                }
            }

            return null;
        }

        /**
         * Returns the last Entry object which satisifes the condition .getValue() > threshold
         * @param threshold threshold value
         * @return the last entry greater than the threshold
         */
        public Entry<Long, Double> getLastEntryGt(double threshold) {

            Entry<Long, Double> last = null;

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<Long, Double> entry = iterator.next();

                if (entry.getValue() > threshold) {
                    last = entry;
                }
            }

            return last;
        }

        public Entry<Long, Double> getFirstEntryLt(double threshold) {

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {

                Entry<Long, Double> needle = iterator.next();
                if (needle.getValue() < threshold) {
                    return needle;
                }
            }
            return null;
        }

        public Entry<Long, Double> getLastEntryLt(double threshold) {
            /* TODO: Blake - potential optimisation, iterate in reverse. No default API for it. */

            Entry<Long, Double> last = null;

            Iterator<Entry<Long, Double>> iterator = this.entrySet().iterator();
            while (iterator.hasNext()) {

                Entry<Long, Double> needle = iterator.next();
                if (needle.getValue() < threshold) {
                    last = needle;
                }
            }
            return last;
        }


    }

    private void fsmReset() {

        /* Set the initial FSM state. */
        state = STATE_WAITING_FOR_PEAK;

        /* Reset timer values. */
        postPeakTimeStart = 0;
        postFallTimeStart = 0;

        /* Reset values. */
        impactStart = 0;
        impactEnd = 0;
        triggerPeakTime = 0;
        lastReadingTimestamp = 0;

        /* Initialise internal data window. */
        fallLikeEventWindow = new FallLikeEventDataWindow();
    }

    /**
     * Implementation of finite state machine clock. State transitions occur when a timestamp, G
     * pair are provided, ie. a sample. This function will return null if a fall like event has
     * not been completely identified or will return a feature set that identiifes that event. The
     * raw event window can be obtained - it is an instance variable of this class.
     * @param timestamp timestamp of sample, in nanoseconds.
     * @param G acceleration magnitude, in m/s^2
     * @return null or extracted features
     */
    private FallLikeEventFeatures doClock(long timestamp, double G) {

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

                    /* Set the timestamp of the trigger peak for later use. */
                    triggerPeakTime = timestamp;

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

                    /* Set the timestamp of the trigger peak for later use. */
                    triggerPeakTime = timestamp;

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

                /* FIXME: HACK: Attempting to get around when Android occasionally doesn't sample
                 * for up to 200ms after a impulse (such as hitting the phone). Haven't figured
                 * out why but my current guess is that its something beyond our application layer
                 * control, since Android's sampling rate is 'best effort'. */
                if (lastReadingTimestamp == triggerPeakTime) {
                    impactEnd = timestamp;
                }

                break;

            case STATE_POST_FALL_EVENT:

                /* If our timer has expired. It's time to state transition out. */
                if (timestamp - postFallTimeStart > POST_FALL_TIMEOUT_MS) {
                    state = STATE_EVENT_FINISHED;
                    break;
                }

                fallLikeEventWindow.put(timestamp, G);

                /* Go back to the post-peak detection event state. Restart the timer. */
                if (G >= THRESHOLD_PEAK) {

                    /* Trim current window progress. */
                    fallLikeEventWindow.removeOlderThanBy(timestamp, WINDOW_ENTRY_MAX_AGE_MS);

                    postPeakTimeStart = timestamp;
                    state = STATE_POST_PEAK_EVENT;

                    /* Set the timestamp of the trigger peak for later use. */
                    triggerPeakTime = timestamp;

                    break;
                }

                break;

            case STATE_EVENT_FINISHED:

                /* Reduce the dimensionality of the data by extracting notable features from it. */
                FallLikeEventFeatures features = new FallLikeEventFeatures(fallLikeEventWindow);
                return features;
        }

        /* Store the last time we processed a sample in the FSM. */
        lastReadingTimestamp = timestamp;
        return null;
    }

    /**
     * Clocks the finitie state machine. See doClock for details.
     * @param timestamp
     * @param G
     * @return null or extracted features
     */
    public FallLikeEventFeatures run(long timestamp, double G) {

        FallLikeEventFeatures rv = this.doClock(timestamp, G);

        if (rv != null) {
            this.debugDumpEventData(this.fallLikeEventWindow, rv);
            fsmReset();
        }

        return rv;
    }

    /**
     * Functionally identical to run, however, intended to be used to replay data (as read from a
     * csv file) rather than used 'live'.
     * @param timestamp
     * @param
     * @return null or a extracted feature object.
     */
    public FallLikeEventFeatures replay(long timestamp, double G) {

        /* NOTE: Internal data window can't be accessed when this function returns, whilst
         * strictly an internal detail, keep it in mind if you expect window.csv files to be dumped
         */

        /* NOTE: No need to call debugDumpEventData here, if we're replaying it. Responsibility
        *        of caller to write feature values to a .csv if desired. */

        timestamp = TimeUnit.NANOSECONDS.convert(timestamp, TimeUnit.MILLISECONDS);
        FallLikeEventFeatures features = this.doClock(timestamp, G);
        if (features != null) {
            fsmReset();
        }
        return features; /* Only return features, window will be trashed in fsmReset() */
    }

    /**
     * Dumps window and feature data to flash. Only valid to call when a complete window and
     * corresponding feature set has been obtained. See run or replay.
     * @param window a complete window
     * @param features feature representation of that window
     */
    public void debugDumpEventData(FallLikeEventDataWindow window,
                                    FallLikeEventFeatures features) {

        String windowFileFormat = "window%03d.csv";
        String featureFileFormat = "feature%03d.csv";

        File windF = null;
        File featF = null;

        /* Loop until we find a windowXXX.csv and featureXXX.csv pair of filenames that are free. */
        for (int i = 0; i < 999; i++) {
            windF = new File(new File(fallDetectionService.filepath),
                    String.format(windowFileFormat, i));
            featF = new File(new File(fallDetectionService.filepath),
                    String.format(featureFileFormat, i));

            if (!windF.exists() && !featF.exists()) {
                break;
            }
        }
        /* WARNING: If at this point we are using window999.csv and feature999.csv, they may be
         *          appended to, rather than created. */


        /* Let the developer know an event was captured via haptic feedback. */
        Vibrator v = (Vibrator)fallDetectionService.getApplicationContext().
                getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(1000);

        /* Also let the developer know an event was captured via audio feedback. */
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(
                    this.fallDetectionService.getApplicationContext(),
                    notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }


        /* Write the window data to flash. */
        try {
            FileOutputStream out = new FileOutputStream(windF, true);
            PrintWriter pw = new PrintWriter(out, true);

            for (Map.Entry<Long, Double> entry : window.entrySet()) {
                pw.println(entry.getKey() + "," + entry.getValue());
            }

            pw.close();
            out.close();

        } catch (FileNotFoundException e) {
            /* We already assured this can't happen. */
            e.printStackTrace();
        } catch (IOException e) {
            /* TODO: We don't handle becuase we're lazy. Fix me later. */
        }

        /* Write the feature data for the window to flash. */
        try {
            FileOutputStream out = new FileOutputStream(featF, true);
            PrintWriter pw = new PrintWriter(out, true);

            pw.println(features.impactDuration + "," + features.impactViolence +
                    "," + features.impactAverage + "," + features.postImpactAverage);

            pw.close();
            out.close();

        } catch (FileNotFoundException e) {
            /* We already assured this can't happen. */
            e.printStackTrace();
        } catch (IOException e) {
            /* TODO: We don't handle becuase we're lazy. Fix me later. */
        }

        /* Inform Android that the files exist, so it can be viewed using USB immediately */
        MediaScannerConnection.scanFile(fallDetectionService, new String[] {
                windF.toString() }, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) { /* nothing */ }
        });

        /* Inform Android that the files exist, so it can be viewed using USB immediately */
        MediaScannerConnection.scanFile(fallDetectionService, new String[] {
                featF.toString() }, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) { /* nothing */ }
                });

    }

}
