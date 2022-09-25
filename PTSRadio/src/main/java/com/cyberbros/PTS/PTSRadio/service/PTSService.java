package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

public abstract class PTSService extends PTSPacketTrap {

    protected PTSSerial serialio;
    protected PTSListener callback;
    protected String selfID;
    protected boolean flagServiceStarted = false;

    public void setListener( PTSListener l ){
        this.callback = l;
    }

    protected void emit(PTSEvent e){
        new Thread(() -> callback.handle(e) ).start();
    }

    @Override
    public abstract boolean trap(PTSPacket pk);

    public abstract void startService( PTSSerial io, String id );
}
