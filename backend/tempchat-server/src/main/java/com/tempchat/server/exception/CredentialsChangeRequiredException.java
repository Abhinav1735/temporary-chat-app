package com.tempchat.server.exception;

public class CredentialsChangeRequiredException
        extends RuntimeException {

    public CredentialsChangeRequiredException() {
        super("Credential change is required before accessing this resource");
    }
}