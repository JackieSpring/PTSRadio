package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.PTSRadio;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatException;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatIllegalStateException;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSMessageBuilder;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;
import com.cyberbros.PTS.PTSRadio.service.PTSService;

import java.io.IOException;
import java.util.Iterator;

/*
--- EVENTS ---
    CHAT_ERROR              Errore generico durante una procedura
    CHAT_ACCEPTED           Sessione chat aperta e pronta a comunicare
    CHAT_REFUSED            Richiesta di inizio chat inviata, richiesta rifiutata dal client
    CHAT_REQUEST_TIMEOUT    Richiesta di inizio chat inviata, ma non si è ottenuta risposta
    CHAT_MESSAGE            Sessione chat attiva ha ricevuto un messaggio dal client
    CHAT_CLOSED             Sessione chat terminata

--- METHODS ---
    setListener( PTSListener )
    accept()
    refuse()
    send( String )
    isOpen()
    quit()
    getID()
    getMemberID()

--- HOWTO ---
    PTSChat chat = new PTSChat("12345");
    chat.setListener( (PTSEvent event) -> {
        String action = event.getAction();
        switch(action){
            ...
            case PTSChat.CHAT_ACCEPTED:
                // Inizia attività
                break;
            case PTSChat.CHAT_ERROR:
                // Notifica errore
                break;
            ...
        }
    });
    radio.startService(chat);
 */

public class PTSChat extends PTSService {
// EVENTS
    public static final String
    CHAT_ERROR = "chat_error",
    CHAT_ACCEPTED = "chat_accepted",
    CHAT_REFUSED = "chat_refused",
    CHAT_REQUEST_TIMEOUT = "chat_request_timeout",
    CHAT_MESSAGE = "chat_message",
    CHAT_CLOSED = "chat_closed";

// Commands
    private static final String
    SERVICE_ACCEPT      = PTSConstants.CMD_SERVICE_ACCEPT,
    SERVICE_REFUSE      = PTSConstants.CMD_SERVICE_REFUSE,
    SERVICE_QUIT        = PTSConstants.CMD_SERVICE_QUIT,
    SERVICE_MESSAGE     = PTSConstants.CMD_SERVICE_MESSAGE,
    SERVICE_REQUEST_CHAT= PTSConstants.CMD_SERVICE_REQUEST_CHAT;

    private String chatMember;
    private PTSMessageBuilder mb;
    private boolean flagChatOpen = false;
    private boolean flagChatClosed = false;
    private boolean flagSemaphore = false;



    public PTSChat( String target ){
        super();
        chatMember = target;
        mb = new PTSMessageBuilder();
    }


//#############################################################
//                  Chat Public Methods
//#############################################################
    public boolean isOpen() {
        synchronized (this) {
            waitSempahore();
            return this.flagChatOpen && this.flagServiceStarted;
        }
    }

    public String getID() {
        return this.selfID;
    }

    public String getMemberID(){
        return this.chatMember;
    }

    public void accept() throws PTSChatIllegalStateException {
        serialio.write( SERVICE_ACCEPT + chatMember );
        onRequestAccepted();
    }

    public void refuse() throws PTSChatIllegalStateException {
        onRequestRefused();
        serialio.write( SERVICE_REFUSE + chatMember );
        this.destroy();
    }

    public void quit() throws PTSChatIllegalStateException {
        onQuit();
        serialio.write( SERVICE_QUIT );
    }

    public void send(String msg) {
        try {
            for (Iterator<byte[]> it = mb.msgToPkt(msg); it.hasNext(); ) {
                byte[] data = it.next();
                serialio.write(SERVICE_MESSAGE + new String(data) );
            }
        } catch (IOException e) {
            //TODO Message error handling
            Log.e("PTSChat", "TODO: ERROR sending message");
        }
    }

//#############################################################
//                  MAIN TRAP
//#############################################################
    @Override
    public boolean trap(PTSPacket pk) {

        Log.e("PTSChat", String.valueOf(pk));

        if ( ! flagServiceStarted || flagChatClosed )
            return false;

        boolean isHandled = false;
        String action = pk.getAction();
        String member = pk.getSource();
        String dest = pk.getDestination();

        synchronized (this) {
            waitSempahore();

            if (flagChatOpen) {
                try {
                    if (PTSPacket.ACTION_MESSAGE.equals(action) && member.equals(chatMember) ) {
                        String msg = mb.pktToMsg( (String)pk.getPayloadElement(0), this.chatMember );
                        if ( msg != null ) {
                            //TODO handle message and message protocol
                            Log.e("PTSChat", "TODO: handle message recived");
                            PTSEvent ev = new PTSEvent(CHAT_MESSAGE);
                            ev.addPayloadElement( msg );
                            emit(ev);
                        }
                        /*
                        PTSEvent ev = new PTSEvent(CHAT_MESSAGE);
                        ev.addPayloadElement( pk.getPayloadElement(0) );
                        emit(ev);*/
                        isHandled = true;
                    } else if (PTSPacket.ACTION_SERVICE_QUIT.equals(action) && member.equals(chatMember)) {
                        onQuit();
                        emit( new PTSEvent(CHAT_CLOSED) );
                        isHandled = true;
                    }
                }
                catch ( PTSChatIllegalStateException ex ){
                    PTSEvent ev = new PTSEvent( CHAT_ERROR );
                    ev.addPayloadElement( ex );
                    emit( ev );
                    isHandled = true;
                }
            } else {

                try {
                    if (PTSPacket.ACTION_SERVICE_YES.equals(action) && member.equals(chatMember) && dest.equals(selfID) ) {
                        onRequestAccepted();
                        isHandled = true;
                    } else if (PTSPacket.ACTION_SERVICE_NO.equals(action) && member.equals(chatMember) && dest.equals(selfID) ) {
                        onRequestRefused();
                        isHandled = true;
                    } else if (PTSPacket.ACTION_SERVICE_TIMEOUT.equals(action)) {
                        onTimeout();
                        isHandled = true;
                    }
                }
                catch ( PTSChatIllegalStateException ex ){
                    PTSEvent ev = new PTSEvent( CHAT_ERROR );
                    ev.addPayloadElement( ex );
                    emit( ev );
                    isHandled = true;
                }
            }
        }
        return isHandled;
    }


//#############################################################
//                  Chat Event Handlers
//#############################################################
    protected synchronized void onTimeout()  throws PTSChatIllegalStateException {
        synchronized (this) {
            waitSempahore();
            lockSemaphore();

            if ( ! flagServiceStarted || flagChatClosed || flagChatOpen  ) {
                unlockSemaphore();
                this.destroy();
                throw new PTSChatIllegalStateException("Timeout during illegal chat state");
            }
            this.destroy();
            flagChatClosed = true;
            emit( new PTSEvent( CHAT_REQUEST_TIMEOUT ) );

            unlockSemaphore();
        }
    }

    protected synchronized void onRequestAccepted() throws PTSChatIllegalStateException {
        synchronized (this){
            waitSempahore();
            lockSemaphore();

            if ( ! flagServiceStarted || flagChatClosed || flagChatOpen  ){
                unlockSemaphore();
                this.destroy();
                throw new PTSChatIllegalStateException("Cannot accept chat request");
            }
            flagChatOpen = true;
            emit( new PTSEvent( CHAT_ACCEPTED ) );

            unlockSemaphore();
        }
    }

    protected synchronized void onRequestRefused() throws PTSChatIllegalStateException {
        synchronized (this){
            waitSempahore();
            lockSemaphore();

            if ( ! flagServiceStarted || flagChatClosed || flagChatOpen ){
                unlockSemaphore();
                this.destroy();
                throw new PTSChatIllegalStateException("Cannot refuse chat request");
            }
            this.destroy();
            flagChatClosed = true;
            flagChatOpen = false;
            emit( new PTSEvent( CHAT_REFUSED ) );

            unlockSemaphore();
        }
    }

    protected void onQuit() throws PTSChatIllegalStateException {
        synchronized (this){
            try {
                if ( flagSemaphore )
                    wait();
                flagSemaphore = true;

                if ( !flagServiceStarted || ! flagChatOpen || flagChatClosed ) {
                    flagSemaphore = false;
                    notify();
                    this.destroy();
                    throw new PTSChatIllegalStateException("Cannot quit service");
                }
                this.destroy();
                flagChatClosed = true;
                emit( new PTSEvent(CHAT_CLOSED) );

                flagSemaphore = false;
                notify();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

//#############################################################
//                  Chat Service Init
//#############################################################
    // PTSRadio.startService();
    @Override
    public void startService( PTSSerial io, String id ) throws PTSChatIllegalStateException {
        startService(io, id, true);
    }


    // PTSRadio -> REQUEST_CHAT
    public void startService( PTSSerial io, String id, boolean isStartingConnection ) throws PTSChatIllegalStateException {
        if ( flagChatOpen || flagChatClosed )
            throw new PTSChatIllegalStateException("Cannot start chat service");
        if ( io == null || id == null)
            return;

        super.startService(io, id);
        if ( isStartingConnection )
            serialio.write( SERVICE_REQUEST_CHAT + chatMember );
    }

//#############################################################
//                  Chat Semaphore
//#############################################################
    private void waitSempahore(){
        try {
            if ( flagSemaphore )
                wait();
        } catch (InterruptedException e) {
            PTSEvent ev = new PTSEvent( CHAT_ERROR );
            ev.addPayloadElement(e);
            emit( ev );
        }
    }

    private void lockSemaphore(){
        flagSemaphore = true;
    }

    private void unlockSemaphore(){
        flagSemaphore = false;
        notify();
    }

}
