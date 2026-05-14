package pcd.ass02.reactive;

import pcd.ass02.reactive.view.ScanView;

import javax.swing.*;

/**
 * Application entry-point for the reactive GUI.
 */
public class TestRx {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { /* use default */ }

            new ScanView(new FSStatLibRx()).display();
        });
    }
}
