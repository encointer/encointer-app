package com.encointer.signer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;

public class PersonCounter extends AppCompatActivity {

    private static final String TAG = "PersonCounter";
    public static final String EXTRA_ARGS = "com.encointer.signer.ARGS";
    static final int PERFORM_MEETUP = 1;  // The request code

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_counter);

    }

    @Override
    protected void onResume() {
        super.onResume();
        EditText userNameText = (EditText) findViewById(R.id.editText_person_counter);
        userNameText.setFocusable(true);
        userNameText.setFocusableInTouchMode(true);
        userNameText.requestFocus();
        userNameText.postDelayed(new Runnable(){
                                     @Override public void run(){
                                         InputMethodManager keyboard=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                                         keyboard.showSoftInput(userNameText,0);
                                     }
                                 }
                ,200);
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
                args.put("n_participants", Integer.valueOf(person_counter));
                nextIntent.putExtra(EXTRA_ARGS, args.toString());
                Log.i( "personcounter", "Args: " + args.toString());

                // Start activity
                startActivityForResult(nextIntent, PERFORM_MEETUP);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == PERFORM_MEETUP) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                //just forward intent
                setResult(Activity.RESULT_OK,data);
                Log.i(TAG,"Activity PersonCounter finishes now. forwarding result");
                finish();

            } else {
                setResult(Activity.RESULT_CANCELED, data);
                Log.e(TAG,"Got result canceled. Activity PersonCounter finishes now.");
                finish();
            }
        }
    }


}
