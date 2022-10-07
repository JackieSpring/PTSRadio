package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class PingReciver extends PTSPacketTrap {

    public static final String
    USER_ALIVE = "user_alive",
    USER_DEAD = "user_dead";

    private static final int PING_TIME = PTSConstants.PING_MAX_TIMEOUT;

    private String radioID;
    private PTSListener callback;
    //private Set<String> currentPing = new HashSet<String>();
    //private Set<String> oldPing = new HashSet<String>();
    private HashMap< String, TimerTask > pingTable ;
    private Timer timer;
    private boolean pingSemaphore = false;


    public PingReciver( String id ) {
        radioID = id;
        timer = new Timer();
        pingTable = new HashMap();
    }


    public void setListener( PTSListener cb ){
        callback = cb;
    }

    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();

        if ( PTSPacket.ACTION_SESSION_PING.equals(action) ){
            String host = (String)pk.getSource();
            TimerTask pingTimeoutTask;

            if ( host.equals(radioID) )
                return false;

            pingTimeoutTask = new TimerTask() {
                @Override
                public void run() {
                    if ( ! pingTable.containsKey(host) )
                        return;
                    pingTable.remove(host);
                    PTSEvent ev = new PTSEvent(USER_DEAD);
                    ev.addPayloadElement(host);
                    emit(ev);
                }
            };

            if ( ! pingTable.containsKey( host ) ) {
                pingTable.put(host, pingTimeoutTask);
                timer.schedule(pingTimeoutTask, PING_TIME * 1000 );
                PTSEvent ev = new PTSEvent( USER_ALIVE );
                ev.addPayloadElement( host );
                emit(ev);
            }
            else {
                pingTable.get( host ).cancel();
                pingTable.put( host, pingTimeoutTask );
                timer.schedule( pingTimeoutTask, PING_TIME * 1000 );
            }

            return true;
        }

        return false;
    }


    private void emit(PTSEvent event) {
        if ( callback != null )
            callback.handle(event);
    }
}