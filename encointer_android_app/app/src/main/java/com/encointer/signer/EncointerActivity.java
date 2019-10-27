package com.encointer.signer;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.jakewharton.threetenabp.AndroidThreeTen;
import com.neovisionaries.ws.client.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigInteger;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EncointerActivity extends AppCompatActivity {
    private static final String TAG = "EncointerActivity";
    public static final String EXTRA_USERNAME = "com.encointer.signer.USERNAME";

    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET,
            };

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    WebSocket   ws = null;
    String      accountAddress = null;
    String      accountPhrase = null;
    BigInteger  accountNonce = null;
    BigInteger  aliceNonce = null;
    BigInteger  accountBalance = null;
    Integer     specVersion = null;
    String      genesisHash = null;
    Integer     subscriptionIdBlockHeaders = null;
    Integer     subscriptionIdNonce = null;
    Integer     subscriptionIdBalance = null;
    Integer     subscriptionIdEvents = null;
    Boolean     AliceHasPity = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);
        System.loadLibrary("encointer_api_native");
        initNativeLogger();
        /*try {
            mustThrowException();
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        // load preferences
        EditText editText_username = findViewById(R.id.editText_username);
        editText_username.setText(sharedPref.getString("username", "myusername"));
        EditText editText_url = findViewById(R.id.editText_url);
        String node_ws_url = sharedPref.getString("node_ws_url", "wss://poc3-rpc.polkadot.io/");
        editText_url.setText(node_ws_url);

        if (sharedPref.contains("account") == false) {
            Log.i(TAG, "no previously used account found. generating a new one");
            //sharedPref.edit().putString("account", "{\"phrase\": \"one two three\", \"address\": \"f5zhsAEDc\", \"pair\": \"0x1234\"}").apply();
            sharedPref.edit().putString("account", newAccount()).apply();
        }
        try {
            JSONObject jsonObj = new JSONObject(sharedPref.getString("account", "error_no_account_found"));
            accountAddress = jsonObj.getString("address");
            accountPhrase = jsonObj.getString("phrase");
            TextView tv_account_address = findViewById(R.id.account_address);
            tv_account_address.setText(accountAddress);
            Log.i(TAG, "successfully loaded account credentials");
        } catch (Exception e) {
            e.printStackTrace();
        }
        startWebsocket(node_ws_url);
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

    public void startWebsocket(String node_ws_url) {
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
                        if (jsonObj.has("method")) {
                            Integer subscription = jsonObj.getJSONObject("params")
                                .getInt("subscription");
                            if (subscription == subscriptionIdBlockHeaders) {

                                String blockNrHex = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getString("number");
                                BigInteger block_nr = new BigInteger(blockNrHex.substring("0x".length()), 16);
                                TextView tv_block_number = findViewById(R.id.block_number);
                                tv_block_number.setText(String.format("latest block number is %d", block_nr));
                                Log.i(TAG, String.format("latest block number is %s / %d", blockNrHex, block_nr));
                            } else if (subscription == subscriptionIdEvents) {
                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                for (int n = 0; n < changes.length(); n++) {
                                    if (changes.getJSONArray(n).getString(0)
                                            .contentEquals("0xcc956bdb7605e3547539f321ac2bc95c")) {
                                        String events = changes.getJSONArray(n).getString(1);
                                        Log.i(TAG, "got event update: " + events);
                                    }

                                }
                            } else if (subscription == subscriptionIdBalance) {

                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                try {
                                    String bal = changes.getJSONArray(0).getString(1);
                                    accountBalance = new BigInteger(bal.substring("0x".length()), 16);
                                } catch (Exception e) {
                                    Log.w(TAG, "balance is null");
                                    accountBalance = new BigInteger("0");
                                }
                                TextView tv_account_balance = findViewById(R.id.account_balance);
                                tv_account_balance.setText("Balance: " + accountBalance.toString());

                            } else if (subscription == subscriptionIdNonce) {
                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                try {
                                    String noncestr = changes.getJSONArray(0).getString(1);
                                    accountNonce = new BigInteger(noncestr.substring("0x".length()), 16);
                                } catch (Exception e) {
                                    Log.w(TAG, "nonce is null");
                                    accountNonce = new BigInteger("0");
                                }
                                TextView tv_account_nonce = findViewById(R.id.account_nonce);
                                tv_account_nonce.setText("Nonce: " + accountNonce.toString());

                            }
                        } else if (jsonObj.has("result")) {
                            Log.d(TAG, "is a result");
                            switch (jsonObj.getInt("id")) {
                                case 11:
                                    Log.d(TAG, "is block header subscription");
                                    subscriptionIdBlockHeaders = jsonObj.getInt("result");
                                    break;
                                case 12:
                                    Log.d(TAG, "is event subscription");
                                    subscriptionIdEvents = jsonObj.getInt("result");
                                    break;
                                case 13:
                                    Log.d(TAG, "is balance subscription");
                                    subscriptionIdBalance = jsonObj.getInt("result");
                                    break;
                                case 14:
                                    Log.d(TAG, "is nonce subscription");
                                    subscriptionIdNonce = jsonObj.getInt("result");
                                    break;
                                case 15:
                                    Log.d(TAG, "is runtime version");
                                    specVersion = jsonObj.getJSONObject("result")
                                            .getInt("specVersion");
                                    break;
                                case 16:
                                    Log.d(TAG, "is genesis hash");
                                    genesisHash = jsonObj.getString("result");
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (accountBalance != null) {
                        if (accountBalance.compareTo(BigInteger.TEN) < 0) {
                            if ((accountNonce != null)
                                    && (genesisHash != null)
                                    && (specVersion != null)
                                    && (AliceHasPity)) {
                                Log.i(TAG, "Alice has pity and sends you some");
                                AliceHasPity = false;
                                sendRpcRequest("bootstrap_funding", 27);
                            }
                        }
                    }
                }

                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
                    Log.i(TAG, "onConnected is executed");
                    System.loadLibrary("encointer_api_native");
                    Log.i(TAG, "subscribing to Alexander Block updates");
                    websocket.sendText("{\"id\":11,\"jsonrpc\":\"2.0\",\"method\":\"chain_subscribeNewHead\",\"params\":[]}");
                    sendRpcRequest("subscribe_nonce_for", 14);
                    sendRpcRequest("get_runtime_version", 15);
                    sendRpcRequest("get_genesis_hash", 16);
                    sendRpcRequest("subscribe_events", 12);
                    sendRpcRequest("subscribe_balance_for", 13);
                }
            });

            ws.connectAsynchronously();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void sendRpcRequest(String request, int id) {
        JSONObject args = new JSONObject();
        try {
            args.put("phrase", accountPhrase);
            args.put("id", id);
            args.put("genesis_hash", genesisHash);
            args.put("spec_version", specVersion);
            args.put("nonce", accountNonce);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String reqjson = getJsonReq(request, args.toString());
        Log.i(TAG,"sending rpc request: "+ reqjson);
        ws.sendText(reqjson);
    }

    public void startCeremony(View view) {
        Intent intent = new Intent(this, PersonCounter.class);
        EditText editText_username = findViewById(R.id.editText_username);
        String username = editText_username.getText().toString();

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().putString("username", username).apply();

        if (username.length() == 0) {
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
        if (ws != null) {
            ws.disconnect();
            ws = null;
        }
        startWebsocket(url);
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

    private native String initNativeLogger();
    private native String mustThrowException();
    private native String newAccount();
    private native String newClaim(String arg);
    private native String signClaim(String arg);
    private native String getJsonReq(String request, String arg);

}

