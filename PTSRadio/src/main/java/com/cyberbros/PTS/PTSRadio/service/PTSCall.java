package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import androidx.annotation.NonNull;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.exception.PTSCallIllegalStateException;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatIllegalStateException;
import com.cyberbros.PTS.PTSRadio.exception.PTSIllegalArgumentException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRadioException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRuntimeException;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSAudio;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

/*
 new PTSCall("id") -> setListener -> startService(io,id) -> ChannelDiscover -> CHANNEL_FOUND -> send(REQUEST+ccc) -> requestOK -> AUDIO_MODE -> AcccB -> CALL_ACCEPTED

 new PTSCall("id") -> setListener -> startService(io,id,cnl,true) -> send(YES) -> AUDIO_MODE -> AcccA -> CALL_ACCEPTED

 */

/*
TODO
    è inutile distruggere e ricreare workerThread per chiamate successive dello stesso metodo,
    converrebbe assegnare un etichetta al thread e ricrearlo solo se il thread attivo ha un
    etichetta differente

 */

public class PTSCall extends PTSService {
// EVENTS
    public static final String
    CALL_ERROR = "call_error",
    CALL_NO_CHANNEL = "call_no_channel",
    CALL_ACCEPTED = "call_accepted",
    CALL_REFUSED = "call_refused",
    CALL_REQUEST_TIMEOUT = "call_request_timeout",
    CALL_CLOSED = "call_closed",
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
    SERVICE_REQUEST_CALL = PTSConstants.CMD_SERVICE_REQUEST_CALL,
    SERVICE_START_HOST_SUFFIX   = PTSConstants.CMD_CALL_START_HOST_SUFFIX,
    SERVICE_START_CLIENT_SUFFIX = PTSConstants.CMD_CALL_START_CLIENT_SUFFIX;

    private PTSAudio audioio;
    private String callMember;
    private String callChannel;
    private String callStartSuffix;

    private boolean flagCallOpen = false;
    private boolean flagCallClosed = false;
    private boolean flagTalking = false;
    private boolean flagSemaphore = false;


    public PTSCall(String target) {
        this(target, null);
    }
    public PTSCall(String target, String cnl) {
        super();
        if ( target.length() != PTSConstants.ID_LENGTH )
            throw new PTSRuntimeException("Invalid ID");
        callMember = target;
        callChannel = cnl;
    }

//#############################################################
//                  Call Public methods
//#############################################################
    public boolean isOpen() {
        synchronized (this) {
            waitSempahore();
            return this.flagCallOpen && this.flagServiceStarted;
        }
    }

    public String getID() {
        return this.selfID;
    }

    public String getMemberID(){
        return this.callMember;
    }

    public String getChannel(){
        return this.callChannel;
    }

    public void accept() throws PTSCallIllegalStateException {
        serialio.write(SERVICE_ACCEPT + callMember);
        onRequestAccepted();
    }

    public void refuse() throws PTSCallIllegalStateException{
        serialio.write( SERVICE_REFUSE + selfID );
        onRequestRefused();
    }

    public synchronized void talk(){
        waitSempahore();
        lockSemaphore();

        if ( flagCallOpen == false || flagCallClosed == true || flagServiceStarted == false )
            throw new PTSCallIllegalStateException("Cannot talk for unexpected call state");
        if ( flagTalking )
            return;

        serialio.write( SERVICE_TALK );
        audioio.talk();
        flagTalking = true;

        unlockSemaphore();
    }

    public synchronized void listen()  {
        waitSempahore();
        lockSemaphore();

        if ( flagCallOpen == false || flagCallClosed == true || flagServiceStarted == false )
            throw new PTSCallIllegalStateException("Cannot talk for unexpected call state");

        serialio.write( SERVICE_LISTEN );
        audioio.listen();
        flagTalking = false;

        unlockSemaphore();
    }

    public void stop() {
        waitSempahore();
        lockSemaphore();

        if ( flagCallOpen == false || flagCallClosed == true || flagServiceStarted == false )
            throw new PTSCallIllegalStateException("Cannot talk for unexpected call state");

        audioio.stop();
        flagTalking = false;

        unlockSemaphore();
    }

    public void quit() throws PTSCallIllegalStateException {
        serialio.write( SERVICE_QUIT );
        onQuit();
    }

//#############################################################
//                  MAIN TRAP
//#############################################################

    @Override
    public boolean trap(PTSPacket pk) {
        String action = pk.getAction();
        boolean isHandled = false;

        synchronized (this){
            try {
                waitSempahore();

                if (flagCallOpen) {
                    if ( PTSPacket.ACTION_PTT_GO.equals(action) ) {
                        if ( ! flagTalking ) {
                            emit(new PTSEvent(CALL_VOICE_FREE));
                            isHandled = true;
                        }
                    }
                    else if ( PTSPacket.ACTION_PTT_STOP.equals(action) ) {
                        emit( new PTSEvent(CALL_VOICE_BUSY) );
                        isHandled = true;
                    }
                } else {
                    //if ( PTSPacket.ACTION_UNKNOWN.equals(action) && ((String)pk.getPayloadElement(0)).substring(0, 6).equals(callMember + "Y") ){
                    // TODO Decommentare quando l'errore sul call accept viene corretto
                    if (PTSPacket.ACTION_SERVICE_YES.equals(action) && pk.getSource().equals( callMember ) && pk.getDestination().equals(selfID) ) {
                        onRequestAccepted();
                        isHandled = true;
                    }
                    else if (PTSPacket.ACTION_SERVICE_NO.equals(action) && pk.getSource().equals( callMember ) && pk.getDestination().equals(selfID)) {
                        onRequestRefused();
                        isHandled = true;
                    }
                    else if (PTSPacket.ACTION_SERVICE_TIMEOUT.equals(action) ) {
                        onTimeout();
                        isHandled = true;
                    }
                }
            } catch ( PTSCallIllegalStateException ex ){
                PTSEvent ev = new PTSEvent(CALL_ERROR);
                ev.addPayloadElement(ex);
                emit(ev);
                isHandled = true;
            }
        }
        return isHandled;
    }


//#############################################################
//                  Call Event Handlers
//#############################################################

    protected synchronized void onTimeout() throws PTSCallIllegalStateException {
        waitSempahore();

        if ( !flagServiceStarted ||  flagCallClosed || flagCallOpen ) {
            this.destroy();
            throw new PTSCallIllegalStateException("Timeout during illegal chat state");
        }

        lockSemaphore();

        audioio.close();
        this.destroy();
        flagCallOpen = false;
        flagCallClosed = true;
        emit( new PTSEvent( CALL_REQUEST_TIMEOUT) );

        unlockSemaphore();
    }

    private synchronized void onRequestRefused() throws PTSCallIllegalStateException {
        waitSempahore();

        if ( !flagServiceStarted ||  flagCallClosed || ! flagCallOpen ) {
            this.destroy();
            throw new PTSCallIllegalStateException("Cannot refuse call request");
        }

        lockSemaphore();

        audioio.close();
        audioio = null;
        this.destroy();
        flagCallOpen = false;
        flagCallClosed = true;
        emit( new PTSEvent( CALL_REFUSED) );

        unlockSemaphore();
    }

    private synchronized void onRequestAccepted() throws PTSCallIllegalStateException {
        waitSempahore();

        if ( !flagServiceStarted || flagCallOpen || flagCallClosed ) {
            this.destroy();
            throw new PTSCallIllegalStateException("Cannot accept call request");
        }

        BoardSetMode setAudioMode = new BoardSetMode( BoardSetMode.SET_BOARD_AUDIO_MODE, () -> {
            synchronized (PTSCall.this) {
                waitSempahore();
                lockSemaphore();

                Log.e("PTSCallonRequestAcce", SERVICE_START_PREFIX + callChannel + callStartSuffix);
                serialio.write(SERVICE_START_PREFIX + callChannel + callStartSuffix);
                flagCallOpen = true;
                audioio.start();
                emit(new PTSEvent(CALL_ACCEPTED));

                unlockSemaphore();
            }
        } );
        setAudioMode.setOnErrorCallback( () -> emit( new PTSEvent(CALL_ERROR) ) );
        this.addPrev(setAudioMode);
        setAudioMode.startService(serialio, selfID);
    }

    public synchronized void onQuit() throws PTSCallIllegalStateException {
        waitSempahore();

        if ( !flagServiceStarted || flagCallClosed || ! flagCallOpen ) {
            this.destroy();
            throw new PTSCallIllegalStateException("Cannot quit call service");
        }

        BoardSetMode setTextMode = new BoardSetMode( BoardSetMode.SET_BOARD_TEXT_MODE, () -> {
            synchronized ( PTSCall.this ) {
                waitSempahore();
                lockSemaphore();

                audioio.close();
                audioio = null;
                this.destroy();
                flagCallOpen = false;
                flagCallClosed = true;
                flagTalking = false;
                emit(new PTSEvent(CALL_CLOSED));

                unlockSemaphore();
            }
        } );
        setTextMode.setOnErrorCallback( () -> emit( new PTSEvent(CALL_ERROR) ) );
        this.addPrev(setTextMode);
        setTextMode.startService(serialio, selfID);
    }


//#############################################################
//                  Call Service Init/Fini
//#############################################################

    @Override
    public void destroy(){
        super.destroy();
        if ( ! flagCallClosed )
            if ( flagCallOpen )
                serialio.write( SERVICE_QUIT );
        if ( audioio != null )
            audioio.close();
        audioio = null;
        flagCallOpen = false;
        flagCallClosed = true;
    }

    @Override
    public void startService(PTSSerial io, String id) throws PTSChatIllegalStateException {
        startService(io, id, null);
    }

    public void startService(PTSSerial io, String id, PTSAudio aio) throws PTSCallIllegalStateException {
        startService(io, id, aio, true);
    }

    public void startService(PTSSerial io, String id, @NonNull PTSAudio aio, boolean isStartingConnection) throws PTSCallIllegalStateException {
        if ( flagCallOpen || flagCallClosed )
            throw new PTSCallIllegalStateException();

        if ( io == null || id == null )
            return;

        if ( id.equals( callMember ) ) {
            PTSEvent errev = new PTSEvent(CALL_ERROR);
            errev.addPayloadElement( new PTSIllegalArgumentException("Illegal Id") );
            emit(errev);
            return;
        }

        super.startService(io, id);
        audioio = aio;

        if ( isStartingConnection ) {
            callStartSuffix = SERVICE_START_HOST_SUFFIX;

            ChannelDiscover cd = new ChannelDiscover();
            cd.setListener( (PTSEvent event) -> {
                String action = event.getAction();
                if ( ChannelDiscover.CHANNEL_FOUND.equals(action) ){
                    String cnl = (String) event.getPayloadElement(0);
                    callChannel = cnl;
                    serialio.write( SERVICE_REQUEST_CALL + callMember + cnl);
                }
                else if ( ChannelDiscover.CHANNEL_NOT_FOUND.equals(action) ){
                    this.destroy();
                    emit( new PTSEvent(CALL_NO_CHANNEL) );
                }
            } );
            this.addPrev(cd);
            cd.startService(serialio, selfID);

            // TODO Handle Call starting connection
            Log.e("PTSCall", "TODO: startService on startingConnection");
        }
        else {
            if (callChannel == null || Integer.parseInt(callChannel) > PTSConstants.CALL_CHANNEL_LAST_CHANNEL ) {
                this.destroy();
                throw new PTSChatIllegalStateException("Channel not specified");
            }
            callStartSuffix = SERVICE_START_CLIENT_SUFFIX;
            // TODO Handle Call recieving connection
            Log.e("PTSCall", "TODO: startService on recieving connection");
        }
    }

//#############################################################
//                  Call Semaphore
//#############################################################
    private synchronized void waitSempahore(){
        try {
            if ( flagSemaphore )
                wait();
        } catch (InterruptedException e) {
            PTSEvent ev = new PTSEvent( CALL_ERROR );
            ev.addPayloadElement(e);
            emit( ev );
        }
    }

    private synchronized void lockSemaphore(){
        flagSemaphore = true;
    }

    private synchronized void unlockSemaphore(){
        flagSemaphore = false;
        notify();
    }
}
