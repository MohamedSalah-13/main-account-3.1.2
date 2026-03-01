package com.hamza.controlsfx.serial;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;


@Getter
@Setter
public class AppleProduct implements Serializable {

    @Serial
    private static final long serialVersionUID = -5766619331630289388L;
    private String headphonePort;
    private String thunderboltPort;
    private String name;

}