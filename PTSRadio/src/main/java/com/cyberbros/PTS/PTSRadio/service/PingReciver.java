package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class PingReciver extends PTSPacketTrap {

    public static final String
    USER_ALIVE = "user_alive",
    USER_DEAD = "user_dead";

    private static final int PING_TIME = PTSConstants.PING_MAX_TIMEOUT + 5;

    private String radioID;
    private PTSListener callback;
    private Set<String> currentPing = new HashSet<String>();
    private Set<String> oldPing = new HashSet<String>();
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

        if ( PTSPacket.ACTION_SESSION_PING.equals(action) ){
            String host = (String)pk.getSource();
            if ( host.equals(radioID) )
                return false;

            currentPing.add(host);
            return true;
        }

        return false;
    }


    private void emit(PTSEvent event) {
        if ( callback != null )
            callback.handle(event);
    }
}