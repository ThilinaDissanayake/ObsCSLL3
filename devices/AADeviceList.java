package com.sroom.cslablogger3.devices;

import com.sroom.cslablogger3.DeviceProfile;

public final class AADeviceList {

    static private DeviceProfile[] _list = new DeviceProfile[]{
            new DeviceProfile(AndroidAccelerometer.class),
            new DeviceProfile(AndroidBattery.class),
            new DeviceProfile(AndroidBluetooth.class),
            new DeviceProfile(AndroidBluetoothLE.class),
            new DeviceProfile(AndroidCamera.class),
            new DeviceProfile(AndroidLight.class),
            new DeviceProfile(AndroidLocation.class),
            new DeviceProfile(AndroidMicrophone.class),
            new DeviceProfile(AndroidOrientation.class),
            new DeviceProfile(AndroidGyroscope.class),
            new DeviceProfile(AndroidWifi.class),
            new DeviceProfile(AndroidPressure.class),
            new DeviceProfile(AndroidMagnetic.class),
            new DeviceProfile(Sweep.class),
            new DeviceProfile(Swave.class),
            new DeviceProfile(SwaveSweep.class),
            new DeviceProfile(SkyhookLocation.class),
            new DeviceProfile(AndroidCellId.class),
            new DeviceProfile(WirelessT.class),
            new DeviceProfile(OptiEyes.class),
            new DeviceProfile(AndroidWatchAccelerometer.class),
            new DeviceProfile(GoogleGlass.class),
            //new DeviceProfile(AndroidWatchMicrophone.class),
    };

	static public DeviceProfile[] getDeviceProfiles() {
        return _list;
    }
}
