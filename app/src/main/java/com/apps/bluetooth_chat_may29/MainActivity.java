package com.apps.bluetooth_chat_may29;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity" ;
    private TextView status;
    private Button btnConnect;
    private ListView listView;
    private Dialog dialog;
    private EditText inputLayout;


    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    private BluetoothAdapter bluetoothAdapter;
    private  ChatController chatController;
    private static  final  int REQUEST_ENABLE_BLUETOOTH =1;
    private final int  PERMISSION_REQUEST_CODE = 2;
    private BluetoothDevice connectingDevice;

private ArrayAdapter<String> discoveredDeviceAdapter,pairedDeviceAdapter;

    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatMessages;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewsByIds();


        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null){
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog();
            }
        });

        chatMessages = new ArrayList<>();
        chatAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,chatMessages);
        listView.setAdapter(chatAdapter);


        Log.i("Device Version", "  "+ Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 23) {  //Build.VERSION_CODES.M
            //Grant Permission in runtime
            requestPermissionAtRuntime();
        } else {
            //User already granted permission before Installation
        }
    }



    private boolean requestPermissionAtRuntime() {

        String[] permissionsToRequest = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        List<String> listPermissionsNeeded = new ArrayList<String>();
        int result;
        for (String p : permissionsToRequest) {
            result = ContextCompat.checkSelfPermission(this,p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), PERMISSION_REQUEST_CODE);
            return false;
        }return true;

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                }else {
                    requestPermissionAtRuntime();
                }
                break;

            default:
                break;
        }
    }

    private void showDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        ListView pairedDeviceAdapterListView = (ListView) dialog.findViewById(R.id.pairedDeviceList);
        ListView discoveredDeviceAdapterListView = (ListView) dialog.findViewById(R.id.discoveredDeviceList);
        ToggleButton toggleButton = (ToggleButton) dialog.findViewById(R.id.scan);

        pairedDeviceAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        discoveredDeviceAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);

        pairedDeviceAdapterListView.setAdapter(pairedDeviceAdapter);
        discoveredDeviceAdapterListView.setAdapter(discoveredDeviceAdapter);


        final IntentFilter intentFilter = new IntentFilter();
        // Register for broadcasts when a device is discovered
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);

        // Register for broadcasts when discovery has started
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);

        // Register for broadcasts when discovery has finished
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);




        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
             if (isChecked){
                 registerReceiver(mReceiver,intentFilter);
                 if (bluetoothAdapter.isDiscovering()){
                     bluetoothAdapter.cancelDiscovery();
                 }

                 bluetoothAdapter.startDiscovery();
             }else{
                 unregisterReceiver(mReceiver);
                 bluetoothAdapter.cancelDiscovery();
                }
            }
        });



          // If there are paired devices, add each one to the ArrayAdapter

         bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedBluetoothDevices = bluetoothAdapter.getBondedDevices();

        if (pairedBluetoothDevices.size() > 0){
            for (BluetoothDevice bd:pairedBluetoothDevices){
                pairedDeviceAdapter.add(bd.getName()+"\n"+bd.getAddress());
            }
        }else {
            pairedDeviceAdapter.add("No Paired Devices Found");
        }

        pairedDeviceAdapterListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView)view).getText().toString();
                String address = info.substring(info.length()-17);
                Log.i(TAG,"info ? "+info);
                Log.i(TAG,"address ? "+address);
                connectToDevice(address);
                dialog.dismiss();

            }
        });


        discoveredDeviceAdapterListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView)view).getText().toString();
                String address = info.substring(info.length()-17);
                Log.i(TAG,"info ? "+info);
                Log.i(TAG,"address ? "+address);
                connectToDevice(address);
                dialog.dismiss();

            }
        });


        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.setCancelable(false);
        dialog.show();
    }

    private void connectToDevice(String address) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        chatController.connect(bluetoothDevice);
    }


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
             if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)){
                 Log.i(TAG,"action ? ACTION_DISCOVERY_STARTED");
             }else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)){
                 Log.i(TAG,"action ? ACTION_DISCOVERY_FINISHED");
                 if (discoveredDeviceAdapter.getCount() == 0){
                     discoveredDeviceAdapter.add("No devices found");
                 }
             }else if (action.equals(BluetoothDevice.ACTION_FOUND)){
                 Log.i(TAG,"action ? ACTION_FOUND");
                 BluetoothDevice device =  intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                 if (device.getBondState() != BluetoothDevice.BOND_BONDED){
                     Log.i(TAG,"name address ? "+device.getName()+" "+device.getAddress());
                     discoveredDeviceAdapter.add(device.getName()+"\n"+device.getAddress());
                     discoveredDeviceAdapter.notifyDataSetChanged();
                 }
             }

        }
    };

    private void findViewsByIds() {
        status = (TextView) findViewById(R.id.status);
        btnConnect = (Button) findViewById(R.id.btn_connect);
        listView = (ListView) findViewById(R.id.list);
        inputLayout = (EditText) findViewById(R.id.input_layout);
        View btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (inputLayout.getText().toString().equals("")) {
                    Toast.makeText(MainActivity.this, "Please input some texts", Toast.LENGTH_SHORT).show();
                } else {
                    //TODO: here
                    sendMessage(inputLayout.getText().toString());
                    inputLayout.setText("");
                }
            }
        });
    }

    private void sendMessage(String s) {
        if (chatController.getState() != ChatController.STATE_CONNECTED){
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (s.length() > 0){
            byte[] send = s.getBytes();
            chatController.write(send);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()){
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
        }else {
            chatController = new ChatController(this,handler);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (chatController != null){
            if (chatController.getState() == ChatController.STATE_NONE){
                chatController.start();
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatController != null){
            chatController.stop();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK){
                    chatController = new ChatController(this,handler);
                }else{
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1){
                        case ChatController.STATE_CONNECTED:
                            setStatus("Connected to: " + connectingDevice.getName());
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_CONNECTING:
                            setStatus("Connecting...");
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_LISTEN:
                            setStatus("Listening...");
                            break;
                        case ChatController.STATE_NONE:
                            setStatus("Not connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    chatMessages.add("Me: "+writeMessage);
                    chatAdapter.notifyDataSetChanged();

                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf,0,msg.arg1);
                    chatMessages.add(connectingDevice.getName()+":"+readMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    setStatus("Connected to "+connectingDevice.getName());
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void setStatus(String s) {
        status.setText(s);
    }

}
