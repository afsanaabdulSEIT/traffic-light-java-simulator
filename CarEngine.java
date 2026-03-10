/*
 * File: CarEngine.java
 * Author: Afsana Abdul
 * Date: March 3, 2026
 * Course: CMSC 335
 * Project: Project 3 – Traffic Simulation
 *
 * Description:
 * This class represents a car in the traffic simulation.
 * Each car runs in its own thread and moves along the road
 * according to its speed. The car checks the state of upcoming
 * traffic lights and stops when encountering a red light.
 */

public class CarEngine implements Runnable {

    private double xPosition = 0;
    private final double speed; // m/s
    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile String status = "Stopped";

    private final int carIndex;
    private final TrafficLightEngine[] engines;

    public CarEngine(int carIndex, double speed, TrafficLightEngine[] engines) {
        this.carIndex = carIndex;
        this.speed = speed;
        this.engines = engines;
    }

    public synchronized void startEngine() {
        running = true;
        paused = false;
        status = "Moving";
        notifyAll();
    }

    public synchronized void pauseEngine() {
        paused = true;
        status = "Paused";
    }

    public synchronized void resumeEngine() {
        paused = false;
        status = "Moving";
        notifyAll();
    }

    public synchronized void stopEngine() {
        running = false;
        paused = false;
        xPosition = 0;
        status = "Stopped";
        notifyAll();
    }

    public double getX() {
        return xPosition;
    }

    public double getSpeed() {
        return speed;
    }

    public String getStatus() {
        if (!running) return "Stopped";
        if (paused) return "Paused";
        return "Moving";
    }

    public double getY() {
        return 0.0; // assignment assumption: straight line
    }

    @Override
    public void run() {

        while (true) {

            synchronized (this) {
                while (!running || paused) {
                    try { wait(); } catch (InterruptedException ignored) {}
                }
            }

            try {
                Thread.sleep(1000); // 1 second intervals
            } catch (InterruptedException ignored) {}

            // Determine which intersection we're approaching
            int intersectionIndex = (int)(xPosition / 1000) + 1;

            if (intersectionIndex < engines.length && engines[intersectionIndex] != null) {

                TrafficLightColor color = engines[intersectionIndex].getColor();

                // Stop if red and within 5 meters of light
                if (color == TrafficLightColor.RED &&
                        xPosition >= (intersectionIndex * 1000) - 5 &&
                        xPosition < intersectionIndex * 1000) {
                    status = "Stopped (Red)";
                    continue;
                }
            }

            xPosition += speed;// distance = speed * time (1 sec)
            status = "Moving";
            xPosition += speed;
        }
    }
}