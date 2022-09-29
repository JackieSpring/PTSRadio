package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

import java.util.Timer;
import java.util.TimerTask;

public class BoardTextMode extends PTSService {
// EVENTS
    public static final String
    BOARD_REQUEST_CHAT = "board_request_chat",
    BOARD_REQUEST_GROUP = "board_request_group",
    BOARD_REQUEST_CALL = "board_request_call";

    public BoardTextMode(){
        super();
    }

    @Override
    public synchronized boolean trap(PTSPacket pk) {
        if ( !super.flagServiceStarted )
            return false;

        String action = pk.getAction();
        boolean isHandled = false;

        switch ( action ){
            case PTSPacket.ACTION_REQUEST_CALL:
                //TODO handle request
                Log.e( "BoardTextMode", "TODO: handle call requests" );
                break;
            case PTSPacket.ACTION_REQUEST_GROUP:
                //TODO handle request
                Log.e( "BoardTextMode", "TODO: handle group requests" );
                break;
            case PTSPacket.ACTION_REQUEST_CHAT:
                Log.e("BoardTextMode", "trap: selfID=" + super.selfID);
                if ( ! super.selfID.equals( pk.getDestination() ) )
                    break;
                onRequestChat(pk);
                isHandled = true;
                break;
        }
        return isHandled;
    }

//#############################################################
//                  Packet Handlers
//#############################################################

    private void onRequestChat( PTSPacket pk ){
        PTSChat chat = new PTSChat( pk.getSource() );
        chat.startService( serialio, selfID, false );
        this.addPrev(chat);
        PTSEvent ev = new PTSEvent(BOARD_REQUEST_CHAT);
        ev.addPayloadElement( chat );

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (chat){
                    if( ! chat.isOpen() )
                        chat.onTimeout();
                }
            }
        }, PTSConstants.SERVICE_TIMEOUT * 1000 );

        emit(ev);
    }

    @Override
    public void startService(PTSSerial io, String id) {
        super.serialio = io;
        super.selfID = id;
        super.flagServiceStarted = true;
    }
}
