package com.cyberbros.PTS.PTSRadio.io;

import android.annotation.SuppressLint;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.cyberbros.PTS.PTSRadio.PTSConstants;
import com.cyberbros.PTS.PTSRadio.exception.PTSChatIllegalStateException;
import com.cyberbros.PTS.PTSRadio.internals.PTSEvent;

import java.io.IOException;

/* TODO
    RIMUOVERE codice inutile
 */

public class PTSAudio {

    private final AudioManager audioman;

    private int SMOOTHING = 25;

    private AudioDeviceInfo outTalkDevice;
    private AudioDeviceInfo inTalkDevice;
    private AudioDeviceInfo outListenDevice;
    private AudioDeviceInfo inListenDevice;


    private AudioRecord recorder;
    private AudioTrack player;



    private int samplerate;
    private int BUFFER_SIZE;
    private short [] buffer;

    private boolean flagIsOpen = false;
    private boolean flagIsActive = false;
    private boolean flagSemaphore = false;

    private Thread workerThread;

    private Runnable deviceDetachedCallback = null;
    @SuppressLint("NewApi")
    private AudioDeviceCallback detachListener = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            super.onAudioDevicesAdded(addedDevices);
            // TODO ?
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            super.onAudioDevicesRemoved(removedDevices);
            for ( AudioDeviceInfo dev : removedDevices )
                if ( dev.equals( inListenDevice ) || dev.equals(inTalkDevice) || dev.equals(outListenDevice) || dev.equals(outTalkDevice) ) {
                    onDeviceDetached();
                }
        }
    };


    @SuppressLint({"MissingPermission", "NewApi"})
    public PTSAudio (AudioDeviceInfo INdev, AudioDeviceInfo OUTdev, AudioManager am) throws IOException {
        audioman = am;

        outTalkDevice = OUTdev;
        inTalkDevice = getAudioDevice( AudioDeviceInfo.TYPE_BUILTIN_MIC, true );

        outListenDevice = getAudioDevice( AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, false );
        inListenDevice = INdev;

        if ( outListenDevice == null || outTalkDevice == null || inListenDevice == null || inTalkDevice == null )
            throw new IOException("Missing audio device");


        samplerate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        BUFFER_SIZE = 2 * AudioRecord.getMinBufferSize(samplerate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        buffer = new short[BUFFER_SIZE];

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                samplerate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE);

        player = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
                samplerate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE,
                AudioTrack.MODE_STREAM);

        audioman.registerAudioDeviceCallback( detachListener ,null );
    }

//#############################################################
//                  Public Methods
//#############################################################

    @SuppressLint("NewApi")
    public synchronized void talk(){
        waitSempahore();
        lockSemaphore();
        Log.e("PTSAudio talk", "flagIsOpen=" + flagIsOpen + " flagIsActive=" + flagIsActive + " workerThread="+ workerThread );
        if ( flagIsOpen == false )
            return;

        try {
            flagIsActive = false;
            if ( workerThread != null )
                workerThread.join();

            buffer = new short[BUFFER_SIZE];
            recorder.stop();
            player.stop();

            recorder.setPreferredDevice( inTalkDevice );
            player.setPreferredDevice( outTalkDevice );

            recorder.startRecording();
            player.play();
            Log.e("PTSAudio talk", "recorder.getState()=" + recorder.getState() + " player.getState()=" + player.getState());
            //if ( recorder.getState() != AudioRecord.SUCCESS || player.getState() != AudioTrack.SUCCESS )
            //    throw new RuntimeException("Something went wrong while strating audio stream");

            workerThread = new Thread( () -> {
                while( flagIsActive ) {
                    recorder.read( buffer, 0, BUFFER_SIZE );
                    player.write( buffer, 0, BUFFER_SIZE );
                    Log.e("PTSAudio talk thread", "Thread working, flagIsActive=" + flagIsActive);
                }
                Log.e("PTSAudio talk thread", "Thread DEAD");
            });


            flagIsActive = true;
            workerThread.start();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            unlockSemaphore();
        }

        Log.e("PTSAudio talk", "Final CHECK" + "flagSemaphore=" + flagSemaphore + " workerThread=" + workerThread + " workerThread.isAlive=" + workerThread.isAlive());
    }

    @SuppressLint("NewApi")
    public synchronized void listen(){
        waitSempahore();
        lockSemaphore();
        Log.e("PTSAudio listen", "flagIsOpen=" + flagIsOpen + " flagIsActive=" + flagIsActive + " workerThread="+ workerThread );

        if ( flagIsOpen == false )
            return;

        try {
            flagIsActive = false;
            if ( workerThread != null )
                workerThread.join();

            buffer = new short[BUFFER_SIZE];
            recorder.stop();
            player.stop();

            recorder.setPreferredDevice( inListenDevice );
            player.setPreferredDevice(outListenDevice);

            recorder.startRecording();
            player.play();

            Log.e("PTSAudio listen", "recorder.getState()=" + recorder.getState() + " player.getState()=" + player.getState());

            //if ( recorder.getState() != AudioRecord.SUCCESS || player.getState() != AudioTrack.SUCCESS )
            //    throw new RuntimeException("Something went wrong while strating audio stream");

            workerThread = new Thread( () -> {
                while( flagIsActive ) {
                    recorder.read( buffer, 0, BUFFER_SIZE );
                    //noiseFilter( buffer );
                    player.write( buffer, 0, BUFFER_SIZE );
                    Log.e("PTSAudio listen thread", "Thread working, flagIsActive=" + flagIsActive);
                }
            });

            flagIsActive = true;
            workerThread.start();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            unlockSemaphore();
        }
    }

    public synchronized void start(){
        waitSempahore();
        lockSemaphore();

        if ( flagIsOpen )
            return;
        flagIsOpen = true;

        unlockSemaphore();
    }

    public synchronized void stop(){
        waitSempahore();
        lockSemaphore();
        try {
            if (!flagIsOpen)
                return;

            flagIsOpen = false;
            flagIsActive = false;
            if (workerThread != null)
                workerThread.join();

            recorder.stop();
            player.stop();
        }catch ( InterruptedException ex ){
            ex.printStackTrace();
        }
        finally {
            unlockSemaphore();
        }
    }

    public synchronized void close(){
            stop();
            recorder.release();
            player.release();
    }

    public void setOnDetachedCallback( Runnable cb ){
        this.deviceDetachedCallback = cb;
    }

//#############################################################
//                  Event Handlers
//#############################################################
    private void onDeviceDetached(){
        close();
        if ( deviceDetachedCallback != null )
            deviceDetachedCallback.run();
    }

//#############################################################
//                  Utility
//#############################################################
    @SuppressLint("NewApi")
    private AudioDeviceInfo getAudioDevice(int type, boolean issource) {
        for (AudioDeviceInfo audiodev : audioman.getDevices(AudioManager.GET_DEVICES_OUTPUTS | AudioManager.GET_DEVICES_INPUTS))
            if (audiodev.getType() == type && audiodev.isSource() == issource)
                return audiodev;
        return null;
    }

    private void noiseFilter( short [] values ) {
        short current = values[0];
        for ( int i = 1; i < values.length; i++ ) {
            short temp = values[i];
            current += (temp - current) / SMOOTHING;
            values[i] = current;
        }
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
