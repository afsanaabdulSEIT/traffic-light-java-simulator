/*
 * File: TrafficLightEngine.java
 * Author: Afsana Abdul
 * Date: March 3, 2026
 * Course: CMSC 335
 * Project: Project 3 – Traffic Simulation
 *
 * Description:
 * This class controls the behavior of a traffic light at an
 * intersection. Each traffic light runs in its own thread and
 * cycles through red, yellow, and green states according to
 * configured time intervals.
 */

public class TrafficLightEngine implements Runnable {

    private TrafficLightColor color;
    private final int greenMs;
    private final int yellowMs;
    private final int redMs;

    private volatile boolean running = false;
    private volatile boolean paused = false;

    public TrafficLightEngine(TrafficLightColor start, int greenMs, int yellowMs, int redMs) {
        this.color = start;
        this.greenMs = greenMs;
        this.yellowMs = yellowMs;
        this.redMs = redMs;
    }

    public synchronized void startEngine() {
        running = true;
        paused = false;
        notifyAll();
    }

    public synchronized void pauseEngine() {
        paused = true;
    }

    public synchronized void resumeEngine() {
        paused = false;
        notifyAll();
    }

    public synchronized void stopEngine() {
        running = false;
        paused = false;
        notifyAll();
    }

    public synchronized TrafficLightColor getColor() {
        return color;
    }

    @Override
    public void run() {
        // Thread runs forever, but “active work” happens only when running == true
        while (true) {
            synchronized (this) {
                while (!running || paused) {
                    try { wait(); } catch (InterruptedException ignored) {}
                }
            }

            try {
                switch (color) {
                    case GREEN -> Thread.sleep(greenMs);
                    case YELLOW -> Thread.sleep(yellowMs);
                    case RED -> Thread.sleep(redMs);
                }
            } catch (InterruptedException ignored) {}

            // Only advance if still running (and not paused)
            synchronized (this) {
                if (!running || paused) continue;

                color = switch (color) {
                    case GREEN -> TrafficLightColor.YELLOW;
                    case YELLOW -> TrafficLightColor.RED;
                    case RED -> TrafficLightColor.GREEN;
                };
            }
        }
    }
}