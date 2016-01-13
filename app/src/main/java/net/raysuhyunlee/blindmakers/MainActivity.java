package net.raysuhyunlee.blindmakers;

import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.lsjwzh.widget.materialloadingprogressbar.CircleProgressBar;

import net.raysuhyunlee.blindmakers.Ble.BleService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MenuItem scanMenu;

    // Ble
    private ServiceConnection serviceConn;
    private BleService bleService;
    private BleService.RxCallback rxCallback = (msg) -> {

    };
    private BleService.ConnectionCallback connectionCallback = new BleService.ConnectionCallback() {
        @Override
        public void onConnected() {
            invalidateOptionsMenu();
        }

        @Override
        public void onDisconnected() {
            invalidateOptionsMenu();
        }
    };

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(serviceConn);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        scanMenu = menu.findItem(R.id.menu_scan);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_scan:
                if (!bleService.isConnected()) {
                    openScanDialog();
                } else {
                    openDisconnectDialog();
                }
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (bleService.isConnected()) {
            menu.findItem(R.id.menu_scan).setTitle(R.string.connected);
        } else {
            menu.findItem(R.id.menu_scan).setTitle(R.string.scan);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void openScanDialog() {
        Dialog scanProgressDialog = new Dialog(this, R.style.Dialog_Transparent);
        scanProgressDialog.setContentView(R.layout.dialog_scan_progress);
        ((CircleProgressBar)scanProgressDialog.findViewById(R.id.progressBar))
                .setColorSchemeResources(R.color.colorAccent);
        scanProgressDialog.show();


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        List<BluetoothDevice> devices = new ArrayList<>();
        InjectionArrayAdapter<BluetoothDevice> adapter =
                new InjectionArrayAdapter<>(this, R.layout.bluetooth_device, devices,
                        (position, view, data) -> {
                            ((TextView)view.findViewById(R.id.textViewName))
                                    .setText(data.getName());
                            ((TextView)view.findViewById(R.id.textViewAddress))
                                    .setText(data.getAddress());
                            return view;
                        });

        BleService.CompatScanCallback scanCallback = new BleService.CompatScanCallback() {
            public void onScan(BluetoothDevice device) {
                devices.add(device);
            }

            public void onScanFinished() {
                scanProgressDialog.cancel();
                if (devices.size() == 0) {
                    builder.setMessage(R.string.no_available_devices);
                } else {
                    builder.setAdapter(adapter, (dialog, which) -> {
                        bleService.connect(devices.get(which), connectionCallback, rxCallback);
                    });
                }
                builder.show();
            }

        };
        bleService.scanDevices(scanCallback, 1500);
    }

    public void openDisconnectDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.ask_disconnect);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            bleService.disconnect();
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {});
        builder.show();
    }

}
