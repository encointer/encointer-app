package com.encointer.signer;

import java.io.Serializable;
import java.security.PublicKey;
import java.time.LocalDateTime;

public class AccountSignature implements Serializable {

    private PublicKey publicKey;
    private Integer attendantsCount;
    private String id;
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
