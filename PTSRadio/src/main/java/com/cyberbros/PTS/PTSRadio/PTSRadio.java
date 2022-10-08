package com.cyberbros.PTS.PTSRadio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.cyberbros.PTS.PTSRadio.exception.PTSException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRadioException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRadioIllegalStateException;
import com.cyberbros.PTS.PTSRadio.exception.PTSRuntimeException;
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
import com.cyberbros.PTS.PTSRadio.service.PTSCall;
import com.cyberbros.PTS.PTSRadio.service.PTSChat;
import com.cyberbros.PTS.PTSRadio.service.PTSService;
import com.cyberbros.PTS.PTSRadio.service.PingReciver;

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

    private Runnable audioCallback = new Runnable() {
        @Override
        public void run() {
            emit( new PTSEvent(AUDIO_DETACHED) );
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

    public synchronized void close() {
        activity.unregisterReceiver(usbreciver);
        if ( flagUsbConnected )
            serialio.close();
        if ( audioio != null )
            audioio.close();
        if ( trapchain != null )
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
        if ( audioio != null )
            audioio.close();
        if ( trapchain != null )
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
        // TODO: inserire controllo usb
        initRadio(device);
    }

    private synchronized void onUsbPermission( UsbDevice device ){

        try {
            serialio = new PTSSerial(device, usbman);
            initTrapChain();

            AudioDeviceInfo jackIn = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADSET, true );
            AudioDeviceInfo jackOut = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADSET, false );

            if ( jackIn == null )
                jackIn = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADPHONES, true );
            if ( jackOut == null )
                jackOut = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADPHONES, false );

            if ( jackIn == null || jackOut == null )
                emit( new PTSEvent(AUDIO_DETACHED) );

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
        for (UsbDevice dev : devlist.values() ) {
            Log.d("PTSRadio findDevice",
                    "USB_VENDOR_ID =" + dev.getVendorId() +
                            " USB_VENDOR_ID =" + dev.getVendorId() +
                            " USB_VENDOR_ID =" + dev.getVendorId() +
                            " USB_PRODUCT_ID =" + dev.getProductId() +
                            " USB_CLASS =" + dev.getDeviceClass() +
                            " USB_PROTOCOL =" + dev.getDeviceProtocol());
            if (PTSConstants.USB_VENDOR_ID == dev.getVendorId() &&
                    PTSConstants.USB_PRODUCT_ID == dev.getProductId() &&
                    PTSConstants.USB_CLASS == dev.getDeviceClass() &&
                    PTSConstants.USB_SUBCLASS == dev.getDeviceSubclass() &&
                    PTSConstants.USB_PROTOCOL == dev.getDeviceProtocol())
                return dev;
        }
        return null;
    }

// #####################################################
//                  AUDIO METHODS
// #####################################################
    // TODO DEBUG
private String audioType2String( int type ){
    switch( type ){
        case AudioDeviceInfo.TYPE_AUX_LINE: return "TYPE_AUX_LINE";
        //case AudioDeviceInfo.TYPE_BLE_BROADCAST : return "TYPE_BLE_BROADCAST";
        case AudioDeviceInfo.TYPE_BLE_HEADSET : return "TYPE_BLE_HEADSET";
        case AudioDeviceInfo.TYPE_BLE_SPEAKER : return "TYPE_BLE_SPEAKER";
        case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP : return "TYPE_BLUETOOTH_A2DP";
        case AudioDeviceInfo.TYPE_BLUETOOTH_SCO : return "TYPE_BLUETOOTH_SCO";
        case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE : return "TYPE_BUILTIN_EARPIECE";
        case AudioDeviceInfo.TYPE_BUILTIN_MIC : return "TYPE_BUILTIN_MIC";
        case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER : return "TYPE_BUILTIN_SPEAKER";
        case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE : return "TYPE_BUILTIN_SPEAKER_SAFE";
        case AudioDeviceInfo.TYPE_BUS : return "TYPE_BUS";
        case AudioDeviceInfo.TYPE_DOCK : return "TYPE_DOCK";
        case AudioDeviceInfo.TYPE_FM : return "TYPE_FM";
        case AudioDeviceInfo.TYPE_FM_TUNER : return "TYPE_FM_TUNER";
        case AudioDeviceInfo.TYPE_HDMI : return "TYPE_HDMI";
        case AudioDeviceInfo.TYPE_HDMI_ARC : return "TYPE_HDMI_ARC";
        case AudioDeviceInfo.TYPE_HDMI_EARC : return "TYPE_HDMI_EARC";
        case AudioDeviceInfo.TYPE_HEARING_AID : return "TYPE_HEARING_AID";
        case AudioDeviceInfo.TYPE_IP : return "TYPE_IP";
        case AudioDeviceInfo.TYPE_LINE_ANALOG : return "TYPE_LINE_ANALOG";
        case AudioDeviceInfo.TYPE_LINE_DIGITAL : return "TYPE_LINE_DIGITAL";
        case AudioDeviceInfo.TYPE_REMOTE_SUBMIX : return "TYPE_REMOTE_SUBMIX";
        case AudioDeviceInfo.TYPE_TELEPHONY : return "TYPE_TELEPHONY";
        case AudioDeviceInfo.TYPE_TV_TUNER : return "TYPE_TV_TUNER";
        case AudioDeviceInfo.TYPE_UNKNOWN : return "TYPE_UNKNOWN";
        case AudioDeviceInfo.TYPE_USB_ACCESSORY : return "TYPE_USB_ACCESSORY";
        case AudioDeviceInfo.TYPE_USB_DEVICE : return "TYPE_USB_DEVICE";
        case AudioDeviceInfo.TYPE_USB_HEADSET : return "TYPE_USB_HEADSET";
        case AudioDeviceInfo.TYPE_WIRED_HEADPHONES : return "TYPE_WIRED_HEADPHONES";
        case AudioDeviceInfo.TYPE_WIRED_HEADSET : return "TYPE_WIRED_HEADSET";
        default : return "UNDEFINED";
    }
}

    @SuppressLint("NewApi")
    private AudioDeviceInfo getAudioDevice(int type, boolean issource) {
        Log.d("getAudioDevice", "DEVICES");
        for (AudioDeviceInfo audiodev : audioman.getDevices(AudioManager.GET_DEVICES_OUTPUTS | AudioManager.GET_DEVICES_INPUTS)){
            Log.d("AUDIODEV", "\ntype: " + audioType2String(audiodev.getType()) + "\n" + "issource: " + audiodev.isSource() );
            if (audiodev.getType() == type && audiodev.isSource() == issource)
                return audiodev;
        }
        return null;
    }

    @SuppressLint("NewApi")
    private PTSAudio setupAudio() {
        try {
            if ( ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ){
                emit(new PTSEvent(MISSING_AUDIO_PERMISSION));
                return null;
            }
            AudioDeviceInfo jackIn = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADSET, true );
            AudioDeviceInfo jackOut = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADSET, false );

            if ( jackIn == null )
                jackIn = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADPHONES, true );
            if ( jackOut == null )
                jackOut = getAudioDevice( AudioDeviceInfo.TYPE_WIRED_HEADPHONES, false );

            PTSAudio aio = new PTSAudio( jackIn, jackOut, audioman );
            aio.setOnDetachedCallback(audioCallback);
            return aio;

        } catch (IOException e) {
            emit( new PTSEvent(AUDIO_DETACHED) );
            return null;
        }
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
        PingReciver pingReciverTrap;

        // Empty trap, used only as head of the chain
        gatewayTrap = new PTSPacketTrap() {
            @Override
            public boolean trap(PTSPacket pk) {
                // TODO DEBUG ##########################
                printChain();
                Log.d("GatewayTrap", String.valueOf(pk));
                // TODO DEBUG ##########################
                return false;
            }
        };

        pingReciverTrap = new PingReciver(ID);
        pingReciverTrap.setListener( (PTSEvent event) -> {
            PTSEvent notifyHost;
            if ( PingReciver.USER_ALIVE.equals( event ) ){
                notifyHost = new PTSEvent( USER_ONLINE );
                notifyHost.addPayloadElement( event.getPayloadElement(0) );
                emit(notifyHost);
            }
            else if ( PingReciver.USER_DEAD.equals( event ) ) {
                notifyHost = new PTSEvent( USER_OFFLINE );
                notifyHost.addPayloadElement( event.getPayloadElement(0) );
                emit( notifyHost );
            }
        }  );

        textModeTrap = new BoardTextMode();
        textModeTrap.setListener( (PTSEvent event ) -> {
                String action = event.getAction();
                PTSEvent outEv;
                switch(action){
                    case BoardTextMode.BOARD_REQUEST_CHAT:
                        PTSChat chat = (PTSChat) event.getPayloadElement(0);
                        trapchain.addNext(chat);
                        chat.startService( serialio, ID, false );
                        outEv = new PTSEvent(REQUEST_CHAT, event);
                        emit(outEv);
                        break;
                    case BoardTextMode.BOARD_REQUEST_GROUP:
                        // TODO Handle group request
                        Log.e("PTSRadio textModeTrap", "TODO: Handle group request");
                        break;
                    case BoardTextMode.BOARD_REQUEST_CALL:
                        PTSCall call = (PTSCall) event.getPayloadElement(0);
                        PTSAudio aio = setupAudio();
                        if ( aio == null )
                            break;

                        try{
                            trapchain.addNext(call);
                            call.startService(serialio, ID, aio, false);
                        }catch(NumberFormatException ex){
                            call.destroy();
                            throw new PTSRuntimeException("Invalid channel");
                        }
                        outEv = new PTSEvent( REQUEST_CALL, event );
                        emit( outEv );
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

                    this.addNext( pingReciverTrap );
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
        trapchain.addNext(bootTrap);
        bootTrap.startService(serialio, ID);
    }

// #####################################################
//                  SERVICE API
// #####################################################
    public boolean startService (PTSService serv){
        if ( serv == null || ! flagIsOpen )
            return false;
        trapchain.addNext(serv);

        if ( PTSCall.class.isInstance( serv ) ) {
            PTSAudio aio = setupAudio();
            if (aio == null) {
                serv.destroy();
                return false;
            }
            return ((PTSCall)serv).startService(serialio, ID, aio);
        }
        else
            return serv.startService(serialio, ID);
    }




}
