// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.device.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;

import org.chromium.base.CollectionUtil;
import org.chromium.base.ContextUtils;
import org.chromium.base.Log;
import org.chromium.base.ThreadUtils;
import org.chromium.base.VisibleForTesting;
import org.chromium.base.annotations.CalledByNative;
import org.chromium.base.annotations.JNINamespace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Android implementation of the device {motion|orientation} APIs.
 */
@JNINamespace("device")
class DeviceSensors implements SensorEventListener {
    private static final String TAG = "DeviceSensors";

    // Matches kEnableExperimentalWebPlatformFeatures.
    private static final String EXPERIMENTAL_WEB_PLAFTORM_FEATURES =
            "enable-experimental-web-platform-features";

    // These fields are lazily initialized by getHandler().
    private Thread mThread;
    private Handler mHandler;

    // The lock to access the mHandler.
    private final Object mHandlerLock = new Object();

    // Non-zero if and only if we're listening for events.
    // To avoid race conditions on the C++ side, access must be synchronized.
    private long mNativePtr;

    // The lock to access the mNativePtr.
    private final Object mNativePtrLock = new Object();

    // The geomagnetic vector expressed in the body frame.
    private float[] mMagneticFieldVector;

    // Holds a shortened version of the rotation vector for compatibility purposes.
    private float[] mTruncatedRotationVector;

    // Holds current rotation matrix for the device.
    private float[] mDeviceRotationMatrix;

    // Holds Euler angles corresponding to the rotation matrix.
    private double[] mRotationAngles;

    // Lazily initialized when registering for notifications.
    private SensorManagerProxy mSensorManagerProxy;

    static final Set<Integer> DEVICE_ORIENTATION_SENSORS_A =
            CollectionUtil.newHashSet(Sensor.TYPE_GAME_ROTATION_VECTOR);
    static final Set<Integer> DEVICE_ORIENTATION_SENSORS_B =
            CollectionUtil.newHashSet(Sensor.TYPE_ROTATION_VECTOR);
    // Option C backup sensors are used when options A and B are not available.
    static final Set<Integer> DEVICE_ORIENTATION_SENSORS_C =
            CollectionUtil.newHashSet(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD);
    static final Set<Integer> DEVICE_ORIENTATION_ABSOLUTE_SENSORS =
            CollectionUtil.newHashSet(Sensor.TYPE_ROTATION_VECTOR);
    static final Set<Integer> DEVICE_MOTION_SENSORS = CollectionUtil.newHashSet(
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GYROSCOPE);

    @VisibleForTesting
    final Set<Integer> mActiveSensors = new HashSet<Integer>();
    final List<Set<Integer>> mOrientationSensorSets;
    Set<Integer> mDeviceOrientationSensors;
    boolean mDeviceMotionIsActive;
    boolean mDeviceOrientationIsActive;
    boolean mDeviceOrientationIsActiveWithBackupSensors;
    boolean mDeviceOrientationAbsoluteIsActive;
    boolean mOrientationNotAvailable;

    protected DeviceSensors() {
        mOrientationSensorSets = CollectionUtil.newArrayList(DEVICE_ORIENTATION_SENSORS_A,
                DEVICE_ORIENTATION_SENSORS_B, DEVICE_ORIENTATION_SENSORS_C);
    }

    // For orientation we use a 3-way fallback approach where up to 3 different sets of sensors
    // are attempted if necessary. The sensors to be used for orientation are determined in the
    // following order:
    //   A: GAME_ROTATION_VECTOR (relative)
    //   B: ROTATION_VECTOR (absolute)
    //   C: combination of ACCELEROMETER and MAGNETIC_FIELD (absolute)
    // Some of the sensors may not be available depending on the device and Android version, so
    // the 3-way fallback ensures selection of the best possible option.
    // Examples:
    //   * Nexus 9, Android 5.0.2 --> option A
    //   * Nexus 10, Android 5.1 --> option B
    //   * Moto G, Android 4.4.4  --> option C
    @VisibleForTesting
    protected boolean registerOrientationSensorsWithFallback(int rateInMicroseconds) {
        if (mOrientationNotAvailable) return false;
        if (mDeviceOrientationSensors != null) {
            return registerSensors(mDeviceOrientationSensors, rateInMicroseconds, true);
        }
        ensureRotationStructuresAllocated();

        for (Set<Integer> sensors : mOrientationSensorSets) {
            mDeviceOrientationSensors = sensors;
            if (registerSensors(mDeviceOrientationSensors, rateInMicroseconds, true)) return true;
        }

        mOrientationNotAvailable = true;
        mDeviceOrientationSensors = null;
        mDeviceRotationMatrix = null;
        mRotationAngles = null;
        return false;
    }

    /**
     * Start listening for sensor events. If this object is already listening
     * for events, the old callback is unregistered first.
     *
     * @param nativePtr Value to pass to nativeGotOrientation() for each event.
     * @param rateInMicroseconds Requested callback rate in microseconds. The
     *            actual rate may be higher. Unwanted events should be ignored.
     * @param eventType Type of event to listen to, can be either DEVICE_ORIENTATION,
     *            DEVICE_ORIENTATION_ABSOLUTE or DEVICE_MOTION.
     * @return True on success.
     */
    @CalledByNative
    public boolean start(long nativePtr, int eventType, int rateInMicroseconds) {
        boolean success = false;
        synchronized (mNativePtrLock) {
            switch (eventType) {
                case ConsumerType.ORIENTATION:
                    success = registerOrientationSensorsWithFallback(rateInMicroseconds);
                    break;
                case ConsumerType.ORIENTATION_ABSOLUTE:
                    ensureRotationStructuresAllocated();
                    success = registerSensors(
                            DEVICE_ORIENTATION_ABSOLUTE_SENSORS, rateInMicroseconds, true);
                    break;
                case ConsumerType.MOTION:
                    // note: device motion spec does not require all sensors to be available
                    success = registerSensors(DEVICE_MOTION_SENSORS, rateInMicroseconds, false);
                    break;
                default:
                    Log.e(TAG, "Unknown event type: %d", eventType);
                    return false;
            }
            if (success) {
                mNativePtr = nativePtr;
                setEventTypeActive(eventType, true);
            }
            return success;
        }
    }

    @CalledByNative
    public int getNumberActiveDeviceMotionSensors() {
        Set<Integer> deviceMotionSensors = new HashSet<Integer>(DEVICE_MOTION_SENSORS);
        deviceMotionSensors.removeAll(mActiveSensors);
        return DEVICE_MOTION_SENSORS.size() - deviceMotionSensors.size();
    }

    @CalledByNative
    public int getOrientationSensorTypeUsed() {
        if (mOrientationNotAvailable) {
            return OrientationSensorType.NOT_AVAILABLE;
        }
        if (mDeviceOrientationSensors == DEVICE_ORIENTATION_SENSORS_A) {
            return OrientationSensorType.GAME_ROTATION_VECTOR;
        }
        if (mDeviceOrientationSensors == DEVICE_ORIENTATION_SENSORS_B) {
            return OrientationSensorType.ROTATION_VECTOR;
        }
        if (mDeviceOrientationSensors == DEVICE_ORIENTATION_SENSORS_C) {
            return OrientationSensorType.ACCELEROMETER_MAGNETIC;
        }

        assert false; // should never happen
        return OrientationSensorType.NOT_AVAILABLE;
    }

    /**
     * Stop listening to sensors for a given event type. Ensures that sensors are not disabled
     * if they are still in use by a different event type.
     *
     * @param eventType Type of event to listen to, can be either DEVICE_ORIENTATION or
     *                  DEVICE_MOTION.
     * We strictly guarantee that the corresponding native*() methods will not be called
     * after this method returns.
     */
    @CalledByNative
    public void stop(int eventType) {
        Set<Integer> sensorsToRemainActive = new HashSet<Integer>();

        synchronized (mNativePtrLock) {
            if (mDeviceOrientationIsActive && eventType != ConsumerType.ORIENTATION) {
                sensorsToRemainActive.addAll(mDeviceOrientationSensors);
            }

            if (mDeviceOrientationAbsoluteIsActive
                    && eventType != ConsumerType.ORIENTATION_ABSOLUTE) {
                sensorsToRemainActive.addAll(DEVICE_ORIENTATION_ABSOLUTE_SENSORS);
            }

            if (mDeviceMotionIsActive && eventType != ConsumerType.MOTION) {
                sensorsToRemainActive.addAll(DEVICE_MOTION_SENSORS);
            }

            Set<Integer> sensorsToDeactivate = new HashSet<Integer>(mActiveSensors);
            sensorsToDeactivate.removeAll(sensorsToRemainActive);
            unregisterSensors(sensorsToDeactivate);
            setEventTypeActive(eventType, false);
            if (mActiveSensors.isEmpty()) {
                mNativePtr = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        sensorChanged(event.sensor.getType(), event.values);
    }

    @VisibleForTesting
    void sensorChanged(int type, float[] values) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                if (mDeviceMotionIsActive) {
                    gotAccelerationIncludingGravity(values[0], values[1], values[2]);
                }
                if (mDeviceOrientationIsActiveWithBackupSensors) {
                    getOrientationFromGeomagneticVectors(values, mMagneticFieldVector);
                }
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                if (mDeviceMotionIsActive) {
                    gotAcceleration(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                if (mDeviceMotionIsActive) {
                    gotRotationRate(values[0], values[1], values[2]);
                }
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                if (mDeviceOrientationAbsoluteIsActive) {
                    convertRotationVectorToAngles(values, mRotationAngles);
                    gotOrientationAbsolute(
                            mRotationAngles[0], mRotationAngles[1], mRotationAngles[2]);
                }
                if (mDeviceOrientationIsActive
                        && mDeviceOrientationSensors == DEVICE_ORIENTATION_SENSORS_B) {
                    if (!mDeviceOrientationAbsoluteIsActive) {
                        // only compute if not already computed for absolute orientation above.
                        convertRotationVectorToAngles(values, mRotationAngles);
                    }
                    gotOrientation(mRotationAngles[0], mRotationAngles[1], mRotationAngles[2]);
                }
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                if (mDeviceOrientationIsActive) {
                    convertRotationVectorToAngles(values, mRotationAngles);
                    gotOrientation(mRotationAngles[0], mRotationAngles[1], mRotationAngles[2]);
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                if (mDeviceOrientationIsActiveWithBackupSensors) {
                    if (mMagneticFieldVector == null) {
                        mMagneticFieldVector = new float[3];
                    }
                    System.arraycopy(
                            values, 0, mMagneticFieldVector, 0, mMagneticFieldVector.length);
                }
                break;
            default:
                // Unexpected
                return;
        }
    }

    /**
     * Returns orientation angles from a rotation matrix, such that the angles are according
     * to spec {@link http://dev.w3.org/geo/api/spec-source-orientation.html}.
     * <p>
     * It is assumed the rotation matrix transforms a 3D column vector from device coordinate system
     * to the world's coordinate system, as e.g. computed by {@see SensorManager.getRotationMatrix}.
     * <p>
     * In particular we compute the decomposition of a given rotation matrix R such that <br>
     * R = Rz(alpha) * Rx(beta) * Ry(gamma), <br>
     * where Rz, Rx and Ry are rotation matrices around Z, X and Y axes in the world coordinate
     * reference frame respectively. The reference frame consists of three orthogonal axes X, Y, Z
     * where X points East, Y points north and Z points upwards perpendicular to the ground plane.
     * The computed angles alpha, beta and gamma are in radians and clockwise-positive when viewed
     * along the positive direction of the corresponding axis. Except for the special case when the
     * beta angle is +-pi/2 these angles uniquely define the orientation of a mobile device in 3D
     * space. The alpha-beta-gamma representation resembles the yaw-pitch-roll convention used in
     * vehicle dynamics, however it does not exactly match it. One of the differences is that the
     * 'pitch' angle beta is allowed to be within [-pi, pi). A mobile device with pitch angle
     * greater than pi/2 could correspond to a user lying down and looking upward at the screen.
     *
     * <p>
     * Upon return the array values is filled with the result,
     * <ul>
     * <li>values[0]: rotation around the Z axis, alpha in [0, 2*pi)</li>
     * <li>values[1]: rotation around the X axis, beta in [-pi, pi)</li>
     * <li>values[2]: rotation around the Y axis, gamma in [-pi/2, pi/2)</li>
     * </ul>
     * <p>
     *
     * @param matrixR
     *        a 3x3 rotation matrix {@see SensorManager.getRotationMatrix}.
     *
     * @param values
     *        an array of 3 doubles to hold the result.
     *
     * @return the array values passed as argument.
     */
    @VisibleForTesting
    public static double[] computeDeviceOrientationFromRotationMatrix(
            float[] matrixR, double[] values) {
        /*
         * 3x3 (length=9) case:
         *   /  R[ 0]   R[ 1]   R[ 2]  \
         *   |  R[ 3]   R[ 4]   R[ 5]  |
         *   \  R[ 6]   R[ 7]   R[ 8]  /
         *
         */
        if (matrixR.length != 9) return values;

        if (matrixR[8] > 0) {  // cos(beta) > 0
            values[0] = Math.atan2(-matrixR[1], matrixR[4]);
            values[1] = Math.asin(matrixR[7]);                 // beta (-pi/2, pi/2)
            values[2] = Math.atan2(-matrixR[6], matrixR[8]);   // gamma (-pi/2, pi/2)
        } else if (matrixR[8] < 0) {  // cos(beta) < 0
            values[0] = Math.atan2(matrixR[1], -matrixR[4]);
            values[1] = -Math.asin(matrixR[7]);
            values[1] += (values[1] >= 0) ? -Math.PI : Math.PI; // beta [-pi,-pi/2) U (pi/2,pi)
            values[2] = Math.atan2(matrixR[6], -matrixR[8]);    // gamma (-pi/2, pi/2)
        } else { // R[8] == 0
            if (matrixR[6] > 0) {  // cos(gamma) == 0, cos(beta) > 0
                values[0] = Math.atan2(-matrixR[1], matrixR[4]);
                values[1] = Math.asin(matrixR[7]);       // beta [-pi/2, pi/2]
                values[2] = -Math.PI / 2;                // gamma = -pi/2
            } else if (matrixR[6] < 0) { // cos(gamma) == 0, cos(beta) < 0
                values[0] = Math.atan2(matrixR[1], -matrixR[4]);
                values[1] = -Math.asin(matrixR[7]);
                values[1] += (values[1] >= 0) ? -Math.PI : Math.PI; // beta [-pi,-pi/2) U (pi/2,pi)
                values[2] = -Math.PI / 2;                           // gamma = -pi/2
            } else { // R[6] == 0, cos(beta) == 0
                // gimbal lock discontinuity
                values[0] = Math.atan2(matrixR[3], matrixR[0]);
                values[1] = (matrixR[7] > 0) ? Math.PI / 2 : -Math.PI / 2;  // beta = +-pi/2
                values[2] = 0;                                              // gamma = 0
            }
        }

        // alpha is in [-pi, pi], make sure it is in [0, 2*pi).
        if (values[0] < 0) {
            values[0] += 2 * Math.PI; // alpha [0, 2*pi)
        }

        return values;
    }

    /*
     * Converts a given rotation vector to its Euler angles representation. The angles
     * are in degrees.
     */
    public void convertRotationVectorToAngles(float[] rotationVector, double[] angles) {
        if (rotationVector.length > 4) {
            // On some Samsung devices SensorManager.getRotationMatrixFromVector
            // appears to throw an exception if rotation vector has length > 4.
            // For the purposes of this class the first 4 values of the
            // rotation vector are sufficient (see crbug.com/335298 for details).
            System.arraycopy(rotationVector, 0, mTruncatedRotationVector, 0, 4);
            SensorManager.getRotationMatrixFromVector(
                    mDeviceRotationMatrix, mTruncatedRotationVector);
        } else {
            SensorManager.getRotationMatrixFromVector(mDeviceRotationMatrix, rotationVector);
        }
        computeDeviceOrientationFromRotationMatrix(mDeviceRotationMatrix, angles);
        for (int i = 0; i < 3; i++) {
            angles[i] = Math.toDegrees(angles[i]);
        }
    }

    private void getOrientationFromGeomagneticVectors(float[] acceleration, float[] magnetic) {
        if (acceleration == null || magnetic == null) {
            return;
        }
        if (!SensorManager.getRotationMatrix(mDeviceRotationMatrix, null, acceleration, magnetic)) {
            return;
        }
        computeDeviceOrientationFromRotationMatrix(mDeviceRotationMatrix, mRotationAngles);

        gotOrientation(Math.toDegrees(mRotationAngles[0]), Math.toDegrees(mRotationAngles[1]),
                Math.toDegrees(mRotationAngles[2]));
    }

    private SensorManagerProxy getSensorManagerProxy() {
        if (mSensorManagerProxy != null) {
            return mSensorManagerProxy;
        }

        ThreadUtils.assertOnUiThread();
        SensorManager sensorManager =
                (SensorManager) ContextUtils.getApplicationContext().getSystemService(
                        Context.SENSOR_SERVICE);

        if (sensorManager != null) {
            mSensorManagerProxy = new SensorManagerProxyImpl(sensorManager);
        }
        return mSensorManagerProxy;
    }

    @VisibleForTesting
    void setSensorManagerProxy(SensorManagerProxy sensorManagerProxy) {
        mSensorManagerProxy = sensorManagerProxy;
    }

    private void setEventTypeActive(int eventType, boolean active) {
        switch (eventType) {
            case ConsumerType.ORIENTATION:
                mDeviceOrientationIsActive = active;
                mDeviceOrientationIsActiveWithBackupSensors =
                        active && (mDeviceOrientationSensors == DEVICE_ORIENTATION_SENSORS_C);
                return;
            case ConsumerType.ORIENTATION_ABSOLUTE:
                mDeviceOrientationAbsoluteIsActive = active;
                return;
            case ConsumerType.MOTION:
                mDeviceMotionIsActive = active;
                return;
        }
    }

    private void ensureRotationStructuresAllocated() {
        if (mDeviceRotationMatrix == null) {
            mDeviceRotationMatrix = new float[9];
        }
        if (mRotationAngles == null) {
            mRotationAngles = new double[3];
        }
        if (mTruncatedRotationVector == null) {
            mTruncatedRotationVector = new float[4];
        }
    }

    /**
     * @param sensorTypes List of sensors to activate.
     * @param rateInMicroseconds Intended delay (in microseconds) between sensor readings.
     * @param failOnMissingSensor If true the method returns true only if all sensors could be
     *                            activated. When false the method return true if at least one
     *                            sensor in sensorTypes could be activated.
     */
    private boolean registerSensors(
            Set<Integer> sensorTypes, int rateInMicroseconds, boolean failOnMissingSensor) {
        Set<Integer> sensorsToActivate = new HashSet<Integer>(sensorTypes);
        sensorsToActivate.removeAll(mActiveSensors);
        if (sensorsToActivate.isEmpty()) return true;

        boolean success = false;
        for (Integer sensorType : sensorsToActivate) {
            boolean result = registerForSensorType(sensorType, rateInMicroseconds);
            if (!result && failOnMissingSensor) {
                // restore the previous state upon failure
                unregisterSensors(sensorsToActivate);
                return false;
            }
            if (result) {
                mActiveSensors.add(sensorType);
                success = true;
            }
        }
        return success;
    }

    private void unregisterSensors(Iterable<Integer> sensorTypes) {
        for (Integer sensorType : sensorTypes) {
            if (mActiveSensors.contains(sensorType)) {
                getSensorManagerProxy().unregisterListener(this, sensorType);
                mActiveSensors.remove(sensorType);
            }
        }
    }

    private boolean registerForSensorType(int type, int rateInMicroseconds) {
        SensorManagerProxy sensorManager = getSensorManagerProxy();
        if (sensorManager == null) {
            return false;
        }
        return sensorManager.registerListener(this, type, rateInMicroseconds, getHandler());
    }

    protected void gotOrientation(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotOrientation(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    protected void gotOrientationAbsolute(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotOrientationAbsolute(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    protected void gotAcceleration(double x, double y, double z) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotAcceleration(mNativePtr, x, y, z);
            }
        }
    }

    protected void gotAccelerationIncludingGravity(double x, double y, double z) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotAccelerationIncludingGravity(mNativePtr, x, y, z);
            }
        }
    }

    protected void gotRotationRate(double alpha, double beta, double gamma) {
        synchronized (mNativePtrLock) {
            if (mNativePtr != 0) {
                nativeGotRotationRate(mNativePtr, alpha, beta, gamma);
            }
        }
    }

    private Handler getHandler() {
        // TODO(timvolodine): Remove the mHandlerLock when sure that getHandler is not called
        // from multiple threads. This will be the case when device motion and device orientation
        // use the same polling thread (also see crbug/234282).
        synchronized (mHandlerLock) {
            if (mHandler == null) {
                HandlerThread thread = new HandlerThread("DeviceMotionAndOrientation");
                thread.start();
                mHandler = new Handler(thread.getLooper()); // blocks on thread start
            }
            return mHandler;
        }
    }

    @CalledByNative
    static DeviceSensors create() {
        return new DeviceSensors();
    }

    /**
     * Native JNI calls,
     * see device/sensors/sensor_manager_android.cc
     */

    /**
     * Orientation of the device with respect to its reference frame.
     */
    private native void nativeGotOrientation(
            long nativeSensorManagerAndroid, double alpha, double beta, double gamma);

    /**
     * Absolute orientation of the device with respect to its reference frame.
     */
    private native void nativeGotOrientationAbsolute(
            long nativeSensorManagerAndroid, double alpha, double beta, double gamma);

    /**
     * Linear acceleration without gravity of the device with respect to its body frame.
     */
    private native void nativeGotAcceleration(
            long nativeSensorManagerAndroid, double x, double y, double z);

    /**
     * Acceleration including gravity of the device with respect to its body frame.
     */
    private native void nativeGotAccelerationIncludingGravity(
            long nativeSensorManagerAndroid, double x, double y, double z);

    /**
     * Rotation rate of the device with respect to its body frame.
     */
    private native void nativeGotRotationRate(
            long nativeSensorManagerAndroid, double alpha, double beta, double gamma);

    /**
     * Need the an interface for SensorManager for testing.
     */
    interface SensorManagerProxy {
        public boolean registerListener(
                SensorEventListener listener, int sensorType, int rate, Handler handler);
        public void unregisterListener(SensorEventListener listener, int sensorType);
    }

    static class SensorManagerProxyImpl implements SensorManagerProxy {
        private final SensorManager mSensorManager;

        SensorManagerProxyImpl(SensorManager sensorManager) {
            mSensorManager = sensorManager;
        }

        @Override
        public boolean registerListener(
                SensorEventListener listener, int sensorType, int rate, Handler handler) {
            List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
            if (sensors.isEmpty()) {
                return false;
            }
            return mSensorManager.registerListener(listener, sensors.get(0), rate, handler);
        }

        @Override
        public void unregisterListener(SensorEventListener listener, int sensorType) {
            List<Sensor> sensors = mSensorManager.getSensorList(sensorType);
            if (sensors.isEmpty()) {
                return;
            }
            try {
                mSensorManager.unregisterListener(listener, sensors.get(0));
            } catch (IllegalArgumentException e) {
                // Suppress occasional exception on Digma iDxD* devices:
                // Receiver not registered: android.hardware.SystemSensorManager$1
                // See crbug.com/596453.
                Log.w(TAG, "Failed to unregister device sensor " + sensors.get(0).getName());
            }
        }
    }
}
