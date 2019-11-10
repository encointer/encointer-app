package com.encointer.signer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.jakewharton.threetenabp.AndroidThreeTen;


import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
//import java.time.LocalDateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DeviceList extends AppCompatActivity {

    private static final String TAG = "DeviceList";
    static final int PERFORM_MEETUP = 1;  // The request code
    public static final String EXTRA_ARGS = "com.encointer.signer.ARGS";

    private static final byte PAYLOAD_CLAIM = 1;
    private static final byte PAYLOAD_SIGNATURE = 2;
    private static final byte PAYLOAD_STATUS = 3;
    private static final byte STATUS_DONE = 1;

    private Integer PERSON_COUNTER;
    private String USERNAME;
    private String ARGS;
    private String claim;

    private Integer foundDevices = 0;
    private Integer signatureCounter = 0;

    private WifiManager wifiManager;
    private boolean wifiTurnOnAtExit = false;
    private ConnectionsClient connectionsClient; // Our handle to Nearby Connections
    private AdvertisingOptions advertisingOption ;
    private DiscoveryOptions discoveryOption;
    private Handler mHandler;

    private boolean retry = false;
    private boolean manageRetry = false;
    private boolean manageWifiOff = false;

    private Vibrator vib;
    private String test;

    private Identicon identicon = new Identicon();

    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    //private List<DeviceItem> devices = new ArrayList<>();
    private Map<String, DeviceItem> devices;

    private PublicKey mPublicKey;
    private PrivateKey mPrivateKey;

    // Invoked when nearby advertisers are discovered or lost
    // Requests a connection with a device as soon as it is discovered
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    if(info.getServiceId().equals("com.encointer.signer")) {
                        if (devices.containsKey(endpointId)) {
                            //we merely rediscovered an endpoint we already know
                            Log.i(TAG, "onEndpointFound: rediscovered endpoint we already know: " + info.getEndpointName() + "(id: " + endpointId + ")");
                        } else {
                            Log.i(TAG, "onEndpointFound: discovered new endpoint: " + info.getEndpointName() + "(id: " + endpointId + ")");
                            devices.put(endpointId, new DeviceItem(endpointId, info.getEndpointName(), info.getServiceId()));
                            updateFoundConnections();
                            mAdapter.notifyDataSetChanged();
                        }
                        //establishConnection(endpointId);
                    }
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    Log.i(TAG, "onEndpointLost: endpoint lost: " + endpointId);
                    if (devices.containsKey(endpointId)) {
                        DeviceItem item = devices.get(endpointId);
                        item.setConnected(false);
                        devices.put(endpointId, item);
                        mAdapter.notifyDataSetChanged();
                    } else {
                        Log.e(TAG, "lost and endpoint we didn't even know. this should never happen");
                    }
                }
            };

    // Invoked when discoverers request to connect to the advertiser
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i(TAG, "onConnectionInitiated: accepting connection from " + connectionInfo.getEndpointName() + "(id: " + endpointId + ")");
                    if (devices.containsKey(endpointId)) {
                        DeviceItem item = devices.get(endpointId);
                        item.setAuthenticationToken(connectionInfo.getAuthenticationToken());
                        devices.put(endpointId, item);
                        mAdapter.notifyDataSetChanged();
                        Toast toast = Toast.makeText(DeviceList.this, connectionInfo.getAuthenticationToken(), Toast.LENGTH_LONG);
                        toast.show();
                        // Automatically accept the connection on both sides.
                        connectionsClient.acceptConnection(endpointId, payloadCallback);
                    } else {
                        Log.e(TAG, "onConnectionInitiated with device we haven't even discovered yet. this should never happen");
                    }
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            Log.i(TAG, "onConnectionResult: connection to " + endpointId + " successful");
                            // We're connected! Can now start sending and receiving data.
                            DeviceItem item = devices.get(endpointId);
                            item.setConnected(true);
                            devices.put(endpointId, item);
                            mAdapter.notifyDataSetChanged();
                            try {
                                sendClaim(endpointId);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            Log.i(TAG, "onConnectionResult: connection to " + endpointId + " rejected");
                            // The connection was rejected by one or both sides.
                            break;
                        default:
                            Log.i(TAG, "onConnectionResult: connection to " + endpointId + " failed");
                            // The connection was broken before it was accepted.
                            Toast toast = Toast.makeText(DeviceList.this, "Connecting to " + endpointId + " failed", Toast.LENGTH_LONG);
                            toast.show();
                            break;
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Log.i(TAG, "onDisconnected: disconnected from the opponent");
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                    DeviceItem item = devices.get(endpointId);
                    item.setConnected(false);
                    devices.put(endpointId, item);
                    mAdapter.notifyDataSetChanged();
                }
            };

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                //  is called when the first byte of a Payload is received;
                //  it does not indicate that the entire Payload has been received.
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if(payload.asBytes() != null) {
                        byte [] payloadBytes = payload.asBytes();
                        if (payloadBytes.length > 1) {
                            DeviceItem item = devices.get(endpointId);
                            switch (payloadBytes[0]) {
                                case PAYLOAD_CLAIM:
                                    try {
                                        String endpointClaim = new String(stripPayload(payloadBytes), "UTF-8");
                                        Log.i(TAG, "onPayloadReceived: claim received from " + endpointId + ": "+ endpointClaim);
                                        item.setClaim(endpointClaim);
                                        devices.put(endpointId, item);
                                        mAdapter.notifyDataSetChanged();
                                        // let's get the user's attention
                                        vib.vibrate(200);
                                    }
                                    catch (UnsupportedEncodingException e ) { e.printStackTrace();}
                                    break;
                                case PAYLOAD_SIGNATURE:
                                    try {
                                        String endpointSignature = new String(stripPayload(payloadBytes), "UTF-8");
                                        Log.i(TAG, "onPayloadReceived: signature received from " + endpointId + ": "+ endpointSignature);
                                        item.setSignature(endpointSignature);
                                        devices.put(endpointId, item);
                                        mAdapter.notifyDataSetChanged();
                                        updateSignaturesCounter();
                                        //send STATUS
                                        byte [] msg = {PAYLOAD_STATUS, STATUS_DONE};
                                        connectionsClient.sendPayload(endpointId, Payload.fromBytes(msg));
                                    }
                                    catch (UnsupportedEncodingException e ) { e.printStackTrace();}
                                    break;
                                case PAYLOAD_STATUS:
                                    if (payloadBytes[1] == STATUS_DONE) {
                                        Log.i(TAG, "onPayloadReceived: our endpoint is done: "+ endpointId);
                                        item.setDone(true);
                                        if (item.hasSignature()) {
                                            Log.i(TAG, "onPayloadReceived: we are done too. closing connection");
                                            connectionsClient. disconnectFromEndpoint(endpointId);
                                            item.setConnected(false);
                                            devices.put(endpointId, item);
                                            mAdapter.notifyDataSetChanged();
                                        }
                                    }
                                    break;

                                default:
                                    Log.w(TAG,"onPayloadReceived: failed to identify payload by first byte");
                            }
                        }
                    }
                }

                //  is called with a status of PayloadTransferUpdate.Status.SUCCESS
                //  (or PayloadTransferUpdate.Status.ERROR in the case of an error).
                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    // Payload progress has updated.
                    // Bytes payloads are sent as a single chunk, so you'll receive a SUCCESS update immediately
                    // after the call to onPayloadReceived().

                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "lifecycle onCreate()");
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);

        System.loadLibrary("encointer_api_native");
        //initNativeLogger();

        devices = new HashMap<>();

        wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiTurnOnAtExit = wifiManager.isWifiEnabled();

        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_device_list);
        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();

        ARGS = intent.getStringExtra(EXTRA_ARGS);

        try {
            JSONObject args = new JSONObject(ARGS);
            PERSON_COUNTER = args.getInt("n_participants") - 1;
            USERNAME = args.getString("username");
            claim = newClaim(ARGS);
            Log.i(TAG, "created new claim: "+ claim);

        } catch (Exception e) {
            e.printStackTrace();
        }

        ImageView imageViewUser = (ImageView) findViewById(R.id.imageViewUser);
        imageViewUser.setImageBitmap(Identicon.create(USERNAME));

        updateSignaturesCounter();
        updateFoundConnections();

        // Set up android nearby service
        connectionsClient = Nearby.getConnectionsClient(this);
        advertisingOption = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        discoveryOption = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        connectionsClient.startAdvertising(USERNAME, "com.encointer.signer", connectionLifecycleCallback, advertisingOption); // Start advertising
        connectionsClient.startDiscovery("com.encointer.signer", endpointDiscoveryCallback, discoveryOption); // Start discovering
        //manages retries
        mHandler = new Handler();
        Log.i(TAG,"my instanceId is " + connectionsClient.getInstanceId());
        // Set up list of available devices
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view_device_list);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new DevicesRecyclerViewAdapter(devices,DeviceList.this, TAG);
        recyclerView.setAdapter(mAdapter);

        ((Button) findViewById(R.id.button_done)).setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                finalizeMeetup();
             }
        });
        Log.i(TAG, "onCreate: complete");
        ((Switch) findViewById(R.id.switch_advertising))
            .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG,"start advertising my nearby id");
                    connectionsClient.startAdvertising(USERNAME, "com.encointer.signer", connectionLifecycleCallback, advertisingOption);
                    connectionsClient.startDiscovery("com.encointer.signer", endpointDiscoveryCallback, discoveryOption); // Start discovering
                } else {
                    Log.i(TAG,"stop advertising my nearby id");
                    connectionsClient.stopAdvertising();
                    //connectionsClient.stopDiscovery();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "lifecycle onResume()");
        super.onResume();
        Log.i(TAG, "turning off WiFi to get better nearby connections");
        if (manageWifiOff) {
            wifiManager.setWifiEnabled(false);
        }
        // Start advertising
        connectionsClient.startAdvertising(USERNAME, "com.encointer.signer", connectionLifecycleCallback, advertisingOption);
        // Start discovering
        connectionsClient.startDiscovery("com.encointer.signer",endpointDiscoveryCallback, discoveryOption);
        if (manageRetry) {
            retry = true;
        }
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        Log.d(TAG, "lifecycle onPause()");
        super.onPause();
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        if (wifiTurnOnAtExit && manageWifiOff) {
            wifiManager.setWifiEnabled(true);
        }
        retry = false;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "lifecycle onStop()");
        super.onStop();
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        retry = false;
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "lifecycle onDestroy()");
        super.onDestroy();
        devices.clear();
        retry = false;
    }

    public void finalizeMeetup() {
        JSONObject args = new JSONObject();
        JSONArray witnesses = new JSONArray();
        try {
            for (DeviceItem item : devices.values()) {
                Log.d(TAG,"finalizeMeetup(): checking " + item.getEndpointName());
                if (item.hasSignature()) {
                    String sig = item.getSignature();
                    if (sig.length() > 1) {
                        Log.d(TAG,"finalizeMeetup(): adding " + item.getEndpointName() + " witness: " + item.getSignature());
                        witnesses.put(item.getSignature());
                    } else {
                        Log.e(TAG,"finalizeMeetup(): bad signature from " + item.getEndpointName() + " witness: " + item.getSignature());
                    }

                }
            }
            args.put("witnesses", witnesses);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Intent returnIntent = new Intent();
        returnIntent.putExtra(EXTRA_ARGS,args.toString());
        setResult(Activity.RESULT_OK,returnIntent);
        Log.i(TAG,"Activity DeviceList finishes now. returning result: " + args.toString());
        finish();
    }

    //
    // Update counters
    //
    public void updateFoundConnections() {
        foundDevices = devices.size();
        TextView textView = findViewById(R.id.textView_devices_found);
        String counter = "Devices found: " + foundDevices.toString() + "/" + PERSON_COUNTER.toString();
        textView.setText(counter);
        if (false) {//(foundDevices >= PERSON_COUNTER) {
            Log.i(TAG,"stop advertising my nearby id");
            connectionsClient.stopAdvertising();
            connectionsClient.stopDiscovery();
            ((Switch) findViewById(R.id.switch_advertising)).setChecked(false);
        }
    }


    public void updateSignaturesCounter() {
        signatureCounter = 0;
        for(DeviceItem item : devices.values()) {
            if(item.hasSignature()) {
                ++signatureCounter;
            }
        }
        TextView textView = findViewById(R.id.textView_devices_challenged);
        String counter = "Signatures collected: " + signatureCounter.toString() + "/" + PERSON_COUNTER.toString();
        textView.setText(counter);

 /*
        if(signatureCounter.equals(PERSON_COUNTER)) {
            try {
                FileOutputStream outputStream = openFileOutput("collectedSignatures", Context.MODE_PRIVATE);
                outputStream.write(listOfSignedDevices());
                outputStream.close();
                Log.i(TAG, "updateSignaturesCounter: all signatures collected and saved to phone storage");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            new AlertDialog.Builder(DeviceList.this)
                    .setTitle("All signatures collected")
                    .setMessage("Have you collected all attendees' signatures?")

                    // Specifying a listener allows you to take an action before dismissing the dialog.
                    // The dialog is automatically dismissed when a dialog button is clicked.
                    .setPositiveButton("End meetup", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Quit application
                            moveTaskToBack(true);
                        }
                    })
                    .setNegativeButton("Collect more", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Go back one screen where one can enter the number of attendees
                            Intent intent = new Intent(DeviceList.this, DeviceList.class);
                            intent.putExtra(EXTRA_ARGS, ARGS);
                            startActivity(intent);
                        }
                    })
                    .setNeutralButton("Sign others", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .show();
        }*/
    }

/*    private byte[] listOfSignedDevices() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        for(DeviceItem item : devices.values()) {
            if(item.hasSignature()) {
                oos.writeObject(item.getSignature());
            }
        }
        return bos.toByteArray();
    }

    public void resetSignaturesCounter() {
        signatureCounter = 0;
        TextView textView = findViewById(R.id.textView_devices_challenged);
        String counter = "Signatures collected: " + signatureCounter.toString() + "/" + PERSON_COUNTER.toString();
        textView.setText(counter);
    }
*/
    //
    // Clicky pressy
    //
    public void establishConnection(final String endpointId) {
        Log.i(TAG, "establishConnection: start connecting to " + endpointId);
        connectionsClient
                .requestConnection(USERNAME, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We successfully requested a connection. Now both sides
                            // must accept before the connection is established.
                            Log.i(TAG, "Connection to " + endpointId + " success. need to accept.");
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // Nearby Connections failed to request the connection.
                            Log.i(TAG, "Connection to " + endpointId + " failed with: ", e);
                            Toast toast = Toast.makeText(DeviceList.this, "establishConnection to " + endpointId + " failed", Toast.LENGTH_LONG);
                            toast.show();
                            if (retry) {
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast toast = Toast.makeText(DeviceList.this, "retrying connection to " + endpointId, Toast.LENGTH_LONG);
                                        toast.show();
                                        establishConnection(endpointId);
                                    }
                                }, (new Random()).nextInt(1000) + 300);
                            }
                        });
        DeviceItem item = devices.get(endpointId);
        item.setConnected(false);
        devices.put(endpointId, item);
    }

    public void sendSignature(String endpointId) throws NoSuchAlgorithmException, InvalidKeyException {
        try {
            DeviceItem item = devices.get(endpointId);
            JSONObject args = new JSONObject(ARGS);
            args.put("claim", item.getClaim());
            String endpointWitness = signClaim(args.toString());
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(tagPayload(PAYLOAD_SIGNATURE, endpointWitness.getBytes("UTF-8"))));
            Log.i(TAG, "sendSignature: signature sent to " + endpointId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendClaim(String endpointId) throws NoSuchAlgorithmException, InvalidKeyException {
        try {
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(tagPayload(PAYLOAD_CLAIM, claim.getBytes("UTF-8"))));
            Log.i(TAG, "sendClaim: claim (" + claim + ") sent to " + endpointId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte [] tagPayload(byte identifier, byte [] payload) {
        byte[] c = new byte[1 + payload.length];
        c[0] = identifier;
        System.arraycopy(payload, 0, c, 1, payload.length);
        return c;
    }
    public byte [] stripPayload(byte [] payload) {
        byte[] c = new byte[payload.length - 1];
        System.arraycopy(payload, 1, c, 0, payload.length - 1);
        return c;
    }

    public native String initNativeLogger();
    public native String newClaim(String arg);
    public native String signClaim(String arg);
}
