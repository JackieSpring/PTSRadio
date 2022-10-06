package com.cyberbros.PTS.PTSRadio.io;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.exception.PTSRuntimeException;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.felhr.utils.ProtocolBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class PTSSerial {
    private static final int BAUD_RATE = PTSConstants.USB_BAUD_RATE;
    private static final int DATA_BITS = PTSConstants.USB_DATA_BITS;

    public interface PTSSerialCallback {
        public void dataRecived(byte [] data);
    };

    private final UsbManager usbman;
    private final UsbDevice rawdevice;
    private final UsbDeviceConnection connection;
    private final UsbSerialDevice serial;

    private boolean isopen = false;
    private boolean flagSemaphore = false;
    private PTSSerialCallback readcallback = null;
    private ProtocolBuffer buffer;

    protected PTSSerial(){
        Log.e("PTSSerial", "DEBUG CONSTRUCTOR INVOKED");
        usbman = null;
        rawdevice = null;
        connection = null;
        serial = null;
    }

    public PTSSerial(UsbDevice dev, UsbManager man) throws IOException {
        rawdevice = dev;
        usbman = man;

        connection = usbman.openDevice(rawdevice);
        serial = UsbSerialDevice.createUsbSerialDevice(rawdevice, connection);
        if ( serial == null )
            throw new IOException("Cannot open device " + rawdevice);

        buffer = new ProtocolBuffer(ProtocolBuffer.BINARY);
        buffer.setDelimiter(new byte[]{0xd, 0xa});

        serial.open();
        serial.setDataBits(DATA_BITS);
        serial.setBaudRate(BAUD_RATE);
        serial.read((byte [] data) -> {
            waitSempahore();

            if ( isopen == false )
                return;

            buffer.appendData(data);
            if ( buffer.hasMoreCommands() ){
                byte [] ret = buffer.nextBinaryCommand();
                buffer = new ProtocolBuffer(ProtocolBuffer.BINARY);
                buffer.setDelimiter(new byte[]{0xd, 0xa});
                if ( readcallback != null )
                    readcallback.dataRecived(ret);
            }
        });

        isopen = true;
    }

    public synchronized void write(String msg) throws PTSRuntimeException {
        this.write(msg.getBytes(StandardCharsets.UTF_8));
    }
    public synchronized void write(byte [] arr) throws PTSRuntimeException {
        waitSempahore();
        if ( arr.length > PTSConstants.PACKET_MAX_LEN )
            throw new PTSRuntimeException("Serial packet too long");

        String ret = "";
        for ( byte b : arr )
            ret += String.valueOf(b) + ",";
        Log.e("PTSSerial write", "Array= " + ret);
        if ( isopen )
            serial.write(arr);
    }
    public synchronized void close(){
        waitSempahore();
        lockSemaphore();

        if ( isopen == false )
            return;

        isopen = false;
        readcallback = null;
        //serial.syncClose();
        buffer = null;
        serial.close();

        unlockSemaphore();
    }
    public void setReadListener( PTSSerial.PTSSerialCallback cb ){
        this.readcallback = cb;
    }

    public UsbDevice getDevice(){
        return this.rawdevice;
    }

    public boolean isOpen(){
        return this.isopen;
    }

    //#############################################################
//                  Chat Semaphore
//#############################################################
    private synchronized void waitSempahore(){
        try {
            if ( flagSemaphore )
                wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
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


