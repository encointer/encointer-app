package com.encointer.signer;


import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
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
    public static final String EXTRA_ARGS = "com.encointer.signer.ARGS";
    static final int PERFORM_MEETUP = 1;  // The request code

    public static final int CEREMONY_PHASE_REGISTERING = 0;
    public static final int CEREMONY_PHASE_ASSIGNING = 1;
    public static final int CEREMONY_PHASE_WITNESSING = 2;


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

    String      ARGS = null;
    JSONArray   witnessesJson = null;
    Boolean     witnessesSent = false;

    String      node_ws_url = null;
    WebSocket   ws = null;
    String      accountAddress = null;
    String      accountPhrase = null;
    BigInteger  accountNonce = null;
    BigInteger  aliceNonce = null;
    BigInteger  accountBalance = null;
    Integer     specVersion = null;
    String      genesisHash = null;
    BigInteger  ceremonyPhase = null;
    BigInteger  ceremonyIndex = null;
    BigInteger  meetupIndex = null;

    Integer     subscriptionIdBlockHeaders = null;
    Integer     subscriptionIdNonce = null;
    Integer     subscriptionIdBalance = null;
    Integer     subscriptionIdEvents = null;
    Integer     subscriptionIdMeetupIndex = null;
    Integer     subscriptionIdCeremonyIndex = null;
    Integer     subscriptionIdCeremonyPhase = null;
    Integer     subscriptionIdRegisterParticipant = null;
    Integer     subscriptionIdRegisterWitnesses = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "lifecycle onCreate()");
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);

        System.loadLibrary("encointer_api_native");
        initNativeLogger();

        setContentView(R.layout.activity_main);

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        // load preferences
        EditText editText_username = findViewById(R.id.editText_username);
        editText_username.setText(sharedPref.getString("username", "myusername"));
        EditText editText_url = findViewById(R.id.editText_url);
        node_ws_url = sharedPref.getString("node_ws_url", "wss://poc3-rpc.polkadot.io/");
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
            Log.i(TAG, "successfully loaded account credentials for "+ accountAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (sharedPref.contains("meetup_result")) {

            try {
                JSONObject json = new JSONObject(sharedPref.getString("meetup_result", ""));
                witnessesJson = json.getJSONArray("witnesses");
                witnessesSent = false;
                Log.i(TAG, "found unsent witnesses from previous run: " + witnessesJson.toString());
            } catch (Exception e) { e.printStackTrace(); }
        }


        (findViewById(R.id.editText_url)).setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    EditText editText_url = findViewById(R.id.editText_url);
                    String url = editText_url.getText().toString();
                    sharedPref.edit().putString("node_ws_url", url).apply();
                    if (ws != null) {
                        ws.disconnect();
                        ws = null;
                    }
                    startWebsocket(url);

                }
            }
        });
        (findViewById(R.id.editText_username)).setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    EditText editText_username = findViewById(R.id.editText_username);
                    String username = editText_username.getText().toString();
                    if (username.length() == 0) {
                        editText_username.setError("Please enter a valid username!");
                    } else {
                        sharedPref.edit().putString("username", username).apply();
                    }
                }
            }
        });

        startWebsocket(node_ws_url);

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "lifecycle onDestroy()");
        super.onDestroy();

        if (ws != null) {
            ws.disconnect();
            ws = null;
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "lifecycle onStart()");
        super.onStart();

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
        }

/*        if (ws != null) {
            if (!ws.isOpen()) {
                Log.d(TAG, "re-opening websocket");
                startWebsocket(node_ws_url);
            }
        }*/
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "lifecycle onStop()");
        super.onStop();

        if (ws != null) {
            ws.disconnect();
        }
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        Log.i(TAG, "lifecycle onSaveInstanceState");
        if (witnessesSent) {
            Log.i(TAG, "witnesses have been finalized, will purge saved state");
            SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            sharedPref.edit().remove("meetup_result").apply();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "lifecycle onResume()");
        super.onResume();
 /*       if (ws != null) {
            if (!ws.isOpen()) {
                Log.d(TAG, "re-opening websocket");
                startWebsocket(node_ws_url);
            }
        }*/
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Log.i(TAG, "meetup result received. will store it until it is sent and finalized: " + data.getStringExtra(EXTRA_ARGS));

            SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            sharedPref.edit().putString("meetup_result", data.getStringExtra(EXTRA_ARGS)).apply();
            try {
                JSONObject json = new JSONObject(data.getStringExtra(EXTRA_ARGS));
                witnessesJson = json.getJSONArray("witnesses");
                witnessesSent = false;
                sendRpcRequest("register_witnesses", 47);
                Toast.makeText(getApplicationContext(), "meetup results have been stored and will be egistered onchain soon", Toast.LENGTH_LONG ).show();
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            Log.e(TAG,"meetup has been canceled");

        }
        startWebsocket(node_ws_url);
    }

    public void startWebsocket(String url) {
        // Create a WebSocket factory and set 5000 milliseconds as a timeout
        // value for socket encointerChainConnection.
        WebSocketFactory factory = new WebSocketFactory().setConnectionTimeout(5000);
        // Create a WebSocket. The timeout value set above is used.
        try {
            ws = factory.createSocket(url);
            ws.addListener(new WebSocketAdapter() {

                @Override
                public void onTextMessage(WebSocket websocket, String message) throws Exception {
                    Log.i(TAG, "onTextMessage: " + message);
                    try {
                        JSONObject jsonObj = new JSONObject(message);
                        if (jsonObj.has("method")) {
                            Integer subscription = jsonObj.getJSONObject("params")
                                .getInt("subscription");
                            if (subscription.equals(subscriptionIdBlockHeaders)) {

                                String blockNrHex = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getString("number");
                                BigInteger block_nr = new BigInteger(blockNrHex.substring("0x".length()), 16);
                                Log.i(TAG, String.format("latest block number is %s / %d", blockNrHex, block_nr));
                                update_block_number(block_nr);
                            } else if (subscription.equals(subscriptionIdEvents)) {
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
                            } else if (subscription.equals(subscriptionIdBalance)) {

                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                try {
                                    String bal = changes.getJSONArray(0).getString(1);
                                    accountBalance = from_little_endian_hexstring(bal);
                                } catch (Exception e) {
                                    Log.w(TAG, "balance is null");
                                    accountBalance = new BigInteger("0");
                                }
                                update_account_balance(accountBalance);

                            } else if (subscription.equals(subscriptionIdNonce)) {
                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                try {
                                    String noncestr = changes.getJSONArray(0).getString(1);
                                    accountNonce = from_little_endian_hexstring(noncestr);
                                } catch (Exception e) {
                                    Log.w(TAG, "nonce is null");
                                    accountNonce = new BigInteger("0");
                                }
                                update_account_nonce(accountNonce);
                            } else if (subscription.equals(subscriptionIdCeremonyPhase)) {
                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                try {
                                    String resstr = changes.getJSONArray(0).getString(1);
                                    ceremonyPhase = from_little_endian_hexstring(resstr);
                                } catch (Exception e) {
                                    Log.w(TAG, "CeremonyPhase is null");
                                    ceremonyPhase = new BigInteger("0");
                                }
                                update_ceremony_phase(ceremonyPhase);
                            } else if (subscription.equals(subscriptionIdCeremonyIndex)) {
                                JSONArray changes = jsonObj.getJSONObject("params")
                                        .getJSONObject("result")
                                        .getJSONArray("changes");
                                try {
                                    String resstr = changes.getJSONArray(0).getString(1);
                                    ceremonyIndex = from_little_endian_hexstring(resstr);
                                } catch (Exception e) {
                                    Log.w(TAG, "CeremonyPhase is null");
                                    ceremonyIndex = new BigInteger("0");
                                }
                                update_ceremony_index(ceremonyIndex);
                            } else if (subscription.equals(subscriptionIdRegisterParticipant)) {
                                if (jsonObj.getJSONObject("params")
                                        .getString("result").equals("ready")) {
                                    // do nothing until finalized
                                } else if (jsonObj.getJSONObject("params")
                                        .getJSONObject("result").has("finalized")) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(), "registration has been finalized", Toast.LENGTH_SHORT ).show();
                                        }
                                    });
                                    Log.i(TAG, "registration for ceremony xt has been finalized");
                                }
                            } else if (subscription.equals(subscriptionIdRegisterWitnesses)) {
                                if (jsonObj.getJSONObject("params")
                                        .getString("result").equals("ready")) {
                                    // do nothing until finalized
                                } else if (jsonObj.getJSONObject("params")
                                        .getJSONObject("result").has("finalized")) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(), "registration of witnesses has been finalized", Toast.LENGTH_SHORT ).show();
                                        }
                                    });
                                    witnessesSent = true;
                                    Log.i(TAG, "registration of witnesses xt has been finalized");
                                }
                            } else {
                                Log.w(TAG, "unknown subscription: "+ subscription.toString());
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
                                case 17:
                                    Log.d(TAG, "is ceremony index subscription");
                                    subscriptionIdCeremonyIndex = jsonObj.getInt("result");
                                    break;
                                case 18:
                                    Log.d(TAG, "is ceremony phase subscription");
                                    subscriptionIdCeremonyPhase = jsonObj.getInt("result");
                                    break;
                                case 31:
                                    Log.d(TAG, "is subscription for register_participant xt");
                                    subscriptionIdRegisterParticipant = jsonObj.getInt("result");
                                    break;
                                case 32:
                                    Log.d(TAG, "is meetup index");
                                    String resstr = jsonObj.getString("result");
                                    meetupIndex = from_little_endian_hexstring(resstr);
                                    update_meetup_index(meetupIndex);
                                    break;
                                case 47:
                                    Log.d(TAG, "is subscription for register_witnesses xt");
                                    subscriptionIdRegisterWitnesses = jsonObj.getInt("result");
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                 }

                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
                    Log.i(TAG, "lifecycle ws onConnected");
                    System.loadLibrary("encointer_api_native");
                    Log.i(TAG, "subscribing to Alexander Block updates");
                    websocket.sendText("{\"id\":11,\"jsonrpc\":\"2.0\",\"method\":\"chain_subscribeNewHead\",\"params\":[]}");
                    sendRpcRequest("subscribe_nonce_for", 14);
                    sendRpcRequest("get_runtime_version", 15);
                    sendRpcRequest("get_genesis_hash", 16);
                    sendRpcRequest("subscribe_events", 12);
                    sendRpcRequest("subscribe_balance_for", 13);
                    sendRpcRequest("subscribe_ceremony_index", 17);
                    sendRpcRequest("subscribe_ceremony_phase", 18);
                }

                @Override
                public void onDisconnected(WebSocket websocket,
                                           WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame,
                                           boolean closedByServer) throws Exception {
                    super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
                    Log.d(TAG, "lifecycle ws onDisconnected()");
                    // TODO here we could trigger reconnection if it wasn't deliberate
                }
            });

            ws.connectAsynchronously();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void update_account_balance(BigInteger value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv_account_balance = findViewById(R.id.account_balance);
                tv_account_balance.setText(value.toString());
            }
        });
    }

    public void update_account_nonce(BigInteger value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv_account_nonce = findViewById(R.id.account_nonce);
                tv_account_nonce.setText(value.toString());
            }
        });
    }

    public void update_block_number(BigInteger value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv_block_number = findViewById(R.id.block_number);
                tv_block_number.setText(value.toString());
            }
        });
    }

    public void update_meetup_index(BigInteger value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv_block_number = findViewById(R.id.meetup_index);
                tv_block_number.setText(value.toString());
            }
        });
    }


    public void update_ceremony_phase(BigInteger value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv_block_number = findViewById(R.id.ceremony_phase);
                Button register_button = (Button) findViewById(R.id.button_register);
                Button start_button = (Button) findViewById(R.id.button_start);
                switch (value.intValue()) {
                    case CEREMONY_PHASE_REGISTERING:
                        tv_block_number.setText("REGISTERING");
                        register_button.setEnabled(true);
                        start_button.setEnabled(false);
                        meetupIndex = null;
                        break;
                    case CEREMONY_PHASE_ASSIGNING:
                        tv_block_number.setText("ASSIGNING");
                        register_button.setEnabled(false);
                        start_button.setEnabled(false);
                        break;
                    case CEREMONY_PHASE_WITNESSING:
                        tv_block_number.setText("WITNESSING");
                        sendRpcRequest("get_meetup_index_for", 32);
                        register_button.setEnabled(false);
                        start_button.setEnabled(true);
                        if (!(witnessesJson == null)) {
                            if (!witnessesSent) {
                                Log.i(TAG, "sending pending witnesses: " + witnessesJson.toString());
                                sendRpcRequest("register_witnesses", 47);
                            }
                        }
                        break;
                }
            }
        });
    }

    public void update_ceremony_index(BigInteger value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv_block_number = findViewById(R.id.ceremony_index);
                tv_block_number.setText(value.toString());
                sendRpcRequest("get_meetup_index_for", 32);
            }
        });
    }

    // TODO: unit test
    public BigInteger from_little_endian_hexstring(String val) {
        Log.i(TAG, "little endian input:"+val);
        if (val == "null") {
            return BigInteger.ZERO;
        }
        StringBuilder target = new StringBuilder();
        for (int n = val.length()-2; n > 1; n=n-2) {
            target.append(val.charAt(n));
            target.append(val.charAt(n+1));
        }
        Log.i(TAG, "big endian output:"+target.toString());
        return new BigInteger(target.toString(),16);
    }

    public void sendRpcRequest(String request, int id) {
        JSONObject args = new JSONObject();
        try {
            args.put("phrase", accountPhrase);
            args.put("id", id);
            args.put("genesis_hash", genesisHash);
            args.put("spec_version", specVersion);
            args.put("ceremony_index", ceremonyIndex);
            args.put("meetup_index", meetupIndex);
            args.put("nonce", accountNonce);
            args.put("witnesses", witnessesJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String reqjson = getJsonReq(request, args.toString());
        Log.i(TAG,"sending rpc request: "+ reqjson);
        ws.sendText(reqjson);
    }

    public void startCeremony(View view) {
        if (meetupIndex == null) {
            Toast.makeText(getApplicationContext(), "can't start ceremony before knowing meetup index", Toast.LENGTH_SHORT ).show();
            return;
        }
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        Intent intent = new Intent(this, PersonCounter.class);
        JSONObject args = new JSONObject();
        try {
            args.put("username", sharedPref.getString("username","dummy"));
            args.put("phrase", accountPhrase);
            args.put("ceremony_index", ceremonyIndex);
            args.put("meetup_index", meetupIndex);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        intent.putExtra(EXTRA_ARGS, args.toString());
        startActivityForResult(intent, PERFORM_MEETUP);
    }

    public void registerParticipant(View view) {
        sendRpcRequest("register_participant", 31);
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

    public native String initNativeLogger();
    public native String mustThrowException();
    public native String newAccount();
    public native String getJsonReq(String request, String arg);
}

