package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.exception.PTSCallIllegalStateException;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatIllegalStateException;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

public class PTSCall extends PTSService {
// EVENTS
    private static final String
    CALL_ERROR = "call_error",
    CALL_ACCEPTED = "call_accepted",
    CALL_REFUSED = "call_refused",
    CALL_REQUEST_TIMEOUT = "call_request_timeout",
    CALL_NO_CHANNEL = "call_no_channel",
    CALL_VOICE_BUSY = "call_voice_busy",
    CALL_VOICE_FREE = "call_voice_free";

//Commands
    private static final String
    SERVICE_ACCEPT  = PTSConstants.CMD_SERVICE_ACCEPT,
    SERVICE_REFUSE  = PTSConstants.CMD_SERVICE_REFUSE,
    SERVICE_QUIT    = PTSConstants.CMD_SERVICE_QUIT,
    SERVICE_TALK    = PTSConstants.CMD_CALL_TALK,
    SERVICE_LISTEN  = PTSConstants.CMD_CALL_LISTEN,
    SERVICE_START_PREFIX = PTSConstants.CMD_CALL_START_PREFIX,
    SERVICE_START_HOST_SUFFIX = PTSConstants.CMD_CALL_START_HOST_SUFFIX,
    SERVICE_START_CLIENT_SUFFIX = PTSConstants.CMD_CALL_START_CLIENT_SUFFIX;

    private String callMember;
    private String callChannel;
    private boolean flagCallOpen = false;
    private boolean flagCallClosed = false;
    private boolean flagChannelFound = false;
    private boolean flagSempahore = false;


    public PTSCall(String target) {
        super();
        callMember = target;
    }

//#############################################################
//                  Call Public methods
//#############################################################

    public void accept(){
        // TODO Call accept request
        Log.e( "PTSCall", "TODO: Call .accept() method" );
    }

    public void refuse(){
        // TODO Call refuse request
        Log.e( "PTSCall", "TODO: Call .refuse() method" );
    }

    public void talk(){
        // TODO Call talk request
        Log.e( "PTSCall", "TODO: Call .talk() method" );
    }

    public void listen()  {
        // TODO Call listen request
        Log.e( "PTSCall", "TODO: Call .listen() method" );
    }

    public void quit(){
        // TODO Call quit request
        Log.e( "PTSCall", "TODO: Call .quit() method" );
    }

//#############################################################
//                  MAIN TRAP
//#############################################################

    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();

        synchronized (this){
            try {
                if ( flagSempahore )
                    wait();

                if ( flagCallOpen ) {
                    // TODO Call open handler
                }
                else {
                    // TODO Call closed handler
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


//#############################################################
//                  Call Event Handlers
//#############################################################


//#############################################################
//                  Call Service Init
//#############################################################

    @Override
    public void startService(PTSSerial io, String id) throws PTSChatIllegalStateException {
        startService(io, id, true);
    }

    public void startService(PTSSerial io, String id, boolean isStartingConnection) throws PTSChatIllegalStateException {
        if ( flagCallOpen || flagCallClosed )
            throw new PTSCallIllegalStateException();

        if ( io == null || id == null )
            return;

        super.startService(io, id);
        if ( isStartingConnection )
            // TODO Handle Call starting connection
            Log.e( "PTSCall", "TODO: startService on startingConnection" );
        else
            // TODO Handle Call recieving connection
            Log.e( "PTSCall", "TODO: startService on recieving connection" );

    }
}
