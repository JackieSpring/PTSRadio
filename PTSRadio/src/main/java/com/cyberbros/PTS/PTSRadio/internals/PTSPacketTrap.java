package com.cyberbros.PTS.PTSRadio.internals;

import android.util.Log;

import androidx.annotation.NonNull;

public abstract class PTSPacketTrap {
    private PTSPacketTrap prev;
    private PTSPacketTrap next;

    private volatile boolean flagSemaphore = false;


    public synchronized void destroy(){
        try {
            if ( flagSemaphore )
                wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        flagSemaphore = true;

        if ( prev != null )
            prev.next = next;

        if ( next != null )
            next.prev = prev;

        flagSemaphore = false;
        notify();
    }

    public synchronized void destroyChain(){
        PTSPacketTrap n = next;
        destroy();
        if ( n != null )
            n.destroyChain();
    }

    public synchronized void addNext( PTSPacketTrap newtrap ){
        try {
            if ( flagSemaphore )
                wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        flagSemaphore = true;

        if ( newtrap != null ) {
            newtrap.prev = this;
            newtrap.next = next;
            if ( next != null )
                next.prev = newtrap;
        }
        this.next = newtrap;

        flagSemaphore = false;
        notify();
    }

    public synchronized void addPrev( @NonNull PTSPacketTrap newtrap ){
        try {
            if ( flagSemaphore )
                wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if ( newtrap == null )
            return;

        if ( prev != null )
            prev.addNext(newtrap);
        else {
            flagSemaphore = true;

            newtrap.next = this;
            newtrap.prev = prev;
            prev = newtrap;

            flagSemaphore = false;
            notify();
        }


    }

    public synchronized void handle( PTSPacket pk ){
        if ( this.trapManager(pk) == false && (this.next != null) )
            this.next.handle(pk);
    }

    // Override for pre-trap checks
    protected boolean trapManager( PTSPacket pk ) {
        return this.trap( pk );
    }

    // Return true to stop packet propagation
    public abstract boolean trap( PTSPacket pk );





    // TODO: DEBUG
    public void printChain(){
        Log.d("printChain", String.valueOf(this));
        if ( next != null )
            next.printChain();
    }
}
