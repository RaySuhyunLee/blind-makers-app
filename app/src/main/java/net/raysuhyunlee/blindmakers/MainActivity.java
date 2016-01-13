package net.raysuhyunlee.blindmakers;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import net.raysuhyunlee.blindmakers.Ble.BleService;

public class MainActivity extends AppCompatActivity {

    ServiceConnection serviceConn;
    BleService bleService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d("MainActivity", "service connected");
                bleService = ((BleService.BleBinder)service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        Intent intent = new Intent(this, BleService.class);
        bindService(intent, serviceConn, BIND_AUTO_CREATE);

        findViewById(R.id.button).setOnClickListener((v) -> {
            bleService.scanDevices(new BleService.CompatScanCallback() {
                @Override
                public void onScan(BluetoothDevice device) {
                    Toast.makeText(MainActivity.this, device.getName(), Toast.LENGTH_SHORT).show();
                    if (device.getName().equals("BlunoMegaV1.8")) {
                        bleService.connect(device, (msg) -> {
                        });
                    }
                }

                @Override
                public void onScanFinished() {

                }
            }, 1000);
        });

        findViewById(R.id.button2).setOnClickListener((v) -> {
            byte a[] = {0, 1, '\n'};
            bleService.write(a);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(serviceConn);
    }
}
