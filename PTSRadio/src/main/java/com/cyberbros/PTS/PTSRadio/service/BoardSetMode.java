package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.exception.PTSException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRuntimeException;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

public class BoardSetMode extends PTSService {

    public static final String
    SET_BOARD_TEXT_MODE = PTSConstants.CMD_BOARD_MODE_TEXT,
    SET_BOARD_AUDIO_MODE = PTSConstants.CMD_BOARD_MODE_AUDIO;

    private static final String
    ORDER_BOARD_RESTART = PTSConstants.CMD_BOARD_RESTART,

    BANNER_BOARD_RESTARTED = "Serial check up STATUS ... OK",
    BANNER_BOARD_TEXT_MODE = "text",
    BANNER_BOARD_AUDIO_MODE = "audio";

    private static final int BANNERS_PREFIX_LENGTH = 1;

    private final String boardMode;
    private final Runnable callback;
    private Runnable onErrorCallback = null;



    public BoardSetMode() {
        this( SET_BOARD_TEXT_MODE, null );
    }

    public BoardSetMode( String mode ) {
        this( mode, null );
    }

    public BoardSetMode( String mode, Runnable cb ) {
        boardMode = mode;
        callback = cb;
    }


    public void setOnErrorCallback(Runnable ecb ){
        this.onErrorCallback = ecb;
    }

    @Override
    public void setListener(PTSListener l) {
        throw new PTSRuntimeException("BoardSetMode cannot have listeners");
    }

    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();
        boolean isHandled = false;

        switch(action) {
            case PTSPacket.ACTION_DEBUG:
                String msg = (String) pk.getPayloadElement(0);
                if (    msg.length() >= BANNERS_PREFIX_LENGTH + BANNER_BOARD_RESTARTED.length() &&
                        msg.substring(BANNERS_PREFIX_LENGTH, BANNERS_PREFIX_LENGTH + BANNER_BOARD_RESTARTED.length() ).equals(BANNER_BOARD_RESTARTED)) {
                    serialio.write(boardMode);
                    isHandled = true;
                } else if (msg.length() >= BANNERS_PREFIX_LENGTH + boardMode.length()){
                    String banner ;
                    int bannerLength;

                    if ( boardMode.equals(SET_BOARD_TEXT_MODE) )
                        bannerLength = BANNER_BOARD_TEXT_MODE.length();
                    else if ( boardMode.equals(SET_BOARD_AUDIO_MODE) )
                        bannerLength = BANNER_BOARD_AUDIO_MODE.length();
                    else {
                        this.destroy();
                        onErrorCallback.run();
                        break;
                    }

                    banner = msg.substring(BANNERS_PREFIX_LENGTH, BANNERS_PREFIX_LENGTH + bannerLength);

                    if ( banner.equals(BANNER_BOARD_TEXT_MODE) ||
                        banner.equals(BANNER_BOARD_AUDIO_MODE)) {
                        this.destroy();
                        callback.run();
                    }
                }
                break;
            case PTSPacket.ACTION_UNKNOWN:
                break;
            default :
                this.destroy();
                onErrorCallback.run();
                break;
        }

        return isHandled;
    }

    @Override
    public void startService(PTSSerial io, String id) {
        super.startService(io, id);
        serialio.write( ORDER_BOARD_RESTART );
    }
}
