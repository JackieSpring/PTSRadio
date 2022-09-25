package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
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

    private class PingEntry implements Comparable<PingEntry> {
        public String id;
        public int timeout ;
        public PingEntry( String id ){
            this.id = id;
            this.timeout = PTSConstants.PING_TIMEOUT;
        }

        @Override
        public int compareTo(PingEntry pingEntry) {
            // 1    elem1 > elem2
            // 0    elem1 == elem2
            // -1   elem1 < elem2
            return this.timeout - pingEntry.timeout;
        }
    }

    private String radioID;
    private PTSListener callback;
    private ArrayList pingScheduler = new ArrayList<PingEntry>();
    private boolean pingSemaphore = false;

    public PingReciver( String id ) {
        radioID = id;
    }

    public void setListener( PTSListener cb ){
        callback = cb;
    }

    @Override
    public boolean trap(PTSPacket pk) {
        return false;
    }

    private void sortPingScheduler(){
        Collections.sort(pingScheduler, new Comparator<PingEntry>() {
            @Override
            public int compare(PingEntry p1, PingEntry p2) {
                return p1.compareTo(p2);
            }
        });
    }
}