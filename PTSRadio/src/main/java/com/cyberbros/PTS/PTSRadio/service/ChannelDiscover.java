package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

//
// TODO Migliorare sendChannelCheck()
//

public class ChannelDiscover extends PTSService {

    // Events
    public static final String
    CHANNEL_FOUND = "channel_found",
    CHANNEL_NOT_FOUND = "channel_not_found";

    // Commands
    private static final String
    BOARD_RESTART   = PTSConstants.CMD_BOARD_RESTART,
    BOARD_MODE_AUDIO= PTSConstants.CMD_BOARD_MODE_AUDIO,
    BOARD_MODE_TEXT = PTSConstants.CMD_BOARD_MODE_TEXT,
    CHANNEL_CHECK   = PTSConstants.CMD_CHANNEL_DISCOVER;

    private static final int START_CHANNEL = PTSConstants.CALL_CHANNEL_FIRST_CHANNEL;
    private static final int LAST_CHANNEL = PTSConstants.CALL_CHANNEL_LAST_CHANNEL;
    private static final int CHANNEL_INCREMENT = PTSConstants.CALL_CHANNEL_INCREMENT;
    private static final String CHANNEL_DIGITS = "3";

    private int channel = START_CHANNEL;
    private boolean flagFirstChannelFound = false;


    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();
        boolean isHandled = false;

        switch(action) {
            case PTSPacket.ACTION_CHANNEL_OK:
                if ( flagFirstChannelFound ) {
                    BoardSetMode setTextMode = new BoardSetMode(BoardSetMode.SET_BOARD_TEXT_MODE, () -> {
                        ChannelDiscover.this.destroy();
                        PTSEvent event = new PTSEvent( CHANNEL_FOUND );
                        event.addPayloadElement( String.format("%0"+ CHANNEL_DIGITS + "d", channel) );
                        emit( event );
                    });
                    setTextMode.setOnErrorCallback( () -> {
                        ChannelDiscover.this.destroy();
                        emit( new PTSEvent(CHANNEL_NOT_FOUND) );
                    } );
                    this.addPrev(setTextMode);
                    setTextMode.startService(serialio, selfID);
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
        if ( channel >= LAST_CHANNEL ) {
            this.destroy();
            PTSEvent event = new PTSEvent(CHANNEL_NOT_FOUND);
            emit(event);
            return;
        }
        sendChannelCheck( chnl );
    }

    private void sendChannelCheck( int chnl ) {

        // TODO Questo codice può essere migliorato, per ogni controllo canale devo reinstanziare il restarter,
        // TODO sarebbe sufficiente usarne uno solo controllabile da ChannelDiscover, ma in questo momento ho
        // TODO altre priorità.
        // TODO basterebbe anche solo un sistema di flag strutturato meglio

        BoardSetMode audioRestarter = new BoardSetMode( BoardSetMode.SET_BOARD_AUDIO_MODE, () -> {
            serialio.write( CHANNEL_CHECK + String.format("%0"+ CHANNEL_DIGITS + "d", chnl) );
        });
        audioRestarter.setOnErrorCallback( () -> {
            ChannelDiscover.this.destroy();
            emit( new PTSEvent(CHANNEL_NOT_FOUND) );
        } );
        this.addPrev(audioRestarter);
        audioRestarter.startService(serialio, selfID);
    }

    @Override
    public void startService(PTSSerial io, String id ) {
        super.startService(io, id);
        discoverChannel( START_CHANNEL );
    }

}
