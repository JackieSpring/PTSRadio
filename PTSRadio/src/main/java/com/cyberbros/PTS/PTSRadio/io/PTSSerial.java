package com.cyberbros.PTS.PTSRadio.io;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

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
    private PTSSerialCallback readcallback = null;

    public PTSSerial(UsbDevice dev, UsbManager man) throws IOException {
        rawdevice = dev;
        usbman = man;

        connection = usbman.openDevice(rawdevice);
        serial = UsbSerialDevice.createUsbSerialDevice(rawdevice, connection);
        if ( serial == null )
            throw new IOException("Cannot open device " + rawdevice);
        serial.open();
        serial.setDataBits(DATA_BITS);
        serial.setBaudRate(BAUD_RATE);
        serial.read((byte [] data) -> {
            if ( readcallback == null || isopen == false )
                return;

            readcallback.dataRecived(data);
        });

        isopen = true;
    }

    public synchronized void write(String msg){
        this.write(msg.getBytes(StandardCharsets.UTF_8));
    }
    public synchronized void write(byte [] arr){
        serial.write(arr);
    }
    public synchronized void close(){
        if ( isopen == false )
            return;

        serial.syncClose();
        isopen = false;
        readcallback = null;
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

}


