package nl.bliss;

import hmi.flipper2.FlipperException;
import hmi.flipper2.launcher.FlipperLauncherThread;
import robotbrain.FLTIsVisualizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Main {

    private static FlipperLauncherThread flt;


    public static void main(String[] args) throws FlipperException, IOException {
        String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: -config <filepath to file containing the flipper properties>\n";
        String flipperPropFile = "data/dm/cbflipper.properties";

        if(args.length % 2 != 0){
            System.err.println(help);
            System.exit(0);
        }

        for(int i = 0; i < args.length; i = i + 2){
            if(args[i].equals("-config")){
                flipperPropFile = args[i+1];
            } else {
                System.err.println("Unknown commandline argument: \""+args[i]+" "+args[i+1]+"\".\n"+help);
                System.exit(0);
            }
        }
        Properties ps = new Properties();
        InputStream flipperPropStream = Main.class.getClassLoader().getResourceAsStream(flipperPropFile);

        try {
            ps.load(flipperPropStream);
        } catch (IOException ex) {
            System.out.println("Could not load flipper settings from "+flipperPropFile);
            ex.printStackTrace();
        }
        // If you want to check templates based on events (i.e. messages on middleware),
        // you can run  flt.forceCheck(); from a callback to force an immediate check.
        System.out.println("FlipperLauncher: Starting Thread");

//        Original flt
//        flt = new FlipperLauncherThread(ps);
//          Custom visualizer
        flt = new FLTIsVisualizer(ps);
        flt.start();
    }
}
