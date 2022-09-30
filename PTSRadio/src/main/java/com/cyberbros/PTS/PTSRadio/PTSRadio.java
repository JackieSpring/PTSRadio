package com.cyberbros.PTS.PTSRadio;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.cyberbros.PTS.PTSRadio.exception.PTSException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRadioException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRadioIllegalStateException;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.cyberbros.PTS.PTSRadio.internals.PTSListener;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSAudio;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;
import com.cyberbros.PTS.PTSRadio.io.PTSSerialSimulator;
import com.cyberbros.PTS.PTSRadio.service.BoardSetMode;
import com.cyberbros.PTS.PTSRadio.service.BoardTextMode;
import com.cyberbros.PTS.PTSRadio.service.ChannelDiscover;
import com.cyberbros.PTS.PTSRadio.service.PTSService;

import java.io.IOException;
import java.util.HashMap;

/*
--- EVENTS ---
    CONNECTED                   usb connesso e permessi concessi, l'ID della scheda è stato recuperato e tutto è pronto
    DISCONNECTED                usb è stato disconnesso, non è possibile utilizzare alcun servizio
    USER_ONLINE                 un utente è apparso nel ping per la prima volta
    USER_OFFLINE                un utente ha smesso di inviare ping
    AUDIO_ATTACHED              è stato attaccato il dispositivo audio
    AUDIO_DETACHED              è stato rimosso il dispositivo audio
    MISSING_USB_PERMISSION      permessi sulla usb non concessi
    MISSING_AUDIO_PERMISSION    permessi sui dispositivi audio non concessi dall'utente
    ERROR_USB                   durante un'operazione sulla usb è statio provocato un error
    ERROR_AUDIO                 durante un'operazione sui dispositivi audio e stato provocato un errore
    REQUEST_CHAT                è arrivata una richiesta di apertura sessione chat
    REQUEST_GROUP               è arrivata una richiesta di apertura sessione gruppo
    REQUEST_CALL                è arrivata una richiesta di apertura sessione chiamata

--- METHODS ---
    start()
    close()
    startService( PTSService )
    getRadioID()
    setRadioListener( PTSListener )

--- HOWTO ---
    PTSRadio radio = new PTSRadio(this);
    radio.setRadioListener( new PTSListener(){...} );
    if ( radio.start() == false )
        notifyNoDevice();

--- WHAT ---
    .start() -> ( cerca usbdevice ) -> .initRadio(UsbDevice) -> (radio aperta?, richiedi permessi) -> onUsbPermission() -> (crea serialio, init audio, init packetfilter, apre Radio)
    .onUsbAttached -> .initRadio(UsbDevice)

 */

public class PTSRadio {

    private static final String USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private static final int USB_REQUEST_CODE = 0xdeadc0de;

    // EVENTS
    public static final String
    CONNECTED                   = "connected",
    DISCONNECTED                = "disconnected",
    USER_ONLINE                 = "user_online",
    USER_OFFLINE                = "user_offline",
    AUDIO_ATTACHED              = "audio_attached",
    AUDIO_DETACHED              = "audio_detached",
    MISSING_USB_PERMISSION      = "missing_usb_permission",
    MISSING_AUDIO_PERMISSION    = "missing_audio_permission",
    ERROR_USB                   = "error_usb",
    ERROR_AUDIO                 = "error_audio",
    REQUEST_CHAT                = "request_chat",
    REQUEST_GROUP               = "request_group",
    REQUEST_CALL                = "request_call";

    // BOARD COMMANDS
    private static final String
    BOARD_MODE_TEXT     = PTSConstants.CMD_BOARD_MODE_TEXT,
    BOARD_MODE_AUDIO    = PTSConstants.CMD_BOARD_MODE_AUDIO,
    BOARD_RESTART       = PTSConstants.CMD_BOARD_RESTART,
    BOARD_GET_ID        = PTSConstants.CMD_BOARD_GET_ID;

    private final Activity activity;

    private final UsbManager usbman;
    private final AudioManager audioman;

    private PTSSerial serialio;
    private PTSAudio audioio;

    private String ID ;

    private boolean flagIsOpen          = false;
    private boolean flagUsbRequestSent  = false;
    private boolean flagUsbConnected    = false;

    private PTSListener callback;
    private PTSPacketTrap trapchain;
    private PTSSerial.PTSSerialCallback serialreader = new PTSSerial.PTSSerialCallback() {
        @Override
        public void dataRecived(byte[] data) {
            PTSPacket pk = PTSPacket.parsePacket(data);
            trapchain.handle( pk );
        }
    };

    private BroadcastReceiver usbreciver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice device;
            synchronized(this) {
                if ( UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) ) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device == null)
                        return;
                    onUsbDetached(device);
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device == null)
                        return;
                    onUsbAttached(device);
                } else if (PTSRadio.USB_PERMISSION.equals(action)) {
                    device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device == null)
                        return;

                    if ( ! intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) ){
                        emit( new PTSEvent(MISSING_USB_PERMISSION) );
                        return;
                    }

                    onUsbPermission( device );
                }
            }
        }
    };



    public PTSRadio(@NonNull Activity act){
        activity  = act;

        usbman = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        audioman = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);

        IntentFilter filter = new IntentFilter(USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        activity.registerReceiver(usbreciver, filter);
    }

// #####################################################
//                  GETTERS / SETTERS
// #####################################################

    public String getRadioID() {
        return ID;
    }

    public void setRadioListener( PTSListener l ){
        callback = l;
    }


// #####################################################
//              PRIVATE UTILITY METHODS
// #####################################################
    private void send( String msg ){
        if( serialio != null && serialio.isOpen() )
            serialio.write(msg);
    }
    private void send( byte [] data ){
        if( serialio != null && serialio.isOpen() )
            serialio.write(data);
    }

    private void emit(PTSEvent ev){
        if ( callback == null )
            return;
        callback.handle(ev);
    }

    private void emitException( String evcode ,Exception e){
        PTSEvent err = new PTSEvent(evcode);
        err.addPayloadElement( e );
        emit( err );
    }

// #####################################################
//                  INIT / FINI RADIO
// #####################################################
    public boolean start() throws PTSRadioIllegalStateException {
        if ( flagIsOpen )
            throw new PTSRadioIllegalStateException( "Radio already started" );

        return initRadio( findDevice() );
    }

    // TODO DEBUG ONLYYYYYY #######################################
    public boolean startSimulation() throws PTSRadioIllegalStateException {
        return startSimulation( 0x0 );
    }
    public boolean startSimulation(long flags) throws PTSRadioIllegalStateException {
        if ( flagIsOpen )
            throw new PTSRadioIllegalStateException( "Radio already started" );
        serialio = new PTSSerialSimulator( flags );
        // TODO create ID filter
        Log.e("PTSRadio Simulation", "SIMULATION STARTED");
        flagIsOpen = true;
        flagUsbRequestSent = false;
        flagUsbConnected = true;
        initTrapChain();
        return true;
    }
    // TODO DEBUG ONLYYYYYYY #######################################

    public void close() {
        activity.unregisterReceiver(usbreciver);
        if ( flagUsbConnected )
            serialio.close();
        trapchain.destroyChain();
        trapchain = null;
        flagIsOpen = false;
        flagUsbConnected = false;
        // TODO close packetfilter, serialio, audioio
        Log.e("TODO", "close: close radio method");
    }

    private synchronized boolean initRadio( UsbDevice device ) {
        if ( device == null )
            return false;
        Log.e("initRadio", "called");
/*
        if ( flagUsbRequestSent ) {
            try {
                wait();
            } catch (InterruptedException e) {
                emitException(ERROR_USB, e);
            }
        }
*/
        if ( ! usbman.hasPermission(device) ){
            PendingIntent usbpermissionintent = PendingIntent.getBroadcast(activity, USB_REQUEST_CODE, new Intent(USB_PERMISSION), PendingIntent.FLAG_MUTABLE);
            usbman.requestPermission(device, usbpermissionintent);
            flagUsbRequestSent = true;
        }
        else
            onUsbPermission(device);

        return true;
    }

// #####################################################
//                  USB EVENT HANDLERS
// #####################################################

    private synchronized void onUsbDetached( UsbDevice device ){

        if ( ! flagUsbConnected )
            return;

        if ( ! device.equals(serialio.getDevice()) )
            return;

        serialio.close();
        trapchain.destroyChain();
        trapchain = null;
        flagUsbConnected = false;

        // TODO destroy packetfilter
        Log.e("onUsbDetached", "TODO: onUsbDetached: clear packetfilters");
        emit( new PTSEvent( DISCONNECTED ) );
    }

    private synchronized void onUsbAttached( UsbDevice device ){
        if ( flagUsbConnected )
            return;
        initRadio(device);
    }

    private synchronized void onUsbPermission( UsbDevice device ){

        try {
            serialio = new PTSSerial(device, usbman);
            initTrapChain();
            // TODO create ID filter
            Log.e("onUsbPermission", "TODO: create ID filter " + device.getDeviceName());
            flagIsOpen = true;
            flagUsbRequestSent = false;
            flagUsbConnected = true;
            //notify();

        } catch (IOException e) {
            emitException(ERROR_USB, e);
        }
    }

// #####################################################
//                  USB METHODS
// #####################################################

    private UsbDevice findDevice(){
        // TODO: A volte confonde i device e si connette al device sbagliato
        Log.e("findDevice", "TODO: migliorare filtro device");
        HashMap<String, UsbDevice> devlist = usbman.getDeviceList();
        for (UsbDevice dev : devlist.values() )
            if ( PTSConstants.USB_VENDOR_ID == dev.getVendorId() &&
                 PTSConstants.USB_PRODUCT_ID == dev.getProductId() &&
                 PTSConstants.USB_CLASS == dev.getDeviceClass() &&
                 PTSConstants.USB_SUBCLASS == dev.getDeviceSubclass() &&
                 PTSConstants.USB_PROTOCOL == dev.getDeviceProtocol())
            return dev;
        return null;
    }

// #####################################################
//                  PACKET TRAP INIT
// #####################################################
    private void initTrapChain(){
        // STARTUP RESET BUTTON PRESS & TEXT MODE

        // send(R) -> [ bootTrap, send(T) ] -> [ initTrap, send(I) ] -> [ textModeTrap ]

        PTSPacketTrap gatewayTrap;
        BoardSetMode bootTrap;
        PTSPacketTrap initTrap;
        BoardTextMode textModeTrap;

        // Empty trap, used only as head of the chain
        gatewayTrap = new PTSPacketTrap() {
            @Override
            public boolean trap(PTSPacket pk) {
                // TODO DEBUG ##########################
                Log.d("initTrapChain", "printChain");
                printChain();
                Log.d("GatewayTrap", String.valueOf(pk));
                // TODO DEBUG ##########################
                return false;
            }
        };

        textModeTrap = new BoardTextMode();
        textModeTrap.setListener( (PTSEvent event ) -> {
                String action = event.getAction();
                PTSEvent outEv;
                switch(action){
                    case BoardTextMode.BOARD_REQUEST_CHAT:
                        outEv = new PTSEvent(REQUEST_CHAT, event);
                        emit(outEv);
                        break;
                    case BoardTextMode.BOARD_REQUEST_GROUP:
                        // TODO Handle group request
                        Log.e("initTrapChain", "TODO: Handle group request");
                        break;
                    case BoardTextMode.BOARD_REQUEST_CALL:
                        // TODO Handle call request
                        Log.e("initTrapChain", "TODO: Handle call request");
                        break;
                }
        } );

        initTrap = new PTSPacketTrap() {
            @Override
            public boolean trap(PTSPacket pk) {
                //Log.d("initTrap", String.valueOf(pk));
                String action = pk.getAction();

                if ( action.equals( PTSPacket.ACTION_SESSION_ID ) ) {
                    ID = (String) pk.getPayloadElement(0);

                    this.addNext( textModeTrap );
                    textModeTrap.startService(serialio, ID);
                    this.destroy();

                    PTSEvent eventConnected = new PTSEvent( PTSRadio.CONNECTED );
                    eventConnected.addPayloadElement( ID );
                    PTSRadio.this.emit( eventConnected );
                    return true;
                }
                return false;
            }
        };

        bootTrap = new BoardSetMode( BoardSetMode.SET_BOARD_TEXT_MODE, () -> {
            PTSRadio.this.trapchain.addNext( initTrap );
            PTSRadio.this.send( PTSRadio.BOARD_GET_ID );
        } );
        bootTrap.setOnErrorCallback( () -> {
            PTSEvent ev = new PTSEvent( ERROR_USB );
            ev.addPayloadElement( new PTSException("Failed restarting board at boot time"));
            emit(ev);
        } );

        serialio.setReadListener( serialreader );
        trapchain = gatewayTrap;
        bootTrap.startService(serialio, ID);
        trapchain.addNext(bootTrap);
        this.send( BOARD_RESTART );
    }

// #####################################################
//                  SERVICE API
// #####################################################
    public void startService (PTSService serv){
        if ( serv == null || ! flagIsOpen )
            return;
        trapchain.addNext(serv);
        serv.startService(serialio, ID);
    }




}
