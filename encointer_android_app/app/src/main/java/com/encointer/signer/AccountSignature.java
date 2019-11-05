package com.encointer.signer;

import java.io.Serializable;
import java.security.PublicKey;
import org.threeten.bp.LocalDateTime;


public class AccountSignature implements Serializable {

    // the witnesses public key used to sign the proof of personhood
    private PublicKey publicKey;
    // the number of attendantes as observed by witness
    private Integer attendantsCount;
    // witnessee's USERNAME whose personhood is to be proven
    private String id;
    // local time claimed by witness' phone
    private LocalDateTime localDateTime;

    public AccountSignature(PublicKey publicKey, Integer attendantsCount, String id, LocalDateTime localDateTime) {
        this.publicKey = publicKey;
        this.attendantsCount = attendantsCount;
        this.id = id;
        this.localDateTime = localDateTime;
    }

    public AccountSignature() {
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public Integer getAttendantsCount() {
        return attendantsCount;
    }

    public void setAttendantsCount(Integer attendantsCount) {
        this.attendantsCount = attendantsCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
