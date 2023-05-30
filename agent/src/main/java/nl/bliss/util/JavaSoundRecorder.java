package nl.bliss.util;

import javax.sound.sampled.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
 
public class JavaSoundRecorder implements Runnable {
    private File wavFile = new File(System.getProperty("user.dir") + "/test.wav");
    private volatile boolean recording;
    private final AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;
    private TargetDataLine line;
    
    public JavaSoundRecorder(String filename){
        this.wavFile = new File(filename);
        this.recording = false;
    }
 
    /**
     * Defines an audio format
     */
    AudioFormat getAudioFormat() {
        float sampleRate = 16000.0F;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits,
                                             channels, signed, bigEndian);
        return format;
    }
    
    /**
     * Getter for when the JSR is recording
     * @return true if recording
     */
    public boolean isRecording(){
        return this.recording;
    }
 
    /**
     * Captures the sound and record into a WAV file
     */
    public void start() {
        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);    
            
            // checks if system supports the data line
            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Line not supported");
                System.exit(0);
            }
            line = (TargetDataLine) AudioSystem.getLine(info);            
            line.open(format);            
            line.start();   // start capturing
            this.recording = true;
            AudioInputStream ais = new AudioInputStream(line);
            // start recording
            AudioSystem.write(ais, fileType, wavFile);
            } catch (IOException | LineUnavailableException ex) {
                Logger.getLogger(JavaSoundRecorder.class.getName()).log(Level.SEVERE, null, ex);
        }        
    }
  
    /**
     * Closes the target data line to finish capturing and recording
     */
    public void finish() {
        this.recording = false;
        line.stop();
        line.close();
    } 

    @Override
    public void run() {
        this.start();        
        while(this.recording){
            
        }        
    }
}