package pcd.reactive.gui;

import pcd.reactive.gui.controller.ScanController;
import pcd.reactive.gui.model.ScanModel;
import pcd.reactive.gui.model.ScanModelRx;
import pcd.reactive.gui.view.ScanView;
import pcd.reactive.gui.view.ScanViewImpl;

import javax.swing.*;

/**
 * Application entry-point for the Reactive GUI.
 * <p>
 * Wires the MVC triad and starts the Swing event loop on the EDT.
 * <p>
 * Dependency graph (no cycles, strict MVC):
 * <p>
 * GUIMain
 * ├── creates  ScanModelRx     (Model)
 * ├── creates  ScanViewImpl    (View)
 * └── creates  ScanController  (Controller)
 * ├── holds ref → Model
 * └── holds ref → View  (via ScanView interface)
 * <p>
 * The View holds a reference only to ScanView.ViewListener (implemented
 * by the Controller), never to the Controller class itself.
 * The Model knows nothing about View or Controller.
 */
public class GUIMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Apply system look-and-feel as base (our custom colours override it)
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { /* use default */ }

            // ── Instantiate the triad ─────────────────────────────────────
            ScanModel model = new ScanModelRx();
            ScanView view = new ScanViewImpl();

            // Controller wires Model ↔ View
            @SuppressWarnings("unused")
            ScanController controller = new ScanController(model, view);

            // ── Show the window ───────────────────────────────────────────
            view.display();
        });
    }
}
