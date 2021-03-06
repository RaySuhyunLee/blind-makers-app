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
    private BluetoothGattCharacteristic defaultCharacteristic;
    private ScanCallback currentScanCallback;

    private String prefix = "";
    private String suffix = "";

    public float weight;
    public float battery;
    public int rssi;

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

    public void scanDevices(CompatScanCallback scanCallback) {
        if (!bluetoothAdapter.isEnabled()) {
            Log.d("BleService", "Bluetooth is off. Cancel scan");
            return;
        }

        if (bleScanner == null) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        currentScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                scanCallback.onScan(result.getDevice());
            }
        };
        bleScanner.startScan(null, scanSettings, currentScanCallback);
    }

    public void stopScan() {
        if (currentScanCallback != null && bleScanner != null) {
            bleScanner.stopScan(currentScanCallback);
            currentScanCallback = null;
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

    public void connect(BluetoothDevice device, ConnectionCallback connectionCallback) {
        BluetoothGattCallback callback = new BluetoothGattCallback() {
            private String buffer = "";

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bluetoothGatt = gatt;
                    gatt.discoverServices();
                    if (connectionCallback != null)
                        connectionCallback.onConnected();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    bluetoothGatt = null;
                    if (connectionCallback != null)
                        connectionCallback.onDisconnected();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int i) {
                // find readable and writable characteristics
                for(BluetoothGattService s : gatt.getServices()) {
                    for(BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        if (isNotificationCharacteristic(c)) {
                            gatt.setCharacteristicNotification(c, true);
                            if (isWritableCharacteristic(c) && defaultCharacteristic == null) {
                                defaultCharacteristic = c;
                                Log.d("BleService", "DefaultCharacteristic found");
                            }
                        }
                    }
                }
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                BleService.this.rssi = rssi;
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                //Log.d("BleService", "Characteristic changed: " + characteristic.getUuid());
                gatt.readRemoteRssi();
                String str = characteristic.getStringValue(0);
                buffer += str;

                while(true) {
                    int start = buffer.indexOf(prefix);
                    if (start != -1) {
                        int end = buffer.indexOf(suffix, start);
                        if (end != -1) {
                            try {
                                //rxCallback.onReceive(buffer.substring(start + prefix.length(), end));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            String sub = buffer.substring(start + prefix.length(), end);
                            String split[] = sub.split(",");
                            weight = Float.parseFloat(split[0]);
                            battery = Float.parseFloat(split[1]);
                            buffer = buffer.substring(end + suffix.length());
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        };

        device.connectGatt(this, false, callback);
    }

    public void disconnect() {
        if (isConnected()) {
            bluetoothGatt.disconnect();
            bluetoothGatt = null;
        }
    }

    private boolean isReadableCharateristic(BluetoothGattCharacteristic c) {
        return (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0;
    }

    private boolean isWritableCharacteristic(BluetoothGattCharacteristic c) {
        int p = c.getProperties();
        return ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) |
                (p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0;
    }

    private boolean isNotificationCharacteristic(BluetoothGattCharacteristic c) {
        return (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
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

    public interface RxCallback {
        void onReceive(String msg);
    }

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
    }

    public boolean isConnected() {
        return bluetoothGatt != null;
    }

    public void write(byte[] msg) {
        if (!isConnected()) {
            Log.e("BleService", "Bluetooth not connected!");
            return;
        }

        defaultCharacteristic.setValue(msg);
        defaultCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        bluetoothGatt.writeCharacteristic(defaultCharacteristic);
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }
}
