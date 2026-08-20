package com.agentic.urlshortener.exception;

public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String alias) {
        super("Alias '" + alias + "' is already taken or reserved");
    }
}
