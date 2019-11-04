package com.encointer.signer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.math.BigInteger;


public class EncointerChain extends Service {

    private static final String TAG = "EncointerChain";

    BigInteger accountNonce = null;

    // Binder given to clients
    private final IBinder binder = new EncointerChainBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class EncointerChainBinder extends Binder {
        EncointerChain getService() {
            // Return this instance of LocalService so clients can call public methods
            return EncointerChain.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        Log.i(TAG, "onDestroy EncointerChain");
        // Tell the user we stopped.
        Toast.makeText(this, "EncointerChain service stopped", Toast.LENGTH_SHORT).show();
    }


    /** method for clients */
    public int getAccountNonce() {
        return accountNonce.intValue();
    }

    public void setAccountNonce() {
        accountNonce = BigInteger.TEN;
    }


    public native String initNativeLogger();
    public native String mustThrowException();
    public native String newAccount();
    public native String newClaim(String arg);
    public native String signClaim(String arg);
    public native String getJsonReq(String request, String arg);


}
