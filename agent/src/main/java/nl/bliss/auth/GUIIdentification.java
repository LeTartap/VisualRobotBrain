/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.bliss.auth;

import javax.swing.JFrame;
import javax.swing.UIManager;

/**
 *
 * @author WaterschootJB
 */
public class GUIIdentification extends SimpleIdentification{
    
    private JFrame gui;
    
    public GUIIdentification(){
        //This is not finished but should have a password protected user ID
        //this.initialize();
    }

    @Override
    public void start() {

        //this.initialize();
    }

    public static void main(String[] args){
        GUIIdentification authGUI = new GUIIdentification();
        authGUI.initialize();
    }

    private void initialize() {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                gui = new SimpleGUIIdentification();
                gui.setVisible(true);
            }
        });   
            try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
                else{
                    javax.swing.UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(SimpleGUIIdentification.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
    }
    
}
