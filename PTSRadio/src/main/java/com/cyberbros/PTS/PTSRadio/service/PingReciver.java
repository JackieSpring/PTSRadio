package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Queue;

public class PingReciver extends PTSPacketTrap {

    public static final String
    USER_ALIVE = "user_alive",
    USER_DEAD = "user_dead";

    private String radioID;
    private PTSListener callback;
    private boolean pingSemaphore = false;


    public PingReciver( String id ) {
        radioID = id;
    }


    public void setListener( PTSListener cb ){
        callback = cb;
    }

    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();

        return false;
    }


    private void emit(PTSEvent event) {
        if ( callback != null )
            callback.handle(event);
    }
}