package com.cyberbros.PTS.PTSRadio.internals;

import androidx.annotation.NonNull;

public abstract class PTSPacketTrap {
    private PTSPacketTrap prev;
    private PTSPacketTrap next;

    public void destroy(){
        if ( prev != null )
            prev.next = next;

        if ( next != null )
            next.prev = prev;
    }

    public void destroyChain(){
        PTSPacketTrap n = next;
        destroy();
        if ( n != null )
            n.destroyChain();
    }

    public void addNext( PTSPacketTrap newtrap ){
        if ( newtrap != null ) {
            newtrap.prev = this;
            newtrap.next = next;
        }
        this.next = newtrap;
    }

    public void addPrev( @NonNull PTSPacketTrap newtrap ){
        newtrap.next = this;
        newtrap.prev = prev;
        prev = newtrap;
    }

    public void handle( PTSPacket pk ){
        if ( ! this.trap(pk) && next != null )
            next.handle(pk);
    }

    // Return true to stop packet propagation
    public abstract boolean trap( PTSPacket pk );

}
