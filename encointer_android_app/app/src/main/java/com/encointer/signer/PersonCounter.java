package com.encointer.signer;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

public class PersonCounter extends AppCompatActivity {

    public static final String EXTRA_PERSON_COUNTER = "com.encointer.signer.EXTRA_PERSON_COUNTER";
    public static final String EXTRA_USERNAME = "com.encointer.signer.USERNAME";

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
            nextIntent.putExtra(EXTRA_PERSON_COUNTER, person_counter);
            // Add username
            Intent oldIntent = getIntent();
            String username = oldIntent.getStringExtra(EncointerActivity.EXTRA_USERNAME);
            nextIntent.putExtra(EXTRA_USERNAME, username);

            Log.i( "personcounter", "Person counter: " + person_counter + ", Username: " + username);

            // Start activity
            startActivity(nextIntent);
        }
    }
}
