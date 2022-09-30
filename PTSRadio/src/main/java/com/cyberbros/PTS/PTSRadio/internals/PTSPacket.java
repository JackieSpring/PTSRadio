package com.cyberbros.PTS.PTSRadio.internals;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// TODO: Il parsing è buggato a causa di inconsistenze del protocollo della scheda, Ho applicato dei workaround che DEVONO essere rimossi
/*
____PACKET________________SRC__DST__PAYLOAD
    ACTION_UNKNOWN                  [fullPacket]
    ACTION_DEBUG                    [message]
    ACTION_MESSAGE          S,      [message]

    ACTION_REQUEST_CHAT     S, D
    ACTION_REQUEST_GROUP    S, D,   [host]
    ACTION_REQUEST_CALL     S, D,   [channel]

    ACTION_SERVICE_YES      S, D
    ACTION_SERVICE_NO       S, D
    ACTION_SERVICE_ADD      S, D
    ACTION_SERVICE_KICK     S, D
    ACTION_SERVICE_QUIT     S
    ACTION_SERVICE_TIMEOUT

    ACTION_SESSION_PING     S
    ACTION_SESSION_ID               [id]

    ACTION_PTT_GO
    ACTION_PTT_STOP

    ACTION_CHANNEL_OK               [channel]
    ACTION_CHANNEL_KO               [channel]
* */

public class PTSPacket {
/*
    public static final int
    PACKET_UNKNOWN = 0,
    PACKET_MESSAGE = 1,     // M
    PACKET_REQUEST = 2,     // C,G,A
    PACKET_SERVICE = 3,     // Y,N,REQUEST_TIMEOUT, +, -, E
    PACKET_SESSION = 4,     // O
    PACKET_PTT = 5,         // PTT0, PTT1
    PACKET_CHANNEL = 6;     // Y,N
*/
    public static final String
    ACTION_UNKNOWN = "unknown",
    ACTION_DEBUG = "DEBUG:",
    ACTION_MESSAGE = "M",

    ACTION_REQUEST_CHAT = "C",
    ACTION_REQUEST_GROUP = "G",
    ACTION_REQUEST_CALL = "A",

    ACTION_SERVICE_YES = "Y",
    ACTION_SERVICE_NO = "N",
    ACTION_SERVICE_ADD = "+",
    ACTION_SERVICE_KICK = "-",
    ACTION_SERVICE_QUIT = "E",
    ACTION_SERVICE_TIMEOUT = "REQUEST_TIMEOUT",

    ACTION_SESSION_PING = "O",
    ACTION_SESSION_ID = "_",

    ACTION_PTT_GO = "PTT0",
    ACTION_PTT_STOP = "PTT1",

    ACTION_CHANNEL_OK = "Y",
    ACTION_CHANNEL_KO = "N";


    private String action;
    private String source;
    private String destination;
    private int length;
    private List<Object> payload;
/*
    public PTSPacket(String act, int len){
        this.action = act;
        this.length = len;
        this.payload = new ArrayList<Object>();
    }
*/
    public PTSPacket( String act, String src, String dest, int len ) {
        this.action = act;
        this.source = src;
        this.destination = dest;
        this.length = len;
        this.payload = new ArrayList<Object>();
    }

// ########################### PUBLIC METHODS ###########################
    public String getAction(){
        return action;
    }

    public String getSource() {
        return source;
    }

    public String getDestination(){
        return destination;
    }

    public int getPacketLength(){
        return this.length;
    }

    public int getPayloadLength(){
        return payload.size();
    }

    public Object getPayloadElement(int i){
        if ( i >= payload.size() )
            return null;
        return payload.get(i);
    }

    public void addPayloadElement(Object elem){
        payload.add(elem);
    }

    @Override
    public String toString(){
        String ret = "";
        ret += "ACTION: " + this.action + "\n";
        ret += "source: " + this.source + "\n";
        ret += "destination: " + this.destination + "\n";
        ret += "packet_length: " + this.length + "\n";
        ret += "payload_length: " + this.payload.size() + "\n";
        for( Object o : payload )
            ret += "\telem : " + o +"\n";
        return ret;
    }

// ########################### STATIC METHODS ###########################

    public static PTSPacket parsePacket( byte [] pkt ){
        return parsePacket( new String(pkt), pkt.length );
    }

    public static PTSPacket parsePacket( String pkt ){
        return parsePacket( pkt, pkt.getBytes(StandardCharsets.UTF_8).length );
    }

    public static PTSPacket parsePacket( String stringpkt, int pklen ) {
        PTSPacket pk = null;
        String action = "";
        String source = "";
        int stringpklen;

        if ( stringpkt.lastIndexOf("\r\n") == (stringpkt.length() - 2) )
            stringpkt = stringpkt.substring(0, stringpkt.lastIndexOf("\r\n") ); //stringpkt.replace("\r\n", "");

        stringpklen = stringpkt.length();
/*
        // TODO
        if ( stringpklen >= 6 && stringpkt.substring(0, 6).equals(PTSPacket.ACTION_DEBUG) ) {

            // TODO: Rimuovere questo WorkAround non appena il protocollo verrà aggiustato

            String REQUEST_RESPONSE_DEBUG = "messaggio destinato a noi    \r\n";
            String REQUEST_DEBUG = "richiesta chat  ";
            int debugIndex;
            int startIndex;
            int endIndex;

            debugIndex = stringpkt.indexOf(REQUEST_RESPONSE_DEBUG);
            if ( debugIndex != -1 ) {
                startIndex = debugIndex + REQUEST_RESPONSE_DEBUG.length();
                endIndex = stringpkt.indexOf("\r\n", startIndex);
                stringpkt = stringpkt.substring(startIndex, endIndex);
                stringpklen = stringpkt.length();
            }

            debugIndex = stringpkt.indexOf(REQUEST_DEBUG);
            if ( debugIndex != -1 ) {
                stringpkt = stringpkt.substring(23, 34);
                stringpklen = stringpkt.length();
            }

        }// TODO
*/
        if ( stringpklen > 5 ) {
            action = String.valueOf(stringpkt.charAt(5));
            source = stringpkt.substring(0, 5);
        }

// Check constant packets, then packet length, then action command

        // DEBUG packet
        if ( stringpklen >= 6 && stringpkt.substring(0, 6).equals(PTSPacket.ACTION_DEBUG) ) {
            pk = new PTSPacket(PTSPacket.ACTION_DEBUG, null, null, pklen);
            pk.addPayloadElement( stringpkt.substring(6) );

        }
        // REQUEST_TIMEOUT packet
        else if ( stringpklen >= 15 && stringpkt.substring( 0, 15 ).equals( PTSPacket.ACTION_SERVICE_TIMEOUT ) )
            pk = new PTSPacket( PTSPacket.ACTION_SERVICE_TIMEOUT, null, null, pklen );

        else if (stringpklen == 4) {
            if (stringpkt.equals(PTSPacket.ACTION_PTT_GO))
                pk = new PTSPacket(PTSPacket.ACTION_PTT_GO, null, null, pklen);

            else if (stringpkt.equals(PTSPacket.ACTION_PTT_STOP))
                pk = new PTSPacket(PTSPacket.ACTION_PTT_STOP, null, null, pklen);

            else if (stringpkt.substring(0, 1).equals(PTSPacket.ACTION_CHANNEL_OK)) {
                pk = new PTSPacket(PTSPacket.ACTION_CHANNEL_OK, null, null, pklen);
                pk.addPayloadElement(stringpkt.substring(1));
            }
            else if (stringpkt.substring(0, 1).equals(PTSPacket.ACTION_CHANNEL_KO)) {
                pk = new PTSPacket(PTSPacket.ACTION_CHANNEL_KO, null, null, pklen);
                pk.addPayloadElement(stringpkt.substring(1));
            }
        }
        else if (stringpklen == 5){
            pk = new PTSPacket(PTSPacket.ACTION_SESSION_ID, null, null, pklen);
            pk.addPayloadElement( stringpkt );
        }
        else{
            String dest = null;

            if ( stringpklen >= 11 )
                dest = stringpkt.substring(6, 11);

            switch( action ){
            // requests
                case PTSPacket.ACTION_REQUEST_CALL:
                    if ( stringpklen == 14 ) {
                        String cnl = stringpkt.substring(11);
                        pk = new PTSPacket( action, source, dest, pklen );
                        pk.addPayloadElement(cnl);
                    }
                    break;
                case PTSPacket.ACTION_REQUEST_GROUP:
                    if ( stringpklen == 16 ) {
                        String host= stringpkt.substring(11);
                        pk = new PTSPacket( action, source, dest, pklen );
                        pk.addPayloadElement(host);
                    }
                    break;
            // service control
                case PTSPacket.ACTION_REQUEST_CHAT:
                case PTSPacket.ACTION_SERVICE_YES :
                case PTSPacket.ACTION_SERVICE_NO:
                case PTSPacket.ACTION_SERVICE_ADD:
                case PTSPacket.ACTION_SERVICE_KICK:
                    if ( stringpklen >= 11 )
                        pk = new PTSPacket( action, source, dest, pklen );
                    break;
                case PTSPacket.ACTION_SERVICE_QUIT:
                case PTSPacket.ACTION_SESSION_PING:
                    //TODO: Inserire questo controllo non appena la scheda vieve aggiustata -> if ( stringpklen != 6 ) break;
                    pk = new PTSPacket( action, source, null, pklen );
                    break;
            // message
                case PTSPacket.ACTION_MESSAGE:
                    pk = new PTSPacket( action, source, null, pklen );
                    pk.addPayloadElement( stringpkt.substring(6) );
                    break;
            // undefined
               default:
                    pk = new PTSPacket( PTSPacket.ACTION_UNKNOWN, null, null, pklen );
                    pk.addPayloadElement( stringpkt );
            }
        }

        if ( pk == null ){
            pk = new PTSPacket( PTSPacket.ACTION_UNKNOWN, null, null, pklen );
            pk.addPayloadElement( stringpkt );
        }

        return pk;
    }

}