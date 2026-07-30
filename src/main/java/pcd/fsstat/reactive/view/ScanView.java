package pcd.fsstat.reactive.view;

import io.reactivex.rxjava3.disposables.Disposable;
import pcd.fsstat.common.FSReport;
import pcd.fsstat.common.FSReportAccumulator;
import pcd.fsstat.common.ReportParameters;
import pcd.fsstat.reactive.FSStatLibRx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Swing GUI directly attached to {@link FSStatLibRx}.
 */
public class ScanView extends JFrame {

    private final FSStatLibRx lib;
    private final AtomicReference<Disposable> currentScan = new AtomicReference<>();
    private final AtomicLong scanToken = new AtomicLong(0);

    private JTextField dirField;
    private JTextField maxFSField;
    private JSpinner bandsSpinner;
    private JButton browseBtn;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel filesLabel;
    private JLabel statusLabel;
    private StatsPanel statsPanel;
    private volatile FSReport lastReport;

    public ScanView(FSStatLibRx lib) {
        super("FSStat");
        this.lib = Objects.requireNonNull(lib, "lib must not be null");
        buildUI();
    }

    public void display() {
        setVisible(true);
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(800, 600));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Configuration & Info
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(buildConfigPanel(), BorderLayout.NORTH);
        topPanel.add(buildStatsRow(), BorderLayout.SOUTH);

        // Center: Stats Panel
        statsPanel = new StatsPanel();

        // Bottom: Start & Stop buttons in the bottom left
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        startBtn = new JButton("Start");
        stopBtn = new JButton("Stop");
        stopBtn.setEnabled(false);

        startBtn.addActionListener(this::onStart);
        stopBtn.addActionListener(e -> stopScan());

        bottomPanel.add(startBtn);
        bottomPanel.add(stopBtn);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(statsPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        // Row 0: Directory field + Browse button
        gc.gridx = 0; gc.gridy = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        panel.add(new JLabel("Directory:"), gc);

        dirField = new JTextField(System.getProperty("user.dir"));
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        panel.add(dirField, gc);

        browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(this::onBrowse);
        gc.gridx = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        panel.add(browseBtn, gc);

        // Row 1: Max File Size and Bands
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsPanel.add(new JLabel("Max file size (bytes):"));
        maxFSField = new JTextField("1000", 10);
        optionsPanel.add(maxFSField);

        optionsPanel.add(new JLabel("Bands:"));
        bandsSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 32, 1));
        optionsPanel.add(bandsSpinner);

        gc.gridx = 0; gc.gridy = 1;
        gc.gridwidth = 3;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(optionsPanel, gc);

        return panel;
    }

    private JPanel buildStatsRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        filesLabel = new JLabel("0");
        statusLabel = new JLabel("Idle");

        p.add(new JLabel("Files:"));
        p.add(filesLabel);
        p.add(new JSeparator(SwingConstants.VERTICAL));
        p.add(new JLabel("Status:"));
        p.add(statusLabel);

        return p;
    }

    private void onScanStarted() {
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        dirField.setEnabled(false);
        maxFSField.setEnabled(false);
        bandsSpinner.setEnabled(false);
        browseBtn.setEnabled(false);

        filesLabel.setText("0");
        setStatus("Scanning...", Color.BLACK);
        statsPanel.clear();
    }

    private void onUpdate(FSReport report) {
        filesLabel.setText(String.format("%,d", report.totalFiles()));
        statsPanel.setReport(report);
    }

    private void onScanCompleted(FSReport report) {
        resetControls();
        filesLabel.setText(String.format("%,d", report.totalFiles()));
        setStatus("Completed!", Color.GREEN);
        statsPanel.setReport(report);
    }

    private void onScanStopped() {
        resetControls();
        setStatus("Stopped", Color.YELLOW);
    }

    private void onScanError(Throwable error) {
        resetControls();
        setStatus("Error: " + error.getMessage(), Color.RED);
    }

    private void resetControls() {
        startBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        dirField.setEnabled(true);
        maxFSField.setEnabled(true);
        bandsSpinner.setEnabled(true);
        browseBtn.setEnabled(true);
        currentScan.set(null);
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private void onBrowse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(dirField.getText().isBlank() ? System.getProperty("user.dir") : dirField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select directory to scan");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            dirField.setText(f.getAbsolutePath());
        }
    }

    private void onStart(ActionEvent e) {
        String dir = dirField.getText().trim();
        if (dir.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please select a directory.", "Missing input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        long maxFS;
        try {
            maxFS = Long.parseLong(maxFSField.getText().trim());
            if (maxFS <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Max file size must be a positive integer.",
                    "Invalid input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bands = (int) bandsSpinner.getValue();
        Path path;
        try {
            path = Paths.get(dir);
        } catch (RuntimeException ex) {
            onScanError(ex);
            return;
        }

        if (!Files.exists(path)) {
            onScanError(new IllegalArgumentException("Directory does not exist: " + path));
            return;
        }
        if (!Files.isDirectory(path)) {
            onScanError(new IllegalArgumentException("Not a directory: " + path));
            return;
        }
        if (!Files.isReadable(path)) {
            onScanError(new IllegalArgumentException("Directory is not readable: " + path));
            return;
        }

        startScan(path, maxFS, bands);
    }

    private void startScan(Path directory, long maxFileSize, int numBands) {
        long token = scanToken.incrementAndGet();
        stopCurrentScan();
        onScanStarted();
        lastReport = null;

        ReportParameters parameters = new ReportParameters(directory, maxFileSize, numBands);
        Disposable d = lib.getFSReport(parameters).subscribe(
                report -> SwingUtilities.invokeLater(() -> {
                    if (scanToken.get() != token) return;
                    lastReport = report;
                    onUpdate(report);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    if (scanToken.get() != token) return;
                    finishScan();
                    onScanError(error);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (scanToken.get() != token) return;
                    finishScan();
                    FSReport finalReport = (lastReport != null)
                            ? lastReport
                            : emptyReport(maxFileSize, numBands);
                    onScanCompleted(finalReport);
                })
        );
        currentScan.set(d);
    }

    private void stopScan() {
        scanToken.incrementAndGet();
        stopCurrentScan();
        onScanStopped();
    }

    private void stopCurrentScan() {
        Disposable d = currentScan.getAndSet(null);
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }

    private void finishScan() {
        currentScan.set(null);
    }

    private FSReport emptyReport(long maxFileSize, int numBands) {
        return new FSReportAccumulator(maxFileSize, numBands).toReport();
    }
}
