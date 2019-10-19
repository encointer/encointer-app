package com.encointer.signer;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;


public class DevicesRecyclerViewAdapter extends RecyclerView.Adapter<DevicesRecyclerViewAdapter.ViewHolder> {

    private List<DeviceItem> deviceList;
    private Context parentContext;
    private String TAG;



    public DevicesRecyclerViewAdapter(List<DeviceItem> deviceList, Context context, String TAG) {
        this.deviceList = deviceList;
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
        LayoutInflater inflater = LayoutInflater.from(parentContext);
        View v = inflater.inflate(R.layout.recycler_view_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        final DeviceItem device = deviceList.get(position);
        holder.textView1.setText(String.format("%s (%s), %s", device.getEndpointName(), device.getEndpointId(), device.getAuthenticationStatus()));
        holder.textView2.setText(device.getServiceId());
        holder.textView3.setText(device.isAccountSignatureReceived());
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
        holder.layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast toast = Toast.makeText(parentContext, "Connecting to " + deviceList.get(position).getEndpointId(), Toast.LENGTH_SHORT);
                toast.show();
                ((DeviceList)parentContext).establishConnection(deviceList.get(position).getEndpointId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

}
