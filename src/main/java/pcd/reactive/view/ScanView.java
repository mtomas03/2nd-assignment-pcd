package pcd.reactive.view;

import io.reactivex.rxjava3.disposables.Disposable;
import pcd.common.FSReport;
import pcd.common.FSReportAccumulator;
import pcd.common.ReportParameters;
import pcd.reactive.FSStatLibRx;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
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
 * Simple Swing GUI directly attached to {@link FSStatLibRx}.
 */
public class ScanView extends JFrame {

    private static final Color BG = Color.WHITE;
    private static final Color START_BG = Color.GREEN;
    private static final Color STOP_BG = Color.RED;
    private static final Color PANEL_BG = new Color(245, 245, 245);
    private static final Color BORDER = new Color(200, 200, 200);
    private static final Color TEXT_BLACK = Color.BLACK;
    private static final Color TEXT_GRAY = new Color(100, 100, 100);
    private static final Color BTN = new Color(60, 60, 60);
    private static final Font MONO = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font SANS = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font SANS_BOLD = new Font("SansSerif", Font.BOLD, 13);
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 18);

    private final FSStatLibRx lib;
    private final AtomicReference<Disposable> currentScan = new AtomicReference<>();
    private final AtomicLong startTimeMs = new AtomicLong(0);
    private final AtomicLong scanToken = new AtomicLong(0);

    private JTextField dirField;
    private JTextField maxFSField;
    private JSpinner bandsSpinner;
    private JButton browseBtn;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel filesLabel;
    private JLabel elapsedLabel;
    private JLabel statusLabel;
    private StatsPanel histogram;
    private volatile FSReport lastReport;

    public ScanView(FSStatLibRx lib) {
        super("FSStat");
        this.lib = Objects.requireNonNull(lib, "lib must not be null");
        buildUI();
    }

    public void display() {
        setVisible(true);
    }

    private void onScanStarted() {
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        dirField.setEnabled(false);
        maxFSField.setEnabled(false);
        bandsSpinner.setEnabled(false);
        browseBtn.setEnabled(false);

        filesLabel.setText("0");
        elapsedLabel.setText("0 ms");
        setStatus("Scanning...", TEXT_GRAY);
        histogram.clear();
    }

    private void onUpdate(FSReport report, long elapsedMs) {
        filesLabel.setText(String.format("%,d", report.totalFiles()));
        elapsedLabel.setText(elapsedMs + " ms");
        histogram.setReport(report);
    }

    private void onScanCompleted(FSReport report, long elapsedMs) {
        resetControls();
        filesLabel.setText(String.format("%,d", report.totalFiles()));
        elapsedLabel.setText(elapsedMs + " ms");
        setStatus("Completed!", TEXT_BLACK);
        histogram.setReport(report);
    }

    private void onScanStopped() {
        resetControls();
        setStatus("Stopped", TEXT_GRAY);
    }

    private void onScanError(Throwable error) {
        resetControls();
        setStatus("Error: " + error.getMessage(), TEXT_BLACK);
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


    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 680));
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL_BG);
        p.setBorder(new MatteBorder(0, 0, 1, 0, BORDER));

        JLabel title = new JLabel("  FSStat");
        title.setFont(TITLE_FONT);
        title.setForeground(TEXT_BLACK);
        title.setBorder(new EmptyBorder(14, 18, 14, 0));

        p.add(title, BorderLayout.WEST);
        return p;
    }

    private JComponent buildCenter() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(BG);

        JPanel top = new JPanel(new BorderLayout(0, 0));
        top.setBackground(BG);
        top.add(buildConfigPanel(), BorderLayout.NORTH);
        top.add(buildStatsRow(), BorderLayout.SOUTH);

        histogram = new StatsPanel();

        p.add(top, BorderLayout.NORTH);
        p.add(histogram, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildConfigPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(PANEL_BG);
        p.setBorder(new EmptyBorder(16, 20, 14, 20));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: directory
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weightx = 0;
        p.add(label("Directory"), gc);

        dirField = field(System.getProperty("user.dir"));
        gc.gridx = 1;
        gc.weightx = 1;
        p.add(dirField, gc);

        browseBtn = button("Browse...", BTN);
        browseBtn.addActionListener(this::onBrowse);
        gc.gridx = 2;
        gc.weightx = 0;
        p.add(browseBtn, gc);

        // Row 1: max file size + bands + buttons
        gc.gridx = 0;
        gc.gridy = 1;
        gc.weightx = 0;
        p.add(label("Max file size (bytes)"), gc);

        maxFSField = field("10000");
        maxFSField.setPreferredSize(new Dimension(140, 28));
        gc.gridx = 1;
        gc.weightx = 0.4;
        gc.fill = GridBagConstraints.NONE;
        p.add(maxFSField, gc);

        gc.gridx = 2;
        gc.weightx = 0;
        JPanel bandsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bandsPanel.setBackground(PANEL_BG);
        bandsPanel.add(label("Bands"));
        bandsSpinner = new JSpinner(new SpinnerNumberModel(5, 2, 32, 1));
        styleSpinner(bandsSpinner);
        bandsPanel.add(bandsSpinner);
        p.add(bandsPanel, gc);

        // Row 2: buttons
        gc.gridx = 1;
        gc.gridy = 2;
        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnPanel.setBackground(PANEL_BG);

        startBtn = button("Start", START_BG);
        stopBtn = button("Stop", STOP_BG);
        stopBtn.setEnabled(false);

        startBtn.addActionListener(this::onStart);
        stopBtn.addActionListener(e -> stopScan());

        btnPanel.add(startBtn);
        btnPanel.add(stopBtn);
        p.add(btnPanel, gc);

        return p;
    }

    private JPanel buildStatsRow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 10));
        p.setBackground(BG);
        p.setBorder(new MatteBorder(1, 0, 1, 0, BORDER));

        filesLabel = stat("0");
        elapsedLabel = stat("0 ms");
        statusLabel = new JLabel("Idle");
        statusLabel.setFont(SANS_BOLD);
        statusLabel.setForeground(TEXT_GRAY);

        p.add(statGroup("Files", filesLabel));
        p.add(separator());
        p.add(statGroup("Elapsed", elapsedLabel));
        p.add(separator());
        p.add(statGroup("Status", statusLabel));
        return p;
    }

    private JPanel statGroup(String title, JLabel value) {
        JPanel g = new JPanel(new BorderLayout(0, 2));
        g.setBackground(BG);
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.PLAIN, 10));
        t.setForeground(TEXT_GRAY);
        g.add(t, BorderLayout.NORTH);
        g.add(value, BorderLayout.CENTER);
        return g;
    }

    private JLabel stat(String initial) {
        JLabel l = new JLabel(initial);
        l.setFont(new Font("Monospaced", Font.BOLD, 15));
        l.setForeground(TEXT_BLACK);
        return l;
    }

    private JSeparator separator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 30));
        sep.setForeground(BORDER);
        return sep;
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
        startTimeMs.set(System.currentTimeMillis());
        lastReport = null;

        ReportParameters parameters = new ReportParameters(directory, maxFileSize, numBands);
        Disposable d = lib.getFSReport(parameters).subscribe(
                report -> SwingUtilities.invokeLater(() -> {
                    if (scanToken.get() != token) return;
                    lastReport = report;
                    long elapsed = System.currentTimeMillis() - startTimeMs.get();
                    onUpdate(report, elapsed);
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    if (scanToken.get() != token) return;
                    finishScan();
                    onScanError(error);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    if (scanToken.get() != token) return;
                    finishScan();
                    long elapsed = System.currentTimeMillis() - startTimeMs.get();
                    FSReport finalReport = (lastReport != null)
                            ? lastReport
                            : emptyReport(maxFileSize, numBands);
                    onScanCompleted(finalReport, elapsed);
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

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(SANS);
        l.setForeground(TEXT_GRAY);
        return l;
    }

    private JTextField field(String text) {
        JTextField tf = new JTextField(text);
        tf.setBackground(Color.WHITE);
        tf.setForeground(TEXT_BLACK);
        tf.setFont(MONO);
        tf.setBorder(BorderFactory.createLineBorder(BORDER));
        return tf;
    }

    private JButton button(String text, Color bgColor) {
        JButton b = new JButton(text);
        b.setFont(SANS_BOLD);
        b.setForeground(Color.BLACK);
        b.setBackground(bgColor);
        b.setBorder(BorderFactory.createEmptyBorder(7, 18, 7, 18));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleSpinner(JSpinner s) {
        s.setBackground(Color.WHITE);
        s.setPreferredSize(new Dimension(70, 28));
        JComponent editor = s.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(Color.WHITE);
            de.getTextField().setForeground(TEXT_BLACK);
            de.getTextField().setFont(MONO);
            de.getTextField().setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }
    }
}
