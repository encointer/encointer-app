package com.encointer.signer;

import java.io.Serializable;

public class SignedMessage implements Serializable {
    private AccountSignature accountSignature;
    private byte[] signature;

    public SignedMessage(AccountSignature accountSignature, byte[] signature) {
        this.accountSignature = accountSignature;
        this.signature = signature;
    }

    public AccountSignature getAccountSignature() {
        return accountSignature;
    }

    public void setAccountSignature(AccountSignature accountSignature) {
        this.accountSignature = accountSignature;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }
}
