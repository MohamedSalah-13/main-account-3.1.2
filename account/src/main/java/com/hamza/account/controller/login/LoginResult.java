package com.hamza.account.controller.login;

import com.hamza.account.model.domain.Users;

public record LoginResult(Status status, Users user) {

    public enum Status {
        SUCCESS,
        INVALID_CREDENTIALS,
        INACTIVE
    }

    public static LoginResult success(Users user) {
        return new LoginResult(Status.SUCCESS, user);
    }

    public static LoginResult invalidCredentials() {
        return new LoginResult(Status.INVALID_CREDENTIALS, null);
    }

    public static LoginResult inactive() {
        return new LoginResult(Status.INACTIVE, null);
    }
}
