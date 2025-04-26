
import java.io.*;
import java.util.*;

// CPU Scheduler simulating FCFS, Round-Robin, and Priority scheduling
public class CPUsched {
    // Process Control Block for job details
    static class PCB {
        int id, burstTime, remainingTime, priority, memoryRequired;
        int waitingTime = 0, turnaroundTime = 0, startTime = -1, endTime = -1;
        String state = "NEW";

        PCB(int id, int burstTime, int priority, int memoryRequired) {
            this.id = id;
            this.burstTime = burstTime;
            this.remainingTime = burstTime;
            this.priority = priority;
            this.memoryRequired = memoryRequired;
        }
    }

    // System calls
    static void createProcess(PCB pcb) { System.out.println("System Call: Create P" + pcb.id); }
    static void allocateMemory(PCB pcb) { System.out.println("System Call: Allocate memory for P" + pcb.id); }
    static void deallocateMemory(PCB pcb) { System.out.println("System Call: Deallocate memory for P" + pcb.id); }
    static void terminateProcess(PCB pcb) { System.out.println("System Call: Terminate P" + pcb.id); }
    static void setState(PCB pcb, String state) {
        pcb.state = state;
        System.out.println("System Call: Set P" + pcb.id + " state to " + state);
    }

    public static void main(String[] args) {
        List<PCB> jobQueue = new ArrayList<>();
        List<PCB> readyQueue = new ArrayList<>();
        java.util.concurrent.locks.Lock lock = new java.util.concurrent.locks.ReentrantLock();

        // Thread to read job.txt
        Thread fileReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader("C:\\Users\\aSus\\Desktop\\job.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        String[] parts = line.split("[:;]");
                        if (parts.length != 4) throw new IllegalArgumentException("Invalid format");
                        int id = Integer.parseInt(parts[0].trim());
                        int burst = Integer.parseInt(parts[1].trim());
                        int priority = Integer.parseInt(parts[2].trim());
                        int memory = Integer.parseInt(parts[3].trim());
                        if (burst <= 0 || priority < 1 || priority > 8 || memory <= 0)
                            throw new IllegalArgumentException("Invalid values");
                        PCB pcb = new PCB(id, burst, priority, memory);
                        createProcess(pcb);
                        lock.lock();
                        try { jobQueue.add(pcb); } finally { lock.unlock(); }
                    } catch (Exception e) {
                        System.err.println("Error parsing: " + line + " (" + e.getMessage() + ")");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading job.txt: " + e.getMessage());
            }
        });

        // Thread to manage memory
        Thread memoryManager = new Thread(() -> {
            int usedMemory = 0;
            while (true) {
                lock.lock();
                try {
                    if (jobQueue.isEmpty()) break;
                    for (int i = 0; i < jobQueue.size(); i++) {
                        PCB pcb = jobQueue.get(i);
                        if (usedMemory + pcb.memoryRequired <= 2048) {
                            jobQueue.remove(i);
                            readyQueue.add(pcb);
                            usedMemory += pcb.memoryRequired;
                            allocateMemory(pcb);
                            setState(pcb, "READY");
                            break;
                        }
                    }
                } finally { lock.unlock(); }
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
        });

        fileReader.start();
        try { fileReader.join(); } catch (InterruptedException e) {}
        memoryManager.start();
        try { memoryManager.join(); } catch (InterruptedException e) {}

        // User input for algorithm
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nSelect scheduling algorithm:");
            System.out.println("1. FCFS");
            System.out.println("2. Round Robin (Quantum=7)");
            System.out.println("3. Priority");
            System.out.println("0. Exit");
            System.out.print("Enter choice (0-3): ");
            int choice = -1;
            try { choice = scanner.nextInt(); } catch (Exception e) { scanner.nextLine(); }
            if (choice == 0) break;
            List<PCB> processes = deepCopy(readyQueue);
            switch (choice) {
                case 1 -> fcfs(processes);
                case 2 -> roundRobin(processes, 7);
                case 3 -> priority(processes);
                default -> System.out.println("Invalid choice! Please enter 0-3.");
            }
        }
        scanner.close();

    }
    // Deep copy of process list
    static List<PCB> deepCopy(List<PCB> original) {
        List<PCB> copy = new ArrayList<>();
        for (PCB p : original)
            copy.add(new PCB(p.id, p.burstTime, p.priority, p.memoryRequired));
        return copy;
    }

    // FCFS scheduling
    static void fcfs(List<PCB> list) {
        int time = 0;
        System.out.println("\nFCFS Gantt Chart:");
        System.out.print("0");
        for (PCB p : list) {
            p.startTime = time;
            time += p.burstTime;
            p.endTime = time;
            p.waitingTime = p.startTime;
            p.turnaroundTime = p.endTime;
            System.out.print(" | P" + p.id + " | " + time);
            terminateProcess(p);
            deallocateMemory(p);
        }
        System.out.println("\n");
        printStats(list, "FCFS");
    }

    // Round-Robin scheduling
    static void roundRobin(List<PCB> list, int quantum) {
        int time = 0;
        Queue<PCB> queue = new LinkedList<>(list);
        System.out.println("\nRound Robin (Quantum=" + quantum + ") Gantt Chart:");
        System.out.print("0");
        while (!queue.isEmpty()) {
            PCB p = queue.poll();
            if (p.startTime == -1) p.startTime = time;
            int run = Math.min(p.remainingTime, quantum);
            time += run;
            p.remainingTime -= run;
            System.out.print(" | P" + p.id + " | " + time);
            if (p.remainingTime > 0) {
                queue.add(p);
            } else {
                p.endTime = time;
                p.turnaroundTime = p.endTime;
                p.waitingTime = p.turnaroundTime - p.burstTime;
                terminateProcess(p);
                deallocateMemory(p);
            }
        }
        System.out.println("\n");
        printStats(list, "Round Robin");
    }

    // Priority scheduling
    static void priority(List<PCB> list) {
        int time = 0;
        list.sort((a, b) -> b.priority - a.priority);
        System.out.println("\nPriority Gantt Chart:");
        System.out.print("0");
        for (PCB p : list) {
            p.startTime = time;
            time += p.burstTime;
            p.endTime = time;
            p.waitingTime = p.startTime;
            p.turnaroundTime = p.endTime;
            if (p.waitingTime > (10 - p.priority) * 10)
                System.out.print("\n⚠️ Starvation detected for P" + p.id);
            System.out.print(" | P" + p.id + " | " + time);
            terminateProcess(p);
            deallocateMemory(p);
        }
        System.out.println("\n");
        printStats(list, "Priority");
    }

    // Print statistics
    static void printStats(List<PCB> list, String algorithm) {
        double avgWait = list.stream().mapToInt(p -> p.waitingTime).average().orElse(0);
        double avgTurn = list.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0);
        System.out.printf("%s - Average Waiting Time: %.2f ms%n", algorithm, avgWait);
        System.out.printf("%s - Average Turnaround Time: %.2f ms%n", algorithm, avgTurn);
    }
}