
package ideum.com.megamovie.Java;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.model.LatLng;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import ideum.com.megamovie.R;

public class CaptureActivity extends AppCompatActivity
        implements CameraFragment.CaptureListener,
        CaptureSequenceSession.CameraController,
        LocationProvider,
        LocationListener{

    private final static String TAG = "CaptureActivity";
    private GPSFragment mGPSFragment;
    private CameraFragment mCameraFragment;
    private TextView captureTextView;
    private Integer totalCaptures;
    private CaptureSequenceSession session;
    private static final String[] SETTINGS_PERMISSIONS = {Manifest.permission.WRITE_SETTINGS};
    private int initialBrightness;
    private ContentResolver mContentResolver;
    private static final int SCREEN_BRIGHTNESS_LOW = 5;
    private Location mLocation;
    private static final boolean SHOULD_DIM_SCREEN = false;

    @Override
    public void onCapture() {
        updateCaptureTextView();
    }

    @Override
    public void takePhotoWithSettings(CaptureSequence.CaptureSettings settings) {
        mCameraFragment.takePhotoWithSettings(settings);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        // Keep phone from going to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mContentResolver = getContentResolver();

        // Initial view showing number of completed captures
        captureTextView = (TextView) findViewById(R.id.capture_text);

        /* Add Gps */
        mGPSFragment = new GPSFragment();
        getFragmentManager().beginTransaction().add(
                android.R.id.content, mGPSFragment).commit();
        mGPSFragment.addLocationListener(this);

        /* Add Camera Fragment */
        mCameraFragment = new CameraFragment();
        getFragmentManager().beginTransaction().add(
                android.R.id.content, mCameraFragment).commit();
        mCameraFragment.setLocationProvider(mGPSFragment);
        mCameraFragment.addCaptureListener(this);

    }

    private boolean checkSystemWritePermissions() {
        boolean permission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permission = Settings.System.canWrite(this);
        } else {
            permission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_DENIED;
        }
        return permission;
    }

    private void setUpCaptureSequenceSession() {
        Resources resources = getResources();
        ConfigParser parser = new ConfigParser(resources);
        try {
            EclipseTimeCalculator calculator = new EclipseTimeCalculator(getApplicationContext(),this);
            EclipseCaptureSequenceBuilder builder = new EclipseCaptureSequenceBuilder(this, parser, calculator);
            CaptureSequence sequence = builder.buildSequence();
            session = new CaptureSequenceSession(sequence, this, this);
            session.startSession();
            totalCaptures = sequence.getRequestQueue().size();
            updateCaptureTextView();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
            try {
                initialBrightness = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_BRIGHTNESS);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }

        if (SHOULD_DIM_SCREEN) {
            setScreenBrightness(SCREEN_BRIGHTNESS_LOW);
        }
    }

    @Override
    protected void onPause() {
        if (session != null) {
            session.cancelSession();
            session = null;
        }
        if (SHOULD_DIM_SCREEN) {
            setScreenBrightness(initialBrightness);
        }
        super.onPause();
    }


    @Override
    public Location getLocation() {
        return mLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mLocation == null) {
            mLocation = location;
            setUpCaptureSequenceSession();
        }
    }

    private void setScreenBrightness(int brightness) {
        if (!checkSystemWritePermissions()) {
            return;
        }
        Settings.System.putInt(mContentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness);
    }

    private void updateCaptureTextView() {
        if (captureTextView == null) {
            return;
        }
        captureTextView.setText("Images Captured: " + String.valueOf(mCameraFragment.mRequestCounter) + "/" + String.valueOf(totalCaptures));
    }

    public void loadCalibrationActivity(View view) {
        startActivity(new Intent(this, CalibrationActivity.class));
    }

}
