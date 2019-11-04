package com.encointer.signer;

public final class EncointerChainApi {
    static {
        System.loadLibrary("encointer_api_native");
    }

    public EncointerChainApi() {
        initNativeLogger();
    }

    public native String initNativeLogger();
    public native String mustThrowException();
    public native String newAccount();
    public native String newClaim(String arg);
    public native String signClaim(String arg);
    public native String getJsonReq(String request, String arg);
}
