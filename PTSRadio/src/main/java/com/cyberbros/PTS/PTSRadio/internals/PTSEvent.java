package com.cyberbros.PTS.PTSRadio.internals;

import java.util.ArrayList;
import java.util.List;

public class PTSEvent {
    private String action;
    private List<Object> payload;

    public PTSEvent( String act ){
        this.action = act;
        payload = new ArrayList<Object>();
    }

    public PTSEvent ( String newAction, PTSEvent oldEv ) {
        action = newAction;
        payload = new ArrayList<Object>( oldEv.payload );
    }

    public String getAction(){
        return this.action;
    }

    public Object getPayloadElement( int index ){
        if ( index >= payload.size() )
            return null;

        return payload.get(index);
    }

    public void addPayloadElement( Object elem ){
        payload.add(elem);
    }

    public int getPayloadLength(){
        return payload.size();
    }
}
