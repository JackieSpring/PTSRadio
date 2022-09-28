package com.cyberbros.PTS.PTSRadio.internals;

import android.util.Log;

import androidx.annotation.NonNull;

/*
* TODO OGNI OPERAZIONE SULLA CHAIN VA SINCRONIZZATA CON wait() e notify()
*
* */

public abstract class PTSPacketTrap {
    private PTSPacketTrap prev;
    private PTSPacketTrap next;


    public synchronized void destroy(){
        if ( prev != null )
            prev.next = next;

        if ( next != null )
            next.prev = prev;
    }

    public synchronized void destroyChain(){
        PTSPacketTrap n = next;
        destroy();
        if ( n != null )
            n.destroyChain();
    }

    public synchronized void addNext( PTSPacketTrap newtrap ){
        if ( newtrap != null ) {
            newtrap.prev = this;
            newtrap.next = next;
        }
        this.next = newtrap;
    }

    public synchronized void addPrev( @NonNull PTSPacketTrap newtrap ){
        newtrap.next = this;
        newtrap.prev = prev;
        prev = newtrap;
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
