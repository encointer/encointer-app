package com.encointer.signer;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class DevicesRecyclerViewAdapter extends RecyclerView.Adapter<DevicesRecyclerViewAdapter.ViewHolder> {

    private Map<String, DeviceItem> devices;
    //private List<DeviceItem> devices;
    private Context parentContext;
    private String TAG;

    // allows to access HashMap with a position
    private DeviceItem get_position(int position) {
        ArrayList<String> keys = new ArrayList<String>(this.devices.keySet());
        return this.devices.get(keys.get(position));
    }

    public DevicesRecyclerViewAdapter(Map<String, DeviceItem> devices, Context context, String TAG) {
        //List<DeviceItem> tempList = new ArrayList<DeviceItem>(devices.values());
        this.devices = devices;
        //this.devices = new ArrayList<DeviceItem>(devices.values());
        this.TAG = TAG;
        this.parentContext = context;
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textView1;
        public TextView textView2;
        public TextView textView3;
        public ImageView imageView1;
        public Button btnSendSignature;
        public View layout;

        public ViewHolder(View v) {
            super(v);
            layout = v;
            textView1 = (TextView) v.findViewById(R.id.textView1);
            textView2 = (TextView) v.findViewById(R.id.textView2);
            textView3 = (TextView) v.findViewById(R.id.textView3);
            btnSendSignature = (Button) v.findViewById(R.id.btn_send_signature);
            imageView1 = (ImageView) v.findViewById(R.id.imageView1);

        }
    }

    @Override
    public DevicesRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder called");
        LayoutInflater inflater = LayoutInflater.from(parentContext);
        View v = inflater.inflate(R.layout.recycler_view_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        Log.d(TAG, "onBindViwHolder called with position %d" + position);
        final DeviceItem device = get_position(position);
        holder.textView1.setText(String.format("%s (%s)", device.getEndpointName(), device.getEndpointId()));

        String connection = device.getAuthenticationToken();
        if (device.isConnected()) {
            connection += " (connected) ";
        } else {
            connection += " (disconnected) ";
        }
        holder.textView2.setText(connection);
        String status = "";
        if (device.hasClaim()) {
            status += "got claim ";
        }
        if (device.hasSignature()) {
            status += "got sign ";
        }
        holder.textView3.setText(status);
        holder.imageView1.setImageBitmap(device.getIdPicture());
        holder.btnSendSignature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    ((DeviceList)parentContext).sendSignature(device.getEndpointId());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }
            }
        });
        holder.btnSendSignature.setEnabled(device.hasClaim());
        holder.layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DeviceItem item = get_position(position);
                Toast toast = Toast.makeText(parentContext, "Connecting to " + item.getEndpointId(), Toast.LENGTH_SHORT);
                toast.show();
                ((DeviceList)parentContext).establishConnection(item.getEndpointId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

}
