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

import java.util.Timer;
import java.util.TimerTask;

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

    private static final int TIMEOUT = PTSConstants.SERVICE_TIMEOUT;

    private PTSAudio audioio;
    private String callMember;
    private String callChannel;
    private String callStartSuffix;

    private boolean flagCallOpen = false;
    private boolean flagCallClosed = false;
    private boolean flagTalking = false;
    private boolean flagSemaphore = false;

    private TimerTask recivedRequestTimeout = new TimerTask() {
        @Override
        public void run() {
            waitSempahore();
            if ( flagServiceStarted && ! flagCallOpen && ! flagCallClosed )
                onTimeout();
        }
    };

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
        onRequestAccepted();
        serialio.write(SERVICE_ACCEPT + callMember);
        recivedRequestTimeout.cancel();
    }

    public void refuse() throws PTSCallIllegalStateException{
        onRequestRefused();
        serialio.write( SERVICE_REFUSE + selfID );
        recivedRequestTimeout.cancel();
    }

    public synchronized void talk(){
        waitSempahore();
        lockSemaphore();

        if ( flagCallOpen == false || flagCallClosed == true || flagServiceStarted == false ) {
            unlockSemaphore();
            throw new PTSCallIllegalStateException("Cannot talk for unexpected call state");
        }
        if ( flagTalking ) {
            unlockSemaphore();
            return;
        }

        audioio.talk();
        serialio.write( SERVICE_TALK );
        flagTalking = true;

        unlockSemaphore();
    }

    public synchronized void listen()  {
        waitSempahore();
        lockSemaphore();

        if ( flagCallOpen == false || flagCallClosed == true || flagServiceStarted == false ) {
            unlockSemaphore();
            throw new PTSCallIllegalStateException("Cannot talk for unexpected call state");
        }

        audioio.listen();
        serialio.write( SERVICE_LISTEN );
        flagTalking = false;

        unlockSemaphore();
    }

    public void stop() {
        waitSempahore();
        lockSemaphore();

        if ( flagCallOpen == false || flagCallClosed == true || flagServiceStarted == false ) {
            unlockSemaphore();
            throw new PTSCallIllegalStateException("Cannot talk for unexpected call state");
        }

        audioio.stop();
        flagTalking = false;

        unlockSemaphore();
    }

    public void quit() throws PTSCallIllegalStateException {
        onQuit();
        serialio.write( SERVICE_QUIT );
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

        if (  flagCallClosed || flagCallOpen ) {
            this.destroy();
            throw new PTSCallIllegalStateException("Timeout during illegal call state");
        }

        lockSemaphore();

        if ( audioio != null )
            audioio.close();
        flagCallOpen = false;
        flagCallClosed = true;
        this.destroy();
        emit( new PTSEvent( CALL_REQUEST_TIMEOUT) );

        unlockSemaphore();
    }

    private synchronized void onRequestRefused() throws PTSCallIllegalStateException {
        waitSempahore();

        if (  flagCallClosed || ! flagCallOpen ) {
            this.destroy();
            throw new PTSCallIllegalStateException("Cannot refuse call request");
        }

        lockSemaphore();

        if (audioio != null)
            audioio.close();
        audioio = null;
        flagCallOpen = false;
        flagCallClosed = true;
        this.destroy();
        emit( new PTSEvent( CALL_REFUSED) );

        unlockSemaphore();
    }

    private synchronized void onRequestAccepted() throws PTSCallIllegalStateException {
        waitSempahore();

        if ( flagCallOpen || flagCallClosed ) {
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

                if ( audioio != null )
                    audioio.close();
                audioio = null;
                flagCallOpen = false;
                flagCallClosed = true;
                flagTalking = false;
                this.destroy();
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
    public boolean startService(PTSSerial io, String id) throws PTSChatIllegalStateException {
        return startService(io, id, null);
    }

    public boolean startService(PTSSerial io, String id, PTSAudio aio) throws PTSCallIllegalStateException {
        return startService(io, id, aio, true);
    }

    public boolean startService(PTSSerial io, String id, @NonNull PTSAudio aio, boolean isStartingConnection) throws PTSCallIllegalStateException {
        if ( flagCallOpen || flagCallClosed ) {
            this.destroy();
            throw new PTSCallIllegalStateException();
        }

        if ( io == null || id == null || aio == null ) {
            this.destroy();
            return false;
        }

        if ( id.equals( callMember ) ) {
            this.destroy();
            PTSEvent errev = new PTSEvent(CALL_ERROR);
            errev.addPayloadElement( new PTSIllegalArgumentException("Illegal Id") );
            emit(errev);
            return false;
        }

        if ( ! super.startService(io, id) )
            return false;
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
            (new Timer()).schedule(recivedRequestTimeout, TIMEOUT * 1000);
            // TODO Handle Call recieving connection
            Log.e("PTSCall", "TODO: startService on recieving connection");
        }
        return true;
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
