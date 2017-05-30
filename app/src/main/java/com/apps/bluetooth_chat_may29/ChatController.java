package com.apps.bluetooth_chat_may29;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by Kavitha on 5/29/2017.
 */

public class ChatController {

    private static final String APP_NAME = "Bluetooth_Chat_may29";
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;
    AcceptThread acceptThread;
    ConnectThread connectThread;
    ReadWriteThread readWriteThread;


    static final int STATE_NONE = 0;
    static final int STATE_LISTEN = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;

    private int state;

    public  ChatController(Context context,Handler handler){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_NONE;
        this.handler = handler;
    }


    // Set the current state of the chat connection
    private synchronized void  setState(int state){
        this.state = state;
        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE,state,-1).sendToTarget();
    }

    // get current connection state
    public synchronized int getState(){
        return  state;
    }


    public synchronized void start(){
        if (connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        if (readWriteThread != null){
            readWriteThread.cancel();
            readWriteThread = null;
        }
        setState(STATE_LISTEN);

        if (acceptThread == null){
            acceptThread = new AcceptThread();
            acceptThread.start();
        }

    }


    public synchronized void  connect(BluetoothDevice device){

        if (state == STATE_CONNECTED){
            if (connectThread != null){
                connectThread.cancel();
                connectThread = null;
            }
        }

        if (connectThread != null){
            connectThread.cancel();
            connectThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);


    }

    // stop all threads
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (readWriteThread != null) {
            readWriteThread.cancel();
            readWriteThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(STATE_NONE);
    }

    // runs while listening for incoming connections
    class  AcceptThread extends  Thread{
        private  BluetoothServerSocket serverSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME,MY_UUID);
            }catch (IOException e){
                e.printStackTrace();
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;

            while (state != STATE_CONNECTED){
                try {
                    socket = serverSocket.accept();
                }catch (IOException e){
                    break;
                }

                // If a connection was accepted
                if (socket != null){
                    synchronized (ChatController.this) {
                        switch (state){
                            case  STATE_LISTEN:
                            case  STATE_CONNECTING:
                                // start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case  STATE_NONE:
                            case  STATE_CONNECTED:
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close();
                                }catch (Exception e){

                                }
                                break;
                        }
                    }
                }


            }
        }

        public void cancel(){
            try {
                serverSocket.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }


    class ConnectThread extends  Thread{
        BluetoothSocket socket;
        BluetoothDevice device;

        public ConnectThread(BluetoothDevice device){
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            }catch (IOException e){
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run(){
         setName("ConnectThread");
            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket

            try {
                socket.connect();
            }catch (IOException e){

                try {
                    socket.connect();
                }catch (IOException ee){

                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ChatController.this){
                connectThread = null;
            }

            // Start the connected thread
            connected(socket,device);

        }

        public void cancel(){
            try {
                socket.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }


    }

    private void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel running thread
        if (readWriteThread != null) {
            readWriteThread.cancel();
            readWriteThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        readWriteThread = new ReadWriteThread(socket);
        readWriteThread.start();


        // Send the name of the connected device back to the UI Activity
        Message message = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(MainActivity.DEVICE_OBJECT,device);
        message.setData(bundle);
        handler.sendMessage(message);

        setState(STATE_CONNECTED);



    }


    class  ReadWriteThread extends  Thread{

        BluetoothSocket bluetoothSocket;
        InputStream inputStream;
        OutputStream outputStream;


  public  ReadWriteThread(BluetoothSocket socket){
      this.bluetoothSocket = socket;
      InputStream tmpIn = null;
      OutputStream tmpOut = null;

      try {
          tmpIn = socket.getInputStream();
          tmpOut = socket.getOutputStream();
      }catch (IOException e){

      }

      inputStream = tmpIn;
      outputStream = tmpOut;
  }

        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while (true){
                try {

                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(MainActivity.MESSAGE_READ,bytes,-1,buffer).sendToTarget();
                }catch (IOException e){
                    connectionLost();
                    break;
                }
            }
        }


        public void  write(byte[] buffer){
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ;

        }



        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] out){
        ReadWriteThread readWriteThred;
        synchronized (this){
            if (state != STATE_CONNECTED)
                return;

            readWriteThred = readWriteThread;
        }
        readWriteThred.write(out);
    }

    private void connectionFailed() {
        Message message = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast","Unable to Connect device");
        message.setData(bundle);
        handler.sendMessage(message);


        ChatController.this.start();
    }


    private void connectionLost() {
        Message message = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast","Device Connection was lost");
        message.setData(bundle);
        handler.sendMessage(message);

        ChatController.this.start();
    }
}
