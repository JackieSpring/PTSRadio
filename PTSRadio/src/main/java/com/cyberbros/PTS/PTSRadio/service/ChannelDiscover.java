package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

/*
*   TODO STA classe è un bordello, rifalla catzooooo
*   Differenzia funzioni del primo
*  */

public class ChannelDiscover extends PTSService {

    // Events
    public static String
    CHANNEL_FOUND = "channel_found",
    CHANNEL_NOT_FOUND = "channel_not_found";

    // Commands
    private static String
    BOARD_RESTART = "R",
    BOARD_MODE_AUDIO = "A",
    BOARD_MODE_TEXT = "T",
    CHANNEL_CHECK = "S";

    private static int START_CHANNEL = PTSConstants.CALL_CHANNEL_FIRST_CHANNEL;
    private static int LAST_CHANNEL = PTSConstants.CALL_CHANNEL_LAST_CHANNEL;
    private static int CHANNEL_INCREMENT = PTSConstants.CALL_CHANNEL_INCREMENT;
    private static String CHANNEL_DIGITS = "3";

    private int channel = START_CHANNEL;
    private boolean flagFirstChannelFound = false;


    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();
        boolean isHandled = false;

        switch(action) {
            case PTSPacket.ACTION_DEBUG:
                String msg = (String) pk.getPayloadElement(0);
                if ( msg.length() >= 30 && msg.substring(1, 30).equals("Serial check up STATUS ... OK"))
                    serialio.write(BOARD_MODE_AUDIO);
                break;
            case PTSPacket.ACTION_CHANNEL_OK:
                if ( flagFirstChannelFound ) {
                    this.destroy();
                    serialio.write(BOARD_MODE_TEXT);

                    PTSEvent event = new PTSEvent( CHANNEL_FOUND );
                    event.addPayloadElement( String.format("%0"+ CHANNEL_DIGITS + "u", channel) );
                    emit( event );
                }
                else {
                    flagFirstChannelFound = true;
                    discoverChannel( channel + 1 );
                }
                isHandled = true;
                break;

            case PTSPacket.ACTION_CHANNEL_KO:
                flagFirstChannelFound = false;
                channel += CHANNEL_INCREMENT;
                discoverChannel( channel );
                isHandled = true;
                break;
        }
        return isHandled;
    }


    private void discoverChannel( int chnl ) {
        Log.e("ChannelDiscover", "Looking for: " + chnl);
        if ( channel >= LAST_CHANNEL ) {
            this.destroy();
            PTSEvent event = new PTSEvent(CHANNEL_NOT_FOUND);
            emit(event);
            return;
        }
        serialio.write( CHANNEL_CHECK + String.format("%0"+ CHANNEL_DIGITS + "d", chnl) );
    }

    @Override
    public void startService(PTSSerial io, String id ) {
        super.startService(io, id);
        serialio.write( BOARD_RESTART );
        discoverChannel( START_CHANNEL );
    }

}
