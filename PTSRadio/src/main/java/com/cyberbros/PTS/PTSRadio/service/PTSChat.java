package com.cyberbros.PTS.PTSRadio.service;

import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSRadio;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatException;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatIllegalStateException;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;
import com.cyberbros.PTS.PTSRadio.service.PTSService;

/*
TODO:
    Invio e ricezione di messaggi,
    Pacchettizazione / Spacchettizazione dei messaggi
    Ricezione chiusura
 */
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
    SERVICE_ACCEPT = "Y",
    SERVICE_REFUSE = "N",
    SERVICE_QUIT = "E",
    SERVICE_MESSAGE = "M",
    SERVICE_REQUEST_CHAT = "C";

    private String chatMember;
    private boolean flagChatOpen = false;
    private boolean flagChatClosed = false;
    private boolean flagSemaphore = false;



    public PTSChat( String target ){
        super();
        chatMember = target;
    }


//#############################################################
//                  Chat Public Methods
//#############################################################
    public boolean isOpen() {
        synchronized (this) {
            try {
                if (flagSemaphore)
                    wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return this.flagChatOpen;
        }
    }

    public String getID() {
        return this.selfID;
    }

    public String getMemberID(){
        return this.chatMember;
    }

    public void accept() throws PTSChatIllegalStateException {
        onRequestAccepted();
        serialio.write( SERVICE_ACCEPT + chatMember );
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

    public void send(String msg){
        //TODO Message handling
        Log.e("PTSChat", "TODO: send message");
        serialio.write(selfID + SERVICE_MESSAGE + msg);
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
            if ( flagSemaphore ) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (flagChatOpen) {
                try {
                    if (PTSPacket.ACTION_MESSAGE.equals(action) && member.equals(chatMember) ) {
                        //TODO handle message and message protocol
                        Log.e("PTSChat", "TODO: handle message");
                        PTSEvent ev = new PTSEvent( CHAT_MESSAGE );
                        ev.addPayloadElement( pk.getPayloadElement(0) );
                        emit( ev );
                        isHandled = true;
                    } else if (PTSPacket.ACTION_SERVICE_QUIT.equals(action) && member.equals(chatMember)) {
                        onQuit();
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
    protected void onTimeout()  throws PTSChatIllegalStateException {
        synchronized (this) {
            try {
                if ( flagSemaphore )
                    wait();
                flagSemaphore = true;

                if ( ! flagServiceStarted || flagChatClosed || flagChatOpen  )
                    throw new PTSChatIllegalStateException("Timeout during illegal state");
                this.destroy();
                flagChatClosed = true;
                emit( new PTSEvent( CHAT_REQUEST_TIMEOUT ) );

                flagSemaphore = false;
                notify();
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

    protected void onRequestAccepted() throws PTSChatIllegalStateException {
        synchronized (this){
            try {
                if ( flagSemaphore )
                    wait();
                flagSemaphore = true;

                if ( ! flagServiceStarted || flagChatClosed || flagChatOpen  )
                    throw new PTSChatIllegalStateException("Cannot accept request");
                flagChatOpen = true;
                emit( new PTSEvent( CHAT_ACCEPTED ) );

                flagSemaphore = false;
                notify();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected void onRequestRefused() throws PTSChatIllegalStateException {
        synchronized (this){
            try {
                if ( flagSemaphore )
                    wait();
                flagSemaphore = true;

                if ( ! flagServiceStarted || flagChatClosed || flagChatOpen )
                    throw new PTSChatIllegalStateException("Cannot refuse request");
                this.destroy();
                flagChatClosed = true;
                emit( new PTSEvent( CHAT_REFUSED ) );

                flagSemaphore = false;
                notify();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected void onQuit() throws PTSChatIllegalStateException {
        synchronized (this){
            try {
                if ( flagSemaphore )
                    wait();
                flagSemaphore = true;

                if ( !flagServiceStarted || ! flagChatOpen || flagChatClosed )
                    throw new PTSChatIllegalStateException("Cannot quit service");
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
//                  Chat Request
//#############################################################
    @Override
    public void startService( PTSSerial io, String id ) throws PTSChatIllegalStateException {
        startService(io, id, true);
    }


    public void startService( PTSSerial io, String id, boolean isStartingConnection ) throws PTSChatIllegalStateException {
        if ( flagChatOpen || flagChatClosed )
            throw new PTSChatIllegalStateException("Cannot start chat service");
        if ( io == null || id == null)
            return;

        serialio = io;
        selfID = id;
        flagServiceStarted = true;
        if ( isStartingConnection )
            serialio.write( SERVICE_REQUEST_CHAT + chatMember );
    }

}
