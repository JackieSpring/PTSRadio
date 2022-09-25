package com.cyberbros.PTS.PTSRadio.exception;

public class PTSRuntimeException extends RuntimeException {
    public PTSRuntimeException(String msg){
        super(msg);
    }
    public PTSRuntimeException(){
        super();
    }
}
