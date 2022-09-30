package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

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

    public void startService( PTSSerial io, String id ) {
        serialio = io;
        selfID = id;
        flagServiceStarted = true;
    };

    protected void emit(PTSEvent e){
        // TODO DEBUG
        Log.e("PTSServiceEmit",e.getAction());
        new Thread(() -> callback.handle(e) ).start();
    }

    @Override
    protected boolean trapManager( PTSPacket pk ) {
        if ( flagServiceStarted )
            return super.trapManager(pk);
        return false;
    }

    @Override
    public abstract boolean trap(PTSPacket pk);
}
