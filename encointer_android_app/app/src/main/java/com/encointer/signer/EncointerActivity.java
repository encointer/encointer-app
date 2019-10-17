package com.encointer.signer;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.neovisionaries.ws.client.*;
import android.util.Log;
import org.json.JSONObject;
import java.math.BigInteger;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EncointerActivity extends AppCompatActivity {
    private static final String TAG = "EncointerActivity";
    public static final String EXTRA_USERNAME = "com.encointer.signer.USERNAME";

    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    WebSocket ws = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);
        //System.loadLibrary("encointer_api_native");


        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        // load preferences
        EditText editText_username = findViewById(R.id.editText_username);
        editText_username.setText(sharedPref.getString("username", "myusername"));
        EditText editText_url = findViewById(R.id.editText_url);
        String node_ws_url = sharedPref.getString("node_ws_url", "wss://poc3-rpc.polkadot.io/");
        editText_url.setText(node_ws_url);

        //TextView dummyTextView = findViewById(R.id.dummyTextView);
        //dummyTextView.setText(hello("Rust Library"));

        // Create a WebSocket factory and set 5000 milliseconds as a timeout
        // value for socket connection.
        WebSocketFactory factory = new WebSocketFactory().setConnectionTimeout(5000);

        // Create a WebSocket. The timeout value set above is used.
        try {
            ws = factory.createSocket(node_ws_url);

            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onTextMessage(WebSocket websocket, String message) throws Exception {
                    Log.i(TAG, "onTextMessage: " + message);
                    try {
                        JSONObject jsonObj = new JSONObject(message);
                        String blockNrHex = jsonObj.getJSONObject("params")
                                .getJSONObject("result")
                                .getString("number");
                        BigInteger block_nr = new BigInteger(blockNrHex.substring("0x".length()), 16);
                        TextView tv_block_number = findViewById(R.id.block_number);
                        tv_block_number.setText(String.format("latest block number is %d", block_nr));
                        Log.i(TAG, String.format("latest block number is %s / %d", blockNrHex, block_nr));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
                    Log.i(TAG, "onConnected is executed");
                    Log.i(TAG, "subscribing to Alexander Block updates");
                    websocket.sendText("{\"id\":12,\"jsonrpc\":\"2.0\",\"method\":\"chain_subscribeNewHead\",\"params\":[]}");

                }
            });

            ws.connectAsynchronously();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (ws != null) {
            ws.disconnect();
            ws = null;
        }
    }


    @Override
    protected void onStart() {
        super.onStart();

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
        }

    }

    public void startCeremony(View view) {
        Intent intent = new Intent(this, PersonCounter.class);
        EditText editText_username = findViewById(R.id.editText_username);
        String username = editText_username.getText().toString();

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().putString("username", username).apply();

        if(username.length() == 0) {
            editText_username.setError("Please enter a valid username!");
        } else {
            intent.putExtra(EXTRA_USERNAME, username);
            startActivity(intent);
        }
    }

    public void sendExtrinsic(View view) {

        EditText editText_url = findViewById(R.id.editText_url);
        String url = editText_url.getText().toString();
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().putString("node_ws_url", url).apply();


        /*
        if(url.length() == 0) {
            editText_url.setError("Please enter a valid username!");
        } else {

            NativeApiThread p = new NativeApiThread(url);
            p.start();
        }
*/
    }

    /** Returns true if the app was granted all the permissions. Otherwise, returns false. */
    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }



}

/*
class NativeApiThread extends Thread {

         String url;

         private static final String TAG = "NativeApiThread";

         NativeApiThread(String url) {
             this.url = url;
         }

         public void run() {
             Log.d(TAG, "spawning native call sendxt in new thread");
             String res = sendxt(this.url);
             Log.d(TAG, "native call sendxt has returned");

         }

         // encointer-api-native functions
         private native String sendxt(String to);

     }
     */
