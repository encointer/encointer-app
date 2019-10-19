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
    private AccountSignature accountSignature;
    private boolean isConnected;
    private byte[] signature;

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

    public DeviceItem(String endpointId, String endpointName, String serviceId, String authenticationToken, boolean isConnected, AccountSignature accountSignature, byte[] signature) {
        this.endpointId = endpointId;
        this.endpointName = endpointName;
        this.serviceId = serviceId;
        this.authenticationToken = authenticationToken;
        this.idPicture = Identicon.create(endpointName);
        this.isConnected = isConnected;
        this.accountSignature = accountSignature;
        this.signature = signature;
    }

    public String isAccountSignatureReceived() {
        if(accountSignature == null) {
            return null;
        } else {
            return "Account Signature received!";
        }
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

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public AccountSignature getAccountSignature() {
        return accountSignature;
    }

    public void setAccountSignature(AccountSignature accountSignature) {
        this.accountSignature = accountSignature;
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
