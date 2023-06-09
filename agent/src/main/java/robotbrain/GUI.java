package robotbrain;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class GUI implements ActionListener, ValueUpdateListener {
    private JFrame frame;
    private JLabel label;

    private JLabel textField0;
    private JLabel textField1;
    private JLabel textField2;
    private JLabel textField3;
    private JLabel textField4;
    private JLabel textField5;
    private JLabel textField6;




    int count = 0;

    public GUI() {
        frame = new JFrame();

//        JButton button = new JButton("Click me");
//        button.addActionListener(this);
        label = new JLabel("Number of clicks: 0");

        textField0 = new JLabel("I heard: ");
        textField1 = new JLabel("Emotion: ");
        textField2 = new JLabel("Intent: ");
        textField3 = new JLabel("Polarity: ");
        textField4 = new JLabel("Intensity: ");
        textField5 = new JLabel("Syntax 1: ");
        textField6 = new JLabel("Syntax 2: ");




        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 10, 30));
        panel.setLayout(new GridLayout(0, 1));
//        panel.add(button);
        panel.add(label);
        panel.add(textField0);
        panel.add(textField1);
        panel.add(textField2);
        panel.add(textField3);
        panel.add(textField4);
        panel.add(textField5);
        panel.add(textField6);

        frame.add(panel, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setTitle("Flipper");
        frame.pack();
        frame.setVisible(true);



    }


    @Override
    public void actionPerformed(ActionEvent e) {
        count++;
        label.setText("Number of clicks: " + count);
    }

    @Override
    public void onValuesUpdated(Map<String, String> updatedValues) {
        // Update the GUI components with the new values
        for (Map.Entry<String, String> entry : updatedValues.entrySet()) {
            String variableName = entry.getKey();
            String updatedValue = entry.getValue();

            // Update the GUI component associated with the variableName
            // For example:
            if (variableName.equals("variable1")) {
                textField1.setText(updatedValue);
            } else if (variableName.equals("variable2")) {
                textField2.setText(updatedValue);
            } else if (variableName.equals("variable3")) {
                textField3.setText(updatedValue);
            } else if (variableName.equals("variable4")) {
                textField4.setText(updatedValue);
            } else if (variableName.equals("variable5")) {
                textField5.setText(updatedValue);
            } else if (variableName.equals("variable6")) {
                textField6.setText(updatedValue);
            } else if (variableName.equals("variable0")) {
                textField0.setText(updatedValue);
            }
            // Handle other variables as needed
        }
    }
}
