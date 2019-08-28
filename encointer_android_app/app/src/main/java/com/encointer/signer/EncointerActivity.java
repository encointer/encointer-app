package com.encointer.signer;


import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class EncointerActivity extends AppCompatActivity {

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.loadLibrary("encointer_api_native");
        //System.loadLibrary("rust");

        setContentView(R.layout.activity_main);

        //TextView dummyTextView = findViewById(R.id.dummyTextView);
        //dummyTextView.setText(hello("Rust Library"));
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

        if(url.length() == 0) {
            editText_url.setError("Please enter a valid username!");
        } else {
            String res = send_xt(url);
            TextView dummyTextView = findViewById(R.id.dummyTextView);
            dummyTextView.setText(res);

        }

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

    // encointer-api-native functions
    private static native String send_xt(final String to);

}
