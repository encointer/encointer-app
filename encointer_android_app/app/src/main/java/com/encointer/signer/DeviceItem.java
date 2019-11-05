package com.encointer.signer;

import android.graphics.Bitmap;

import java.io.Serializable;

public class DeviceItem {

    private String endpointId;
    private String endpointName;
    private String serviceId;
    private String authenticationToken;
//    private Identicon identicon = new Identicon();
    private Bitmap idPicture;
    private boolean isConnected;
    private String signature = "";
    private String claim = "";

    public DeviceItem(String endpointId, String endpointName, String serviceId) {
        this.endpointId = endpointId;
        this.endpointName = endpointName;
        this.serviceId = serviceId;
        this.authenticationToken = null;
        this.idPicture = Identicon.create(endpointName);
        this.isConnected = false;
    }

    public DeviceItem(String endpointId, String endpointName, String serviceId, String authenticationToken) {
        this.endpointId = endpointId;
        this.endpointName = endpointName;
        this.serviceId = serviceId;
        this.authenticationToken = authenticationToken;
        this.idPicture = Identicon.create(endpointName);
        this.isConnected = false;
    }

    public DeviceItem(String endpointId, String endpointName, String serviceId, String authenticationToken, boolean isConnected) {
        this.endpointId = endpointId;
        this.endpointName = endpointName;
        this.serviceId = serviceId;
        this.authenticationToken = authenticationToken;
        this.idPicture = Identicon.create(endpointName);
        this.isConnected = isConnected;
    }

    public DeviceItem(String endpointId, String endpointName, String serviceId, String authenticationToken, boolean isConnected, String claim, String signature) {
        this.endpointId = endpointId;
        this.endpointName = endpointName;
        this.serviceId = serviceId;
        this.authenticationToken = authenticationToken;
        this.idPicture = Identicon.create(endpointName);
        this.isConnected = isConnected;
        this.claim = claim;
        this.signature = signature;
    }

    public boolean hasSignature() {
        return (!signature.equals(""));
    }

    public boolean hasClaim() {
        return (!claim.equals(""));
    }

    public String getAuthenticationStatus() {
        if(getAuthenticationToken() == null) {
            return "disconnected";
        } else {
            return getAuthenticationToken();
        }
    }

    @Override
    public String toString() {
        return getEndpointId() + ", " + getEndpointName() + ", " + getServiceId();
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setIdPicture(Bitmap idPicture) {
        this.idPicture = idPicture;
    }

    public String getSignature() {
        return signature;
    }

    public String getClaim() {
        return claim;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public Bitmap getIdPicture() {
        return idPicture;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public String getAuthenticationToken() {
        return authenticationToken;
    }

    public void setAuthenticationToken(String authenticationToken) {
        this.authenticationToken = authenticationToken;
    }

}
