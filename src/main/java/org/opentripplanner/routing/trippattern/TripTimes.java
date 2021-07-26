package org.opentripplanner.routing.trippattern;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.model.BookingInfo;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.model.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TripTimes represents the arrival and departure times for a single trip in an Timetable. It is carried
 * along by States when routing to ensure that they have a consistent, fast view of the trip when
 * realtime updates have been applied. All times are expressed as seconds since midnight (as in GTFS).
 */
public class TripTimes implements Serializable, Comparable<TripTimes>, Cloneable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TripTimes.class);

    private int timeShift;

    private final Trip trip;

    // not final because these are set later, after TripTimes construction.
    private int serviceCode = -1;

    /**
     * Both trip_headsign and stop_headsign (per stop on a particular trip) are optional GTFS
     * fields. If the headsigns array is null, we will report the trip_headsign (which may also
     * be null) at every stop on the trip. If all the stop_headsigns are the same as the
     * trip_headsign we may also set the headsigns array to null to save space.
     * Field is private to force use of the getter method which does the necessary fallbacks.
     */
    private final String[] headsigns;

    private final int[] scheduledArrivalTimes;

    private final int[] scheduledDepartureTimes;

    private int[] arrivalTimes;

    private int[] departureTimes;

    private boolean[] recordedStops;

    private boolean[] predictionInaccurateOnStops;

    private List<PickDrop> pickups;

    private List<PickDrop> dropoffs;

    private List<BookingInfo> dropOffBookingInfos;

    private List<BookingInfo> pickupBookingInfos;

    /**
     * These are the GTFS stop sequence numbers, which show the order in which the vehicle visits
     * the stops. Despite the face that the StopPattern or TripPattern enclosing this TripTimes
     * provides an ordered list of Stops, the original stop sequence numbers may still be needed for
     * matching with GTFS-RT update messages. Unfortunately, each individual trip can have totally
     * different sequence numbers for the same stops, so we need to store them at the individual
     * trip level. An effort is made to re-use the sequence number arrays when they are the same
     * across different trips in the same pattern.
     */
    private final int[] originalGtfsStopSequence;

    /**
     * The real-time state of this TripTimes.
     */
    private RealTimeState realTimeState = RealTimeState.SCHEDULED;

    /** A Set of stop indexes that are marked as timepoints in the GTFS input. */
    private final BitSet timepoints;

    /**
     * The provided stopTimes are assumed to be pre-filtered, valid, and monotonically increasing.
     * The non-interpolated stoptimes should already be marked at timepoints by a previous filtering step.
     */
    public TripTimes(final Trip trip, final Collection<StopTime> stopTimes, final Deduplicator deduplicator) {
        this.trip = trip;
        final int nStops = stopTimes.size();
        final int[] departures = new int[nStops];
        final int[] arrivals   = new int[nStops];
        final int[] sequences  = new int[nStops];
        final BitSet timepoints = new BitSet(nStops);
        // Times are always shifted to zero. This is essential for frequencies and deduplication.
        setTimeShift(stopTimes.iterator().next().getArrivalTime());
        final List<PickDrop> pickups   = new ArrayList<>();
        final List<PickDrop> dropoffs   = new ArrayList<>();
        final List<BookingInfo> dropOffBookingInfos = new ArrayList<>();
        final List<BookingInfo> pickupBookingInfos = new ArrayList<>();
        int s = 0;
        for (final StopTime st : stopTimes) {
            departures[s] = st.getDepartureTime() - getTimeShift();
            arrivals[s] = st.getArrivalTime() - getTimeShift();
            sequences[s] = st.getStopSequence();
            timepoints.set(s, st.getTimepoint() == 1);

            pickups.add(st.getPickupType());
            dropoffs.add(st.getDropOffType());
            dropOffBookingInfos.add(st.getDropOffBookingInfo());
            pickupBookingInfos.add(st.getPickupBookingInfo());
            s++;
        }
        this.scheduledDepartureTimes = deduplicator.deduplicateIntArray(departures);
        this.scheduledArrivalTimes = deduplicator.deduplicateIntArray(arrivals);
        this.originalGtfsStopSequence = deduplicator.deduplicateIntArray(sequences);
        this.headsigns = deduplicator.deduplicateStringArray(makeHeadsignsArray(stopTimes));
        this.setPickups(deduplicator.deduplicateImmutableList(PickDrop.class, pickups));
        this.setDropoffs(deduplicator.deduplicateImmutableList(PickDrop.class, dropoffs));
        this.setDropOffBookingInfos(deduplicator.deduplicateImmutableList(BookingInfo.class, dropOffBookingInfos));
        this.setPickupBookingInfos(deduplicator.deduplicateImmutableList(BookingInfo.class, pickupBookingInfos));
        // We set these to null to indicate that this is a non-updated/scheduled TripTimes.
        // We cannot point to the scheduled times because they are shifted, and updated times are not.
        this.setArrivalTimes(null);
        this.setDepartureTimes(null);
        this.setRecordedStops(null);
        this.timepoints = deduplicator.deduplicateBitSet(timepoints);
        LOG.trace("trip {} has timepoint at indexes {}", trip, timepoints);
    }

    /** This copy constructor does not copy the actual times, only the scheduled times. */
    // It might be more maintainable to clone the triptimes then null out the scheduled times.
    // However, we then lose the "final" modifiers on the fields, and the immutability.
    public TripTimes(final TripTimes object) {
        this.trip = object.getTrip();
        this.setServiceCode(object.getServiceCode());
        this.setTimeShift(object.getTimeShift());
        this.headsigns = object.headsigns;
        this.scheduledDepartureTimes = object.getScheduledDepartureTimes();
        this.scheduledArrivalTimes = object.getScheduledArrivalTimes();
        this.originalGtfsStopSequence = object.originalGtfsStopSequence;
        this.timepoints = object.timepoints;
        this.setPickups(object.getPickups());
        this.setDropoffs(object.getDropoffs());
        this.setDropOffBookingInfos(object.getDropOffBookingInfos());
        this.setPickupBookingInfos(object.getPickupBookingInfos());
    }

    public void setRecordedStops(boolean[] recordedStops) {
        this.recordedStops = recordedStops;
    }

    public void setPredictionInaccurateOnStops(boolean[] predictionInaccurateOnStops) {
        this.predictionInaccurateOnStops = predictionInaccurateOnStops;
    }

    public void setPickups(List<PickDrop> pickups) {
        this.pickups = pickups;
    }

    public void setDropoffs(List<PickDrop> dropoffs) {
        this.dropoffs = dropoffs;
    }

    public void setDropOffBookingInfos(
        List<BookingInfo> dropOffBookingInfos
    ) {
        this.dropOffBookingInfos = dropOffBookingInfos;
    }

    public void setPickupBookingInfos(
        List<BookingInfo> pickupBookingInfos
    ) {
        this.pickupBookingInfos = pickupBookingInfos;
    }

    public void setServiceCode(int serviceCode) {
        this.serviceCode = serviceCode;
    }

    public void setArrivalTimes(int[] arrivalTimes) {
        this.arrivalTimes = arrivalTimes;
    }

    public void setDepartureTimes(int[] departureTimes) {
        this.departureTimes = departureTimes;
    }

    public void setTimeShift(int timeShift) {
        this.timeShift = timeShift;
    }

    /**
     * @return either an array of headsigns (one for each stop on this trip) or null if the
     * headsign is the same at all stops (including null) and can be found in the Trip object.
     */
    private String[] makeHeadsignsArray(final Collection<StopTime> stopTimes) {
        final String tripHeadsign = getTrip().getTripHeadsign();
        boolean useStopHeadsigns = false;
        if (tripHeadsign == null) {
            useStopHeadsigns = true;
        } else {
            for (final StopTime st : stopTimes) {
                if ( ! (tripHeadsign.equals(st.getStopHeadsign()))) {
                    useStopHeadsigns = true;
                    break;
                }
            }
        }
        if (!useStopHeadsigns) {
            return null; //defer to trip_headsign
        }
        boolean allNull = true;
        int i = 0;
        final String[] hs = new String[stopTimes.size()];
        for (final StopTime st : stopTimes) {
            final String headsign = st.getStopHeadsign();
            hs[i++] = headsign;
            if (headsign != null) allNull = false;
        }
        if (allNull) {
            return null;
        } else {
            return hs;
        }
    }

    /**
     * Trips may also have null headsigns, in which case we should fall back on a Timetable or
     * Pattern-level headsign. Such a string will be available when we give TripPatterns or
     * StopPatterns unique human readable route variant names, but a TripTimes currently does not
     * have a pointer to its enclosing timetable or pattern.
     */
    public String getHeadsign(final int stop) {
        if (headsigns == null) {
            return getTrip().getTripHeadsign();
        } else {
            return headsigns[stop];
        }
    }

    /** @return the time in seconds after midnight that the vehicle arrives at the stop. */
    public int getScheduledArrivalTime(final int stop) {
        return getScheduledArrivalTimes()[stop] + getTimeShift();
    }

    /** @return the amount of time in seconds that the vehicle waits at the stop. */
    public int getScheduledDepartureTime(final int stop) {
        return getScheduledDepartureTimes()[stop] + getTimeShift();
    }

    /**
     * Return an integer witch can be used to sort TripTimes in order of departure/arrivals.
     * <p>
     * This sorted trip times is used to search for trips. OTP assume one trip do NOT pass another
     * trip down the line.
     */
    public int sortIndex() {
        return getArrivalTime(0);
    }

    /** @return the time in seconds after midnight that the vehicle arrives at the stop. */
    public int getArrivalTime(final int stop) {
        if (getArrivalTimes() == null) { return getScheduledArrivalTime(stop); }
        else return getArrivalTimes()[stop]; // updated times are not time shifted.
    }

    /** @return the amount of time in seconds that the vehicle waits at the stop. */
    public int getDepartureTime(final int stop) {
        if (getDepartureTimes() == null) { return getScheduledDepartureTime(stop); }
        else return getDepartureTimes()[stop]; // updated times are not time shifted.
    }

    /** @return the amount of time in seconds that the vehicle waits at the stop. */
    public int getDwellTime(final int stop) {
        // timeShift is not relevant since this involves updated times and is relative.
        return getDepartureTime(stop) - getArrivalTime(stop);
    }

    /** @return the amount of time in seconds that the vehicle takes to reach the following stop. */
    public int getRunningTime(final int stop) {
        // timeShift is not relevant since this involves updated times and is relative.
        return getArrivalTime(stop + 1) - getDepartureTime(stop);
    }

    /** @return the difference between the scheduled and actual arrival times at this stop. */
    public int getArrivalDelay(final int stop) {
        return getArrivalTime(stop) - (getScheduledArrivalTimes()[stop] + getTimeShift());
    }

    /** @return the difference between the scheduled and actual departure times at this stop. */
    public int getDepartureDelay(final int stop) {
        return getDepartureTime(stop) - (getScheduledDepartureTimes()[stop] + getTimeShift());
    }

    public void setRecorded(int stop, boolean recorded) {
        checkCreateTimesArrays();
        getRecordedStops()[stop] = recorded;
    }

    // TODO OTP2 - Unused, but will be used by Transmodel API
    public boolean isRecordedStop(int stop) {
        if (getRecordedStops() == null) {
            return false;
        }
        return getRecordedStops()[stop];
    }

    /**
     * Cancels both pickup and dropoff for the specified stop
     */
    public void cancelStop(int stop) {
        cancelDropOffForStop(stop);
        cancelPickupForStop(stop);
    }

    // TODO OTP2 - Unused, but will be used by Transmodel API

    /**
     * Returns true if both pickup and dropoff is cancelled for the specified stop
     */
    public boolean isCancelledStop(int stop) {
        return dropoffs.get(stop) == PickDrop.CANCELLED && pickups.get(stop) == PickDrop.CANCELLED;
    }

    //Is prediction for single stop inaccurate
    public void setPredictionInaccurate(int stop, boolean predictionInaccurate) {
        checkCreateTimesArrays();
        getPredictionInaccurateOnStops()[stop] = predictionInaccurate;
    }

    // TODO OTP2 - Unused, but will be used by Transmodel API
    public boolean isPredictionInaccurate(int stop) {
        if (getPredictionInaccurateOnStops() == null) {
            return false;
        }
        return getPredictionInaccurateOnStops()[stop];
    }

    public void cancelPickupForStop(int stop) {
        checkCreateTimesArrays();
        pickups.set(stop, PickDrop.CANCELLED);
    }

    // TODO OTP2 - Unused, but will be used by Transmodel API
    public PickDrop getPickupType(int stop) {
        return getPickups().get(stop);
    }

    public void cancelDropOffForStop(int stop) {
        checkCreateTimesArrays();
        dropoffs.set(stop, PickDrop.CANCELLED);
    }

    // TODO OTP2 - Unused, but will be used by Transmodel API
    public PickDrop getDropoffType(int stop) {
        return getDropoffs().get(stop);
    }

    public BookingInfo getDropOffBookingInfo(int stop) {
        return getDropOffBookingInfos().get(stop);
    }

    public BookingInfo getPickupBookingInfo(int stop) {
        return getPickupBookingInfos().get(stop);
    }

    /**
     * @return true if this TripTimes represents an unmodified, scheduled trip from a published
     *         timetable or false if it is a updated, cancelled, or otherwise modified one. This
     *         method differs from {@link #getRealTimeState()} in that it checks whether real-time
     *         information is actually available in this TripTimes.
     */
    public boolean isScheduled() {
        return getDepartureTimes() == null && getArrivalTimes() == null;
    }

    /**
     * @return true if this TripTimes is canceled
     */
    public boolean isCanceled() {
        return realTimeState == RealTimeState.CANCELED;
    }

    /**
     * @return the real-time state of this TripTimes
     */
    public RealTimeState getRealTimeState() {
        return realTimeState;
    }

    public void setRealTimeState(final RealTimeState realTimeState) {
        this.realTimeState = realTimeState;
    }

    /** Used in debugging / dumping times. */
    public static String formatSeconds(int s) {
        int m = s / 60;
        s = s % 60;
        final int h = m / 60;
        m = m % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * When creating a scheduled TripTimes or wrapping it in updates, we could potentially imply
     * negative running or dwell times. We really don't want those being used in routing.
     * This method check that all times are increasing, and logs warnings if this is not the case.
     * @return whether the times were found to be increasing.
     */
    public boolean timesIncreasing() {
        final int nStops = getScheduledArrivalTimes().length;
        int prevDep = -1;
        for (int s = 0; s < nStops; s++) {
            final int arr = getArrivalTime(s);
            final int dep = getDepartureTime(s);

            if (dep < arr) {
                LOG.warn("Negative dwell time in TripTimes at stop index {}.", s);
                return false;
            }
            if (prevDep > arr) {
                LOG.warn("Negative running time in TripTimes after stop index {}.", s);
                return false;
            }
            prevDep = dep;
        }
        return true;
    }

    /** Cancel this entire trip */
    public void cancel() {
        realTimeState = RealTimeState.CANCELED;
    }

    public void updateDepartureTime(final int stop, final int time) {
        checkCreateTimesArrays();
        getDepartureTimes()[stop] = time;
    }

    public void updateDepartureDelay(final int stop, final int delay) {
        checkCreateTimesArrays();
        getDepartureTimes()[stop] = getScheduledDepartureTimes()[stop] + getTimeShift() + delay;
    }

    public void updateArrivalTime(final int stop, final int time) {
        checkCreateTimesArrays();
        getArrivalTimes()[stop] = time;
    }

    public void updateArrivalDelay(final int stop, final int delay) {
        checkCreateTimesArrays();
        getArrivalTimes()[stop] = getScheduledArrivalTimes()[stop] + getTimeShift() + delay;
    }

    /**
     * If they don't already exist, create arrays for updated arrival and departure times
     * that are just time-shifted copies of the zero-based scheduled departure times.
     */
    private void checkCreateTimesArrays() {
        if (getArrivalTimes() == null) {
            setArrivalTimes(Arrays.copyOf(getScheduledArrivalTimes(), getScheduledArrivalTimes().length));
            setDepartureTimes(Arrays.copyOf(getScheduledDepartureTimes(), getScheduledDepartureTimes().length));
            setRecordedStops(new boolean[getArrivalTimes().length]);
            setPredictionInaccurateOnStops(new boolean[getArrivalTimes().length]);
            for (int i = 0; i < getArrivalTimes().length; i++) {
                getArrivalTimes()[i] += getTimeShift();
                getDepartureTimes()[i] += getTimeShift();
                getRecordedStops()[i] = false;
                getPredictionInaccurateOnStops()[i] = false;
            }

            // Update the real-time state
            realTimeState = RealTimeState.UPDATED;
        }
    }

    public int getNumStops () {
        return getScheduledArrivalTimes().length;
    }

    /** Sort TripTimes based on first departure time. */
    @Override
    public int compareTo(final TripTimes other) {
        return this.getDepartureTime(0) - other.getDepartureTime(0);
    }

    @Override
    public TripTimes clone() {
        TripTimes ret = null;
        try {
            ret = (TripTimes) super.clone();
        } catch (final CloneNotSupportedException e) {
            LOG.error("This is not happening.");
        }
        return ret;
    }

   /**
    * Returns a time-shifted copy of this TripTimes in which the vehicle passes the given stop
    * index (not stop sequence number) at the given time. We only have a mechanism to shift the
    * scheduled stoptimes, not the real-time stoptimes. Therefore, this only works on trips
    * without updates for now (frequency trips don't have updates).
    */
    public TripTimes timeShift (final int stop, final int time, final boolean depart) {
        if (getArrivalTimes() != null || getDepartureTimes() != null) { return null; }
        final TripTimes shifted = this.clone();
        // Adjust 0-based times to match desired stoptime.
        final int shift = time - (depart ? getDepartureTime(stop) : getArrivalTime(stop));
        shifted.setTimeShift(shifted.getTimeShift() + shift); // existing shift should usually (always?) be 0 on freqs
        return shifted;
    }

    /** Just to create uniform getter-syntax across the whole public interface of TripTimes. */
    public int getOriginalGtfsStopSequence(final int stop) {
        return originalGtfsStopSequence[stop];
    }

    /** @return whether or not stopIndex is considered a timepoint in this TripTimes. */
    public boolean isTimepoint(final int stopIndex) {
        return timepoints.get(stopIndex);
    }

    /**
     * Hash the scheduled arrival/departure times. Used in creating stable IDs for trips across GTFS feed versions.
     * Use hops rather than stops because:
     * a) arrival at stop zero and departure from last stop are irrelevant
     * b) this hash function needs to stay stable when users switch from 0.10.x to 1.0
     */
    public HashCode semanticHash(final HashFunction hashFunction) {
        final Hasher hasher = hashFunction.newHasher();
        for (int hop = 0; hop < getNumStops() - 1; hop++) {
            hasher.putInt(getScheduledDepartureTime(hop));
            hasher.putInt(getScheduledArrivalTime(hop + 1));
        }
        return hasher.hash();
    }

    /**
     * TODO OTP2 - This needs redesign and a bit more analyzes
     *
     * Flag to indicate that the stop has been passed without removing arrival/departure-times - i.e. "estimates" are
     * actual times, no longer estimates.
     *
     * Non-final to allow updates.
     */
    public boolean[] getRecordedStops() {
        return recordedStops;
    }

    /**
     * TODO OTP2 - This needs redesign and a bit more analyzes
     *
     * Flag tho indicate inaccurate predictions on each stop. Non-final to allow updates, transient for backwards graph-compatibility.
     */
    public boolean[] getPredictionInaccurateOnStops() {
        return predictionInaccurateOnStops;
    }

    /**
     * TODO OTP2 - This needs redesign and a bit more analyzes
     *
     * Flag tho indicate cancellations on each stop. Non-final to allow updates.
     */
    public List<PickDrop> getPickups() {
        return pickups;
    }

    /**
     * TODO OTP2 - This needs redesign and a bit more analyzes
     *
     * Flag tho indicate cancellations on each stop. Non-final to allow updates.
     */
    public List<PickDrop> getDropoffs() {
        return dropoffs;
    }

    public List<BookingInfo> getDropOffBookingInfos() {
        return dropOffBookingInfos;
    }

    public List<BookingInfo> getPickupBookingInfos() {
        return pickupBookingInfos;
    }

    /** The code for the service on which this trip runs. For departure search optimizations. */
    public int getServiceCode() {
        return serviceCode;
    }

    /**
     * The time in seconds after midnight at which the vehicle should arrive at each stop according
     * to the original schedule.
     */
    public int[] getScheduledArrivalTimes() {
        return scheduledArrivalTimes;
    }

    /**
     * The time in seconds after midnight at which the vehicle should leave each stop according
     * to the original schedule.
     */
    public int[] getScheduledDepartureTimes() {
        return scheduledDepartureTimes;
    }

    /**
     * The time in seconds after midnight at which the vehicle arrives at each stop, accounting for
     * any real-time updates. Non-final to allow updates.
     */
    public int[] getArrivalTimes() {
        return arrivalTimes;
    }

    /**
     * The time in seconds after midnight at which the vehicle leaves each stop, accounting for
     * any real-time updates. Non-final to allow updates.
     */
    public int[] getDepartureTimes() {
        return departureTimes;
    }

    /**
     * This allows re-using the same scheduled arrival and departure time arrays for many
     * different TripTimes. It is also used in materializing frequency-based TripTimes.
     */
    public int getTimeShift() {
        return timeShift;
    }

    /** The trips whose arrivals and departures are represented by this TripTimes */
    public Trip getTrip() {
        return trip;
    }
}
