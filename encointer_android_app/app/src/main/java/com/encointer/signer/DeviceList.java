package com.encointer.signer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.ImageView;
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


import org.apache.commons.lang3.SerializationUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAKeyGenParameterSpec;
//import java.time.LocalDateTime;
import org.threeten.bp.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DeviceList extends AppCompatActivity {

    private static final String TAG = "EncointerSigner";
    public static final String EXTRA_USERNAME = "com.encointer.signer.USERNAME";

    private Integer PERSON_COUNTER;
    private String USERNAME;

    private Integer foundDevices = 0;
    private Integer signatureCounter = 0;

    private ConnectionsClient connectionsClient; // Our handle to Nearby Connections
    private AdvertisingOptions advertisingOption ;
    private DiscoveryOptions discoveryOption;

    private String test;

    private Identicon identicon = new Identicon();

    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private List<DeviceItem> deviceList = new ArrayList<>();


    private PublicKey mPublicKey;
    private PrivateKey mPrivateKey;


    // Invoked when nearby advertisers are discovered or lost
    // Requests a connection with a device as soon as it is discovered
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    if(info.getServiceId().equals("com.encointer.signer")) {
                        Toast toast = Toast.makeText(DeviceList.this, "Found new Device", Toast.LENGTH_SHORT);
                        toast.show();
                        add(new DeviceItem(endpointId, info.getEndpointName(), info.getServiceId()));
                        // An endpoint was found. We request a connection to it.
                        Log.i(TAG, "onEndpointFound: endpoint found: " + info.getEndpointName());
                    }
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    try {
                        Log.i(TAG, "onEndpointLost: endpoint lost: " + endpointId);
                        if(getItemFromEndpointId(endpointId) != null) {
                            // A previously discovered endpoint has gone away.
                            // Remove it from the list if we don't have a signature.
                            if(getItemFromEndpointId(endpointId).getSignature() == null) {
                                remove(endpointId);
                            } else {
                                DeviceItem item = remove(endpointId);
                                add(new DeviceItem(item.getEndpointId(), item.getEndpointName(), item.getServiceId(), null, false, item.getAccountSignature(), item.getSignature()));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            };

    // Invoked when discoverers request to connect to the advertiser
    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.i(TAG, "onConnectionInitiated: accepting connection from " + connectionInfo.getEndpointName());
                    Toast toast = Toast.makeText(DeviceList.this, connectionInfo.getAuthenticationToken(), Toast.LENGTH_LONG);
                    toast.show();
                    setAuthenticationToken(endpointId, connectionInfo);
                    // Automatically accept the connection on both sides.
                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            Log.i(TAG, "onConnectionResult: connection successful");
                            // We're connected! Can now start sending and receiving data.
                            connectionEstablished(endpointId);
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            Log.i(TAG, "onConnectionResult: connection rejected");
                            // The connection was rejected by one or both sides.
                            break;
                        default:
                            Log.i(TAG, "onConnectionResult: connection failed");
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
                    try {
                        DeviceItem item = remove(endpointId);
                        add(new DeviceItem(item.getEndpointId(), item.getEndpointName(), item.getServiceId(), null, false, item.getAccountSignature(), item.getSignature()));
                    } catch (Exception e){
                        e.printStackTrace();
                    }

                }
            };

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    // A new payload is being sent over.
                    if(payload.asBytes() != null) {
                        SignedMessage signedMessage = SerializationUtils.deserialize(payload.asBytes());
                        collectSignature(endpointId, signedMessage);
                        updateSignaturesCounter();
                        Log.i(TAG, "onPayloadReceived: Signature collected");
                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    // Payload progress has updated.
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidThreeTen.init(this);
        setContentView(R.layout.activity_device_list);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        PERSON_COUNTER = Integer.parseInt(intent.getStringExtra(PersonCounter.EXTRA_PERSON_COUNTER)) - 1;
        USERNAME = intent.getStringExtra(PersonCounter.EXTRA_USERNAME);
        ImageView imageViewUser = (ImageView) findViewById(R.id.imageViewUser);
        imageViewUser.setImageBitmap(Identicon.create(USERNAME));

        // Set up android nearby service
        connectionsClient = Nearby.getConnectionsClient(this);
        advertisingOption = new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();
        discoveryOption = new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build();
        connectionsClient.startAdvertising(USERNAME, "com.encointer.signer", connectionLifecycleCallback, advertisingOption); // Start advertising
        connectionsClient.startDiscovery("com.encointer.signer", endpointDiscoveryCallback, discoveryOption); // Start discovering

        // Generate public / private key pair
        keyPairGeneration();

        // Reset connection and token counter
        resetFoundConnections();
        resetSignaturesCounter();

        // Set up list of available devices
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view_device_list);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new DevicesRecyclerViewAdapter(deviceList,DeviceList.this, TAG);
        recyclerView.setAdapter(mAdapter);
        Log.i(TAG, "onCreate: complete");

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start advertising
        connectionsClient.startAdvertising(USERNAME, "com.encointer.signer", connectionLifecycleCallback, advertisingOption);
        // Start discovering
        connectionsClient.startDiscovery("com.encointer.signer",endpointDiscoveryCallback, discoveryOption);
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        resetFoundConnections();
    }

    @Override
    protected void onStop() {
        super.onStop();
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        resetFoundConnections();
    }


    //
    // Update counters
    //
    public void updateFoundConnections(Integer number) {
        foundDevices += number;
        TextView textView = findViewById(R.id.textView_devices_found);
        String counter = "Devices found: " + foundDevices.toString() + "/" + PERSON_COUNTER.toString();
        textView.setText(counter);
    }

    public void resetFoundConnections() {
        foundDevices = 0;
        TextView textView = findViewById(R.id.textView_devices_found);
        String counter = "Devices found: " + foundDevices.toString() + "/" + PERSON_COUNTER.toString();
        textView.setText(counter);
    }

    public void updateSignaturesCounter() {
        signatureCounter = 0;
        for(DeviceItem item : deviceList) {
            if(item.getSignature() != null) {
                ++signatureCounter;
            }
        }
        TextView textView = findViewById(R.id.textView_devices_challenged);
        String counter = "Signatures collected: " + signatureCounter.toString() + "/" + PERSON_COUNTER.toString();
        textView.setText(counter);
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
                            intent.putExtra(EXTRA_USERNAME, USERNAME);
                            startActivity(intent);
                        }
                    })
                    .setNeutralButton("Sign others", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .show();
        }
    }

    private byte[] listOfSignedDevices() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        for(DeviceItem item : deviceList) {
            if(item.getSignature() != null) {
                SignedMessage signedMessage = new SignedMessage(item.getAccountSignature(), item.getSignature());
                oos.writeObject(signedMessage);
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


    //
    // Update device list
    //
    public void add(DeviceItem item) {
        try {
            if(getItemsFromEndpointName(item.getEndpointName()) != null) {
                Collection<DeviceItem> items = getItemsFromEndpointName(item.getEndpointName());
                updateFoundConnections((items.size() * -1));
                deviceList.removeAll(items);
            }
            updateFoundConnections(1);
            deviceList.add(item);
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DeviceItem remove(String endpointId) {
        DeviceItem item = getItemFromEndpointId(endpointId);
        try {
            if( getItemFromEndpointId(endpointId) != null) {
                updateFoundConnections(-1);
                deviceList.remove(getItemFromEndpointId(endpointId));
            }
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return item;
    }


    //
    // Device list utils
    //
    public DeviceItem getItemFromEndpointId(String endpointId) {
        for (DeviceItem item : deviceList) {
            if (item.getEndpointId().equals(endpointId)) {
                return item;
            }
        }
        return null;
    }

    public Collection<DeviceItem> getItemsFromEndpointName(String endpointName) {
        Collection<DeviceItem> items = new ArrayList<>();
        for (DeviceItem item : deviceList) {
            if (item.getEndpointName().equals(endpointName)) {
                items.add(item);
            }
        }
        return items;
    }


    //
    // Update device item
    //
    public void setAuthenticationToken(String endpointId, ConnectionInfo connectionInfo) {
        try {
            if(getItemFromEndpointId(endpointId) == null) {
                add(new DeviceItem(endpointId, connectionInfo.getEndpointName(), "com.encointer.signer", connectionInfo.getAuthenticationToken()));
            } else {
                DeviceItem item = remove(endpointId);
                add(new DeviceItem(item.getEndpointId(), item.getEndpointName(), item.getServiceId(), connectionInfo.getAuthenticationToken()));
            }
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void connectionEstablished(String endpointId) {
        try {
            DeviceItem item = remove(endpointId);
            add(new DeviceItem(item.getEndpointId(), item.getEndpointName(), item.getServiceId(), item.getAuthenticationToken(),true));
        } catch (Exception e) {
            e.printStackTrace();
        }
        mAdapter.notifyDataSetChanged();
    }

    public void collectSignature(String endpointId, SignedMessage signedMessage) {
        try {
            DeviceItem item = remove(endpointId);
            add(new DeviceItem(item.getEndpointId(), item.getEndpointName(), item.getServiceId(), item.getAuthenticationToken(),item.isConnected(), signedMessage.getAccountSignature(), signedMessage.getSignature()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        mAdapter.notifyDataSetChanged();
    }


    //
    // Clicky pressy
    //
    public void establishConnection(final String endpointId) {
        Log.i(TAG, "establishConnection: start connecting...");
        connectionsClient
                .requestConnection(USERNAME, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(
                        (Void unused) -> {
                            // We successfully requested a connection. Now both sides
                            // must accept before the connection is established.
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            // Nearby Connections failed to request the connection.
                            Log.i(TAG, "Connection failed.");
                        });
    }

    public void sendSignature(String endpointId) throws NoSuchAlgorithmException, InvalidKeyException {
        try {
            establishConnection(endpointId);
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(mPrivateKey);
            LocalDateTime localDateTime = LocalDateTime.now();
            // TODO
            // Add GPS location to signature
            AccountSignature accountSignature = new AccountSignature(mPublicKey, PERSON_COUNTER+1, endpointId, localDateTime);
            s.update(SerializationUtils.serialize(accountSignature));
            byte[] signature = s.sign();
            SignedMessage signedMessage = new SignedMessage(accountSignature, signature);
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(SerializationUtils.serialize(signedMessage)));
            Log.i(TAG, "sendSignature: signature sent");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    //
    // Security utils
    //
    public void keyPairGeneration() {
        KeyPair keys = null;
        try {
            RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(spec);
            keys = keyGen.generateKeyPair();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if(keys != null){
            mPublicKey = (PublicKey) keys.getPublic();
            mPrivateKey = (PrivateKey) keys.getPrivate();
        }
    }
}
