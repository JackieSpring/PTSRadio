package com.cyberbros.PTS.PTSRadio.io;

import com.cyberbros.PTS.PTSRadio.PTSConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

public class PTSMessageBuilder {

    private static int PKT_MAX_LEN      = PTSConstants.PACKET_MAX_LEN;
    private static int PKT_TOT_NUM_BYTES= 1;
    private static int PKT_NUM_BYTES    = PKT_TOT_NUM_BYTES;
    private static int MSG_ID_BYTES     = 2;
    private static int HEADERS_BYTES    = PKT_NUM_BYTES + PKT_TOT_NUM_BYTES + MSG_ID_BYTES;
    private static int PAYLOAD_MAX_LEN  = PKT_MAX_LEN - HEADERS_BYTES ;
    private static int MESSAGE_MAX_LEN  = PAYLOAD_MAX_LEN * PKT_TOT_NUM_BYTES * 0x100;


    private HashMap<String,HashMap<Integer,TreeSet< byte[] >>> memoria = new HashMap<>();
    private short n_msg_send = 0;


    // ####################################################################
//                      Public Methods
// ####################################################################
    public Iterator< byte[] > msgToPkt(String msg) throws IOException {
        return msgToPkt(msg.getBytes());
    }

    public Iterator< byte[] > msgToPkt( byte[] msg ) throws IOException {
        if ( msg.length >= MESSAGE_MAX_LEN || n_msg_send >= (MSG_ID_BYTES * 8)  )
            throw new IOException("Packet too long");

        List< byte[] > packets_list = new ArrayList<>();
        int message_len = msg.length;
        int n_pkt_tot = message_len / PAYLOAD_MAX_LEN;
        int byte_overflow = 0;

        if ( (n_pkt_tot * PAYLOAD_MAX_LEN) < message_len ) {
            byte_overflow = message_len - (n_pkt_tot * PAYLOAD_MAX_LEN);
            n_pkt_tot++;
        }

        for( int pkt_index = 0; pkt_index < n_pkt_tot ; pkt_index++ ) {
            byte [] packet;
            int start = pkt_index * PAYLOAD_MAX_LEN;
            int end;

            if ( (pkt_index + 1) == n_pkt_tot )
                end = byte_overflow;
            else
                end = PAYLOAD_MAX_LEN;

            packet = new byte[ HEADERS_BYTES + end ];

            packet[0] = (byte)( n_msg_send >> 8 );
            packet[1] = (byte)n_msg_send;
            packet[2] = (byte)n_pkt_tot;
            packet[3] = (byte)pkt_index;

            for ( int i = 0; i < end; i++ )
                packet[ HEADERS_BYTES + i ] = msg[ start + i];

            packets_list.add( packet );
        }

        n_msg_send++;
        return packets_list.iterator();

    }

    public String pktToMsg( String pkt, String sender_id){
        if (pkt == null)
            return null;
        return pktToMsg(pkt.getBytes(), sender_id);
    }
    public String pktToMsg( byte[] pkt, String sender_id){

        if ( pkt == null || sender_id == null || pkt.length < HEADERS_BYTES )
            return null;

        int n_msg = bytesToNum(pkt[0], pkt[1]);
        int n_packets_tot = pkt[2] & 0xff;
        StringBuilder sb;

        if ( n_packets_tot == 0 )
            return null;

        if (!memoria.containsKey(sender_id)){
            memoria.put(sender_id, new HashMap<>());
        }
        if (memoria.get(sender_id).containsKey(n_msg)) {
            memoria.get(sender_id).get(n_msg).add(pkt);
        } else {
            TreeSet<byte []> set = new TreeSet<>( new Comparator<byte[]>(){
                @Override
                public int compare(byte [] b1, byte [] b2){
                    return (b1[3] & 0xff) - (b2[3] & 0xff);
                }
            });
            set.add(pkt);
            memoria.get(sender_id).put(n_msg, set);
        }

        if (memoria.get(sender_id).get(n_msg).size() != (int)n_packets_tot)
            return null;

        sb = new StringBuilder();
        for (byte[] p : memoria.get(sender_id).get(n_msg)) {
            for ( int i = HEADERS_BYTES; i < p.length; i++ )
                sb.append( (char)p[i] );
        }

        memoria.get(sender_id).remove(n_msg);
        if (memoria.get(sender_id).isEmpty())
            memoria.remove(sender_id);

        return sb.toString();

    }

// ####################################################################
//                      Private Methods
// ####################################################################

    public static int bytesToNum(byte... bytes) {
        int value = 0;
        for (byte b : bytes) { value = (value << 8) + (b & 0xFF); }
        return value;
    }



    // TODO DEBUG rimuovere a fine progetto!!!!!
    // AGGIUNTA FRA
    public HashMap<Integer,TreeSet<byte[]>> getSenderHashMap( String sender) {
        return memoria.get(sender);
    }


    public static String printPacket( String packet ){
        return printPacket( packet.getBytes() );
    }
    public static String printPacket( byte[] packet ){
        String ret = "";
        StringBuilder sb = new StringBuilder();

        for ( int i = HEADERS_BYTES; i < packet.length; i++ )
            sb.append( (char)packet[i] );

        ret += "Message len: " + packet.length + "\n";
        ret += "Message ID: " + bytesToNum( packet[0], packet[1] ) + "\n";
        ret += "Message pkt_tot: " + packet[2] + "\n";
        ret += "Message pkt: " + packet[3] + "\n";
        ret += "Message payload: " + sb.toString() + "\n";

        return ret;
    }
}
