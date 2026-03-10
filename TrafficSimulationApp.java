/*
 * File: TrafficSimulationApp.java
 * Author: Afsana Abdul
 * Date: March 3, 2026
 * Course: CMSC 335
 * Project: Project 3 – Traffic Simulation
 *
 * Description:
 * This class is the main graphical user interface for the traffic
 * simulation application. It manages the simulation controls,
 * displays traffic light states, and shows vehicle telemetry data.
 * The class coordinates the traffic light engines and car engines
 * running in separate threads.
 */

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TrafficSimulationApp extends JFrame {

    // ===== Top: Clock =====
    private final JLabel timeLabel = new JLabel("", SwingConstants.CENTER);
    private Timer clockTimer;

    // ===== Middle: Intersections =====
    private final JPanel intersectionsContainer = new JPanel();
    private int intersectionCount = 3;

    // Keep references so we can update later from timers
    private final JLabel[] lightStateLabels = new JLabel[20];
    private final JLabel[] lightVisualLabels = new JLabel[20];

    // Traffic light engines + threads (1-based indexing)
    private final TrafficLightEngine[] engines = new TrafficLightEngine[20];
    private final Thread[] engineThreads = new Thread[20];

    // ===== Bottom: Cars =====
    private DefaultTableModel carModel;
    private JTable carTable;

    private final CarEngine[] cars = new CarEngine[20];
    private final Thread[] carThreads = new Thread[20];
    private int carCount = 3;

    // ===== Controls =====
    private final JButton startBtn = new JButton("Start");
    private final JButton pauseBtn = new JButton("Pause");
    private final JButton contBtn = new JButton("Continue");
    private final JButton stopBtn = new JButton("Stop");
    private final JButton addCarBtn = new JButton("Add Car");
    private final JButton addIntersectionBtn = new JButton("Add Intersection");

    // UI refresh timer (EDT-safe)
    private Timer lightUiTimer;

    // Simulation flags (for UI status display)
    private volatile boolean running = false;
    private volatile boolean paused = false;

    public TrafficSimulationApp() {
        super("Traffic Analyst Console - Simulation");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Build UI
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        // Set initial time label
        updateClock();

        // ===== Clock timer (1 second) =====
        clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.start();

        // ===== Wire button actions =====
        wireActions();

        // ===== Create 3 traffic light engines (example timings) =====
        // NOTE: These values are just for uniqueness; you can change them.
        engines[1] = new TrafficLightEngine(TrafficLightColor.GREEN, 9000, 2500, 11000);
        engines[2] = new TrafficLightEngine(TrafficLightColor.RED, 10000, 2000, 12000);
        engines[3] = new TrafficLightEngine(TrafficLightColor.YELLOW, 8000, 3000, 10000);

        // Start their threads once (they can "idle" until Start is pressed depending on your engine logic)
        for (int i = 1; i <= 3; i++) {
            engineThreads[i] = new Thread(engines[i], "TL-" + i);
            engineThreads[i].start();
        }

        // ===== Initialize 3 cars + start their threads =====
        initCars(); // IMPORTANT: cars need engines[] already created

        // ===== UI timer to repaint lights + refresh car table (EDT-safe) =====
        // Use ONE timer only (do NOT duplicate timers)
        lightUiTimer = new Timer(200, e -> {
            refreshTrafficLights();
            refreshCars();
        });
        lightUiTimer.start();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // ---------------- UI BUILDERS ----------------

    private JPanel buildTopPanel() {
        JPanel p = new JPanel(new BorderLayout());
        timeLabel.setFont(new Font("SansSerif", Font.BOLD, 18));

        Border b = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );
        timeLabel.setBorder(b);

        p.add(timeLabel, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildCenterPanel() {
        intersectionsContainer.setLayout(new BoxLayout(intersectionsContainer, BoxLayout.Y_AXIS));
        intersectionsContainer.setBorder(BorderFactory.createTitledBorder("Intersections"));

        // Create initial 3 intersection panels
        for (int i = 1; i <= intersectionCount; i++) {
            intersectionsContainer.add(buildIntersectionRow(i));
        }

        // Scroll pane so intersections can grow
        JScrollPane scroll = new JScrollPane(intersectionsContainer);
        scroll.setPreferredSize(new Dimension(700, 260));
        return scroll;
    }

    private JPanel buildIntersectionRow(int idx) {
        JPanel row = new JPanel(new BorderLayout(10, 10));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Intersection " + idx);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));

        JLabel state = new JLabel("State: RUNNING (placeholder)");
        lightStateLabels[idx] = state;

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(title);
        left.add(state);

        JLabel visual = new JLabel("●", SwingConstants.CENTER);
        visual.setOpaque(true);
        visual.setPreferredSize(new Dimension(70, 35));
        visual.setBackground(Color.LIGHT_GRAY);
        visual.setForeground(Color.BLACK);
        visual.setFont(new Font("SansSerif", Font.BOLD, 18));
        lightVisualLabels[idx] = visual;

        row.add(left, BorderLayout.CENTER);
        row.add(visual, BorderLayout.EAST);
        return row;
    }

    private JPanel buildBottomPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(10, 10));

        // Controls row
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controls.setBorder(BorderFactory.createTitledBorder("Controls"));
        controls.add(startBtn);
        controls.add(pauseBtn);
        controls.add(contBtn);
        controls.add(stopBtn);
        controls.add(addCarBtn);
        controls.add(addIntersectionBtn);

        // Car telemetry table
        carModel = new DefaultTableModel(
                new Object[]{"Car", "X (m)", "Y (m)", "Speed (m/s)", "Status"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        // Initial 3 rows
        for (int i = 1; i <= carCount; i++) {
            carModel.addRow(new Object[]{"Car " + i, 0, 0, 0.0, "Idle"});
        }

        carTable = new JTable(carModel);
        JScrollPane carScroll = new JScrollPane(carTable);
        carScroll.setBorder(BorderFactory.createTitledBorder("Car Telemetry"));
        carScroll.setPreferredSize(new Dimension(700, 180));

        wrapper.add(controls, BorderLayout.NORTH);
        wrapper.add(carScroll, BorderLayout.CENTER);
        return wrapper;
    }

    // ---------------- LOGIC ----------------

    private void updateClock() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        timeLabel.setText("Time: " + fmt.format(new Date()));
    }

    private void wireActions() {
        startBtn.addActionListener(e -> onStart());
        pauseBtn.addActionListener(e -> onPause());
        contBtn.addActionListener(e -> onContinue());
        stopBtn.addActionListener(e -> onStop());

        addCarBtn.addActionListener(e -> onAddCar());
        addIntersectionBtn.addActionListener(e -> onAddIntersection());
    }

    private void onStart() {
        running = true;
        paused = false;

        for (int i = 1; i <= intersectionCount; i++) {
            if (engines[i] != null) engines[i].startEngine();
        }
        for (int i = 1; i <= carCount; i++) {
            if (cars[i] != null) cars[i].startEngine();
        }
        refreshCars();
        refreshTrafficLights();
    }

    private void onPause() {
        paused = true;
        running = false;

        for (int i = 1; i <= intersectionCount; i++) {
            if (engines[i] != null) engines[i].pauseEngine();
        }
        for (int i = 1; i <= carCount; i++) {
            if (cars[i] != null) cars[i].pauseEngine();
        }
        refreshCars();
        refreshTrafficLights();
    }

    private void onContinue() {
        running = true;
        paused = false;

        for (int i = 1; i <= intersectionCount; i++) {
            if (engines[i] != null) engines[i].resumeEngine();
        }
        for (int i = 1; i <= carCount; i++) {
            if (cars[i] != null) cars[i].resumeEngine();
        }
        refreshCars();
        refreshTrafficLights();
    }

    private void onStop() {
        running = false;
        paused = false;

        for (int i = 1; i <= intersectionCount; i++) {
            if (engines[i] != null) engines[i].stopEngine();
        }
        for (int i = 1; i <= carCount; i++) {
            if (cars[i] != null) cars[i].stopEngine();
        }
        refreshCars();
        refreshTrafficLights();
    }

    // Initializes the initial 3 cars (called once in constructor AFTER engines[] exist)
    private void initCars() {
        carCount = 3;

        // NOTE: Constructor assumed: CarEngine(int carIndex, double speed, TrafficLightEngine[] engines)
        cars[1] = new CarEngine(1, 12.0, engines);
        cars[2] = new CarEngine(2, 10.0, engines);
        cars[3] = new CarEngine(3, 14.0, engines);

        for (int i = 1; i <= carCount; i++) {
            carThreads[i] = new Thread(cars[i], "Car-" + i);
            carThreads[i].start();
        }
    }

    private void onAddCar() {
        if (carCount + 1 >= cars.length) {
            JOptionPane.showMessageDialog(this, "Max cars reached (array limit).", "Limit",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        carCount++;
        int idx = carCount;

        // Add row to table first
        carModel.addRow(new Object[]{"Car " + idx, 0, 0, 11.0, "Idle"});

        // Create & start new car
        cars[idx] = new CarEngine(idx, 11.0, engines);
        carThreads[idx] = new Thread(cars[idx], "Car-" + idx);
        carThreads[idx].start();

        // If simulation is currently running, start it immediately
        if (running) cars[idx].startEngine();
        if (paused) cars[idx].pauseEngine();
    }

    private void onAddIntersection() {
        int next = intersectionCount + 1;
        if (next >= engines.length) {
            JOptionPane.showMessageDialog(this,
                    "Max intersections reached (array limit).",
                    "Limit",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        intersectionCount++;

        // Create a new engine + thread
        engines[next] = new TrafficLightEngine(TrafficLightColor.RED, 9000, 2500, 11000);
        engineThreads[next] = new Thread(engines[next], "TL-" + next);
        engineThreads[next].start();

        // Add UI row
        intersectionsContainer.add(buildIntersectionRow(next));
        intersectionsContainer.revalidate();
        intersectionsContainer.repaint();

        // Apply current sim state
        if (running) engines[next].startEngine();
        if (paused) engines[next].pauseEngine();
    }

    // Updates intersection labels + color box
    private void refreshTrafficLights() {
        for (int i = 1; i <= intersectionCount; i++) {
            if (engines[i] == null) continue;

            TrafficLightColor c = engines[i].getColor();
            String text = (c == null) ? "UNKNOWN" : c.name();

            lightStateLabels[i].setText("State: " + text);

            Color bg = Color.LIGHT_GRAY;
            if (c == TrafficLightColor.GREEN) bg = Color.GREEN;
            else if (c == TrafficLightColor.YELLOW) bg = Color.YELLOW;
            else if (c == TrafficLightColor.RED) bg = Color.RED;

            lightVisualLabels[i].setBackground(bg);
        }
    }

    // Updates X/Y/speed/status in the JTable
    private void refreshCars() {
        // Assumption from assignment: cars move in straight line; Y is always 0.
        for (int i = 1; i <= carCount; i++) {
            if (cars[i] == null) continue;

            int row = i - 1;
            if (row < 0 || row >= carModel.getRowCount()) continue;

            // X from engine
            carModel.setValueAt(Math.round(cars[i].getX()), row, 1); // X (m)
            carModel.setValueAt(0, row, 2);                          // Y (m) always 0
            carModel.setValueAt(cars[i].getSpeed(), row, 3);         // Speed (m/s)

            // Status (adjust this ONE line if your CarEngine uses different method names)
            String status = running ? "Moving" : (paused ? "Paused" : "Stopped");
            carModel.setValueAt(status, row, 4);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TrafficSimulationApp::new);
    }
}
