package ideum.com.megamovie.Java;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import java.text.SimpleDateFormat;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraFragment extends android.app.Fragment
        implements FragmentCompat.OnRequestPermissionsResultCallback {

    private static final boolean SHOULD_SAVE_JPEG = true;
    private static final boolean SHOULD_SAVE_RAW = false;

    private static final String TAG = "Camera Activity";

    /**
     * Request code for camera permissions
     */
    private static final int REQUEST_CAMERA_PERMISSIONS = 2;

    /**
     * Permissions required to take a picture.
     */
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    private LocationProvider mLocationProvider;
    public void setLocationProvider(LocationProvider locationProvider) {
        mLocationProvider = locationProvider;
    }

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCharacteristics mCharacteristics;
    private final AtomicInteger mRequestCounter = new AtomicInteger();
    private CameraCaptureSession mCameraCaptureSession;
    private CameraCaptureSession.CaptureCallback mCaptureSessionCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);

                    String currentDateTime = generateTimeStamp();
                    Log.e(TAG,currentDateTime);

                    File jpegRootPath = new File(Environment.getExternalStorageDirectory(),"MegaMovie/JPEG");
                    if(!jpegRootPath.exists()) {
                        jpegRootPath.mkdirs();
                    }
                    File rawRootPath = new File(Environment.getExternalStorageDirectory(),"MegaMovie/RAW");
                    if(!rawRootPath.exists()) {
                        rawRootPath.mkdirs();
                    }
                    File jpegFile = new File(jpegRootPath,
                            "JPEG_" + currentDateTime + ".jpg");
                    File rawFile = new File(rawRootPath,
                            "RAW_" + currentDateTime + ".dng");

                    ImageSaver.ImageSaverBuilder jpegBuilder;
                    ImageSaver.ImageSaverBuilder rawBuilder;
                    int requestId = (int) request.getTag();
                    jpegBuilder = mJpegResultQueue.get(requestId);
                    rawBuilder = mRawResultQueue.get(requestId);
                    if (jpegBuilder != null) {
                        jpegBuilder.setFile(jpegFile);
                    }
                    if (rawBuilder != null) {
                        rawBuilder.setFile(rawFile);
                    }
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    int requestId = (int) request.getTag();
                    if (SHOULD_SAVE_JPEG) {
                        ImageSaver.ImageSaverBuilder jpegBuilder = mJpegResultQueue.get(requestId);
                        if (jpegBuilder != null) {
                            jpegBuilder.setResult(result);
                        }
                    }
                    if (SHOULD_SAVE_RAW) {
                        ImageSaver.ImageSaverBuilder rawBuilder = mRawResultQueue.get(requestId);
                        if (rawBuilder != null) {
                            rawBuilder.setResult(result);
                        }
                    }
                }
            };

    private CameraDevice.StateCallback mCameraDeviceStateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCameraDevice = camera;
                    createCameraSession();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    mCameraDevice = null;
                }
            };
    private String mCameraID;
    private Size mJpegImageSize;
    private Size mRawImageSize;
    private int mSensorOrientation;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mJpegResultQueue = new TreeMap<>();
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mRawResultQueue = new TreeMap<>();

    private RefCountedAutoCloseable<ImageReader> mJpegImageReader;
    private final ImageReader.OnImageAvailableListener mOnJpegImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    dequeueAndSaveImage(mJpegResultQueue, mJpegImageReader);

                }
            };
    private RefCountedAutoCloseable<ImageReader> mRawImageReader;
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    dequeueAndSaveImage(mRawResultQueue, mRawImageReader);

                }
            };
    //TODO: if user denies request, keep asking them until they say yes.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showMissingPermissionError();
                    return;
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showMissingPermissionError() {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, "This app needs camera permissions.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setUpCamera();
    }

    @Override
    public void onResume() {
        super.onResume();
        openBackgroundThread();
        setUpCamera();
        openCamera();
    }

    @Override
    public void onPause() {
        closeCamera();
        closeBackgroundThread();
        super.onPause();
    }

    public void takePhotoWithSettings(CaptureSequence.CaptureSettings settings) {
        captureStillImage(settings.getExposureTime(),settings.getSensitivity(),settings.getFocusDistance());
    }


    public void takePhoto(long duration,int sensitivity, float focus_distance) {

        captureStillImage(duration, sensitivity, focus_distance);
    }

    private void captureStillImage(long duration, int sensitivity, float focusDistance ) {
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL);
            if (SHOULD_SAVE_JPEG) {
                captureRequestBuilder.addTarget(mJpegImageReader.get().getSurface());
            }
            if (SHOULD_SAVE_RAW) {
                captureRequestBuilder.addTarget(mRawImageReader.get().getSurface());
            }
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            if (SHOULD_SAVE_JPEG) {
                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            }
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, duration);
            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE,focusDistance);

            captureRequestBuilder.setTag(mRequestCounter.getAndIncrement());

            CaptureRequest request = captureRequestBuilder.build();
            if(SHOULD_SAVE_JPEG) {
                ImageSaver.ImageSaverBuilder jpegBuilder = new ImageSaver.ImageSaverBuilder().setCharacteristics(mCharacteristics);
                mJpegResultQueue.put((int) request.getTag(), jpegBuilder);

            }
            if (SHOULD_SAVE_RAW) {
                ImageSaver.ImageSaverBuilder rawBuilder = new ImageSaver.ImageSaverBuilder().setCharacteristics(mCharacteristics);
                mRawResultQueue.put((int) request.getTag(), rawBuilder);
            }
            mCameraCaptureSession.capture(request,
                    mCaptureSessionCallback,
                    mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraSession() {
        try {
            List<Surface> surfaces = new ArrayList<>();
            if (SHOULD_SAVE_JPEG) {
                surfaces.add(mJpegImageReader.get().getSurface());
            }
            if (SHOULD_SAVE_RAW){
                surfaces.add(mRawImageReader.get().getSurface());
            }
            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            mCameraCaptureSession = session;

                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Toast.makeText(getActivity().getApplicationContext(), "create camera session failed", Toast.LENGTH_SHORT).show();
                        }

                    }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void requestCameraPermissions() {
        FragmentCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS);
    }

    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void openCamera() {

        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        if (!hasAllPermissionsGranted()) {
            requestCameraPermissions();
            return;
        }
        try {
            try {
                cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mJpegImageReader != null) {
            mJpegImageReader.close();
            mJpegImageReader = null;
        }
        if (mRawImageReader != null) {
            mRawImageReader.close();
            mRawImageReader = null;
        }
    }

    private void setUpCamera() {
        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraID : mCameraManager.getCameraIdList()) {
                CameraCharacteristics cc = mCameraManager.getCameraCharacteristics(cameraID);
                if (cc.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                mCharacteristics = cc;
                StreamConfigurationMap map = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mJpegImageSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                        rhs.getWidth() * rhs.getHeight());
                            }
                        }
                );
                mRawImageSize = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                        new Comparator<Size>() {
                            @Override
                            public int compare(Size lhs, Size rhs) {
                                return Long.signum(lhs.getWidth() * lhs.getHeight() -
                                        rhs.getWidth() * rhs.getHeight());
                            }
                        }
                );


                if (SHOULD_SAVE_JPEG) {
                    if (mJpegImageReader == null || mJpegImageReader.getAndRetain() == null) {
                        mJpegImageReader = new RefCountedAutoCloseable<>(
                                ImageReader.newInstance(mJpegImageSize.getWidth(),
                                        mJpegImageSize.getHeight(),
                                        ImageFormat.JPEG,
                        /*max images */50));

                    }
                }
                if(SHOULD_SAVE_RAW) {
                    if (mRawImageReader == null || mRawImageReader.getAndRetain() == null) {
                        mRawImageReader = new RefCountedAutoCloseable<>(
                                ImageReader.newInstance(mRawImageSize.getWidth(),
                                        mRawImageSize.getHeight(),
                                        ImageFormat.RAW_SENSOR,
                        /*max images */50));
                    }
                }
                if (SHOULD_SAVE_JPEG) {
                    mJpegImageReader.get().setOnImageAvailableListener(mOnJpegImageAvailableListener, mBackgroundHandler);
                }
                if (SHOULD_SAVE_RAW) {
                    mRawImageReader.get().setOnImageAvailableListener(mOnRawImageAvailableListener, mBackgroundHandler);
                }
                mCameraID = cameraID;
                mSensorOrientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void closeBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }


    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
                                     RefCountedAutoCloseable<ImageReader> reader) {
        Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry = pendingQueue.firstEntry();
        ImageSaver.ImageSaverBuilder builder = entry.getValue();

        // Increment reference count to prevent ImageReader from being closed while we
        // are saving its Images in a background thread (otherwise their resources may
        // be freed while we are writing to a file).
        if (reader == null || reader.getAndRetain() == null) {
            Log.e(TAG, "Paused the activity before we could save the image," +
                    " ImageReader already closed.");
            pendingQueue.remove(entry.getKey());
            return;
        }

        Image image;
        try {
            image = reader.get().acquireNextImage();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return;
        }
        builder.setRefCountedReader(reader).setImage(image);
        handleCompletionLocked(entry.getKey(), builder, pendingQueue);
    }

    private void handleCompletionLocked(int requestId, ImageSaver.ImageSaverBuilder builder,
                                        TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null) return;
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            queue.remove(requestId);
//            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);
            new Thread(saver).start();
        }
    }

    private static class ImageSaver implements Runnable {

        private final Image mImage;
        private final File mFile;
        private final CaptureResult mCaptureResult;
        private final CameraCharacteristics mCharacteristics;
        private final RefCountedAutoCloseable<ImageReader> mReader;

        private ImageSaver(Image image, File file, CaptureResult captureResult,
                           CameraCharacteristics characteristics, RefCountedAutoCloseable<ImageReader> reader) {
            mImage = image;
            mFile = file;
            mCaptureResult = captureResult;
            mCharacteristics = characteristics;
            mReader = reader;
        }

        @Override
        public void run() {

            int format = mImage.getFormat();
//            mImage.setCropRect(new Rect(0,0,-100,100));
            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);

                    FileOutputStream fileOutputStream = null;

                    try {
                        fileOutputStream = new FileOutputStream(mFile);
                        fileOutputStream.write(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(fileOutputStream);
                    }
                }
                break;
                case ImageFormat.RAW_SENSOR: {
                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
//                        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
//                        dngCreator.writeByteBuffer(output,new Size(4080,3028), buffer,0);
                        dngCreator.writeImage(output, mImage);

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                }
                break;
                default: {
                    Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                    break;
                }
            }
        }

        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private RefCountedAutoCloseable<ImageReader> mReader;

            public synchronized ImageSaverBuilder setRefCountedReader(RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();
                mReader = reader;
                return this;
            }

            public synchronized ImageSaverBuilder setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
                return this;
            }

            public synchronized ImageSaverBuilder setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
                return this;
            }

            public synchronized ImageSaverBuilder setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
                return this;
            }

            public synchronized ImageSaverBuilder setCharacteristics(final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mImage, mFile, mCaptureResult, mCharacteristics, mReader);
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            public synchronized String getFileName() {
                return (mFile == null) ? "Unknown" : mFile.getName();
            }

            public boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null
                        && mCharacteristics != null;
            }

        }
    }
    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private  String generateTimeStamp() {
        long mills = mLocationProvider.getLocation().getTime();
        long days = TimeUnit.MILLISECONDS.toDays(mills);
        mills -= TimeUnit.DAYS.toMillis(days);



        long hours = TimeUnit.MILLISECONDS.toHours(mills);
        mills = mills - TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(mills);
        mills = mills - TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(mills);
        mills = mills - TimeUnit.SECONDS.toMillis(seconds);

        String result = "";
        result += String.format("%02d", hours);
        result += "_" + String.format("%02d", minutes);
        result += "_" + String.format("%02d", seconds);
        result += "_" + String.valueOf(mills);
        return result;

//        SimpleDateFormat sdf = new SimpleDateFormat("yyy_MM_dd_HH_mm_ss_SSS", Locale.US);
//        return sdf.format(new Date());
    }
    /**
     * A wrapper for an {@link AutoCloseable} object that implements reference counting to allow
     * for resource management.
     */
    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        /**
         * Wrap the given object.
         *
         * @param object an object to wrap.
         */
        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        /**
         * Return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T get() {
            return mObject;
        }

        /**
         * Decrement the reference count and release the wrapped object if there are no other
         * users retaining this object.
         */
        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }

}