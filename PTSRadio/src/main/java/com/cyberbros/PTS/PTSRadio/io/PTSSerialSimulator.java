package com.cyberbros.PTS.PTSRadio.io;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Iterator;


/*
TODO DEBUG CLASS
Questa classe è stata creata con l'unico scopo di facilitare il debug frontend
e non deve essere inclusa nel progetto finale.
 */

public class PTSSerialSimulator extends PTSSerial {

    public static long FLAG_NONE = 0x0;
    public static long FLAG_CHAT_REQUEST_SEND = 1 << 0;
    public static long FLAG_CHAT_REQUEST_TIMEOUT = 1 << 1;
    public static long FLAG_CHAT_REQUEST_REFUSE = 1 << 2;
    public static long FLAG_CHAT_QUIT = 1 << 3;

    private static String SELF_ID = "40000";
    private static String FAKE_ID = "50000";

    private boolean isTextMode = false;
    private boolean isAudioMode = false;
    private boolean isChatOpen = false;
    private boolean isChatRequestWaiting = false;
    private long ActiveFlags = 0x0L;

    private String chatClient;

    private PTSMessageBuilder mb;
    private PTSSerial.PTSSerialCallback readcallback;



    public PTSSerialSimulator(long flags) {
        super();
        mb = new PTSMessageBuilder();
        ActiveFlags = flags;
    }

    @Override
    public synchronized void write(byte [] arr){
        write( new String(arr) );
    }
    @Override
    public synchronized void write(String msg) {
        Log.e("PTSSerialSimulator", msg);
        if ( isChatOpen )
            onChatOpen(msg);

        if ( isChatRequestWaiting ) {
            isChatRequestWaiting = false;
            if ( checkActiveFlag( FLAG_CHAT_REQUEST_TIMEOUT ) )
                return;
            if ( msg.length() == 6 && msg.startsWith("Y") )
                isChatOpen = true;

        }
        else if ( msg.equals("R") ){
            isTextMode = false;
            isAudioMode = false;
            isChatOpen = false;
            send("STATE:" + PTSConstants.BANNER_RESET);
            return;
        }
        if ( ! isTextMode && ! isAudioMode ){
            if ( msg.equals("T") ) {
                isTextMode = true;
                isAudioMode = false;
                send( "STATE:" + PTSConstants.BANNER_TEXT_MODE );

                if ( checkActiveFlag( FLAG_CHAT_REQUEST_SEND ) ) {
                    isChatRequestWaiting = true;
                    chatClient = FAKE_ID;
                    send( FAKE_ID + "C" + SELF_ID );
                }
            }
            else if ( msg.equals("A") ) {
                isTextMode = false;
                isAudioMode = true;
                send( "STATE:" + PTSConstants.BANNER_AUDIO_MODE );
            }
        }
        else if ( isTextMode ) {
            if ( msg.equals("I") )
                send(SELF_ID);

            else if ( msg.length() >= 6 && msg.startsWith("C") ) {
                chatClient = msg.substring(1, 6);
                if ( checkActiveFlag( FLAG_CHAT_REQUEST_TIMEOUT )  )
                    send( "REQUEST_TIMEOUT"  );

                else if ( checkActiveFlag(FLAG_CHAT_REQUEST_REFUSE) )
                    send( chatClient + "N" + SELF_ID  );
                else
                {
                    isChatOpen = true;
                    send(chatClient + "Y" + SELF_ID);
                }
            }
        }
    }

    @Override
    public synchronized void close(){
        readcallback = null;
    }
    public void setReadListener( PTSSerial.PTSSerialCallback cb ){
        this.readcallback = cb;
    }

    public UsbDevice getDevice(){
        return null;
    }

    public boolean isOpen(){
        return true;
    }

    private void send( String msg ){
        readcallback.dataRecived(msg.getBytes());
    }

    private boolean checkActiveFlag( long flag ){
        if ( (ActiveFlags & flag) != 0 )
            return true;
        return false;
    }


    private void onChatOpen( String msg ) {
        if ( msg.equals("E") ) {
            isChatOpen = false;
            chatClient = null;
        }
        else if ( msg.startsWith( "M" ) ) {
            if ( checkActiveFlag( FLAG_CHAT_QUIT ) ) {
                isChatOpen = false;
                send( chatClient + "E" );
                chatClient = null;
                return;
            }
            String pkt = msg.substring(1);
            String fullmessage = mb.pktToMsg( pkt, SELF_ID );
            String mymessage;

            Log.d("PTSSimulator", PTSMessageBuilder.printPacket(pkt));

            if ( fullmessage == null )
                return;

            mymessage = (new Date()).toString();

            if ( fullmessage.length() > 22 )
                mymessage += " ~~~~~~~~~ looooong message indeed ~~~~~~~";

            try {
                for (Iterator<byte[]> it = mb.msgToPkt(mymessage); it.hasNext(); ) {
                    byte[] data = it.next();
                    send( chatClient + "M" +  new String(data));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
