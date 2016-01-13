package net.raysuhyunlee.blindmakers.Ble;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by SuhyunLee on 2016. 1. 13..
 */
public class BleService extends Service {

    private final Binder mBinder = new BleBinder();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothLeScanner bleScanner;
    private ScanSettings scanSettings;

    private boolean isConnected = false;

    public class BleBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public void onCreate() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        scanSettings = new ScanSettings.Builder().build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy(){
        closeBluetoothGatt();
    }

    public void enableBluetooth(Activity activity) {
        if (bluetoothAdapter != null && !bluetoothAdapter.enable()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            activity.startActivityForResult(enableIntent, 0);
        }
    }

    public void scanDevices(CompatScanCallback scanCallback, int period) {
        if (!bluetoothAdapter.isEnabled()) {
            Log.d("BleService", "Bluetooth is off. Cancel scan");
            return;
        }

        if (bleScanner == null) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        final ScanCallback wrapper = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                scanCallback.onScan(result.getDevice());
            }
        };
        bleScanner.startScan(null, scanSettings, wrapper);

        Handler handler = new Handler();
        handler.postDelayed(() -> {
            bleScanner.stopScan(wrapper);
            scanCallback.onScanFinished();
        }, period);
    }

    public void connect(BluetoothDevice device) {
        BluetoothGattCallback callback = new BluetoothGattCallback() {
            String buffer = "";

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    bluetoothGatt = gatt;
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    bluetoothGatt = null;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int i) {
                // find readable and writable characteristics
                for(BluetoothGattService s : gatt.getServices()) {
                    for(BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        int properties = c.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            gatt.setCharacteristicNotification(c, true);
                        }
                    }
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                String str = characteristic.getStringValue(0);
            }
        };

        device.connectGatt(this, false, callback);
    }

    private void closeBluetoothGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    public interface CompatScanCallback {
        void onScan(BluetoothDevice device);
        void onScanFinished();
    }

    public boolean isConnected() {
        return isConnected;
    }
}
