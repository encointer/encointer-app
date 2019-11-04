package com.encointer.signer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;

public class PersonCounter extends AppCompatActivity {

    public static final String EXTRA_ARGS = "com.encointer.signer.ARGS";

    EncointerChain encointerChainService;
    boolean encointerChainBound = false;

    private ServiceConnection encointerChainConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            EncointerChain.EncointerChainBinder binder = (EncointerChain.EncointerChainBinder) service;
            encointerChainService = binder.getService();
            encointerChainBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            encointerChainBound = false;
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_counter);
    }

    public void confirmAttendants(View view) {
        Intent nextIntent = new Intent(this, DeviceList.class);
        // Add person counter
        EditText editText_person_counter = findViewById(R.id.editText_person_counter);
        String person_counter = editText_person_counter.getText().toString();

        if((person_counter.length() == 0) || (Integer.valueOf(person_counter) > 12) || (Integer.valueOf(person_counter) < 3)) {
            editText_person_counter.setError("Please enter the number of attending people between 3 and 12!");
        } else {
            Intent oldIntent = getIntent();
            try {
                JSONObject args = new JSONObject(oldIntent.getStringExtra(EXTRA_ARGS));
                args.put("n_participants", person_counter);
                nextIntent.putExtra(EXTRA_ARGS, args.toString());
                Log.i( "personcounter", "Args: " + args.toString());

                // Start activity
                startActivity(nextIntent);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
