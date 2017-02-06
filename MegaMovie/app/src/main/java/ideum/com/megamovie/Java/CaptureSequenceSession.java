package ideum.com.megamovie.Java;


import android.location.Location;
import android.os.CountDownTimer;
import android.util.Log;

import java.util.Iterator;
import java.util.Map;

public class CaptureSequenceSession implements MyTimer.MyTimerListener{
    public static final String TAG = "CaptureSequenceSession";
    private static final long TIMER_DURATION = 1000000;
    private static final long TIMER_INTERVAL = 1;
    private CaptureSequence mCaptureSequence;
    private CameraFragment mCameraFragment;
    private LocationProvider mLocationProvider;
    private Map<Long,CaptureSequence.CaptureSettings> timedRequests;

    public CaptureSequenceSession(CameraFragment cameraFragment, CaptureSequence captureSequence, LocationProvider locationProvider) {
        mCameraFragment = cameraFragment;
        mCaptureSequence = captureSequence;
        mLocationProvider = locationProvider;
        timedRequests = captureSequence.getTimedRequests();
    }

    private Long getTime() {
        Location currentLocation = mLocationProvider.getLocation();
        if (currentLocation == null) {
            return null;
        }
        return currentLocation.getTime();
    }

    @Override
    public void onTick() {
        Iterator it = timedRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            Long time = (Long) pair.getKey();
            Long currentTime = getTime();
            if (currentTime == null) {
                continue;
            }
            if (time <= currentTime) {
//                Log.e(TAG,"current: " + String.valueOf(currentTime));
//                Log.e(TAG,"planned: " + String.valueOf(time));
                CaptureSequence.CaptureSettings s = (CaptureSequence.CaptureSettings) pair.getValue();
                mCameraFragment.takePhotoWithSettings(s);
                it.remove();
            }
        }
    }

    public void startSession() {
        MyTimer timer = new MyTimer(this);
        timer.startTicking();
    }

}