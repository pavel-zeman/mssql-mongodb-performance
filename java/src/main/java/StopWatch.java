/**
 * Simple class to measure real and CPU time.
 */
public class StopWatch {
    private long start;
    private long cpuStart;

    public StopWatch(long start) {
        this.start = start;
        this.cpuStart = getCpu();
    }

    private long getCpu() {
        return ProcessHandle.current().info().totalCpuDuration().get().toMillis();
    }

    public static StopWatch startNew() {
        return new StopWatch(System.currentTimeMillis());
    }

    public long elapsed() {
        return System.currentTimeMillis() - start;
    }

    public long cpuElapsed() {
        return getCpu() - cpuStart;
    }
}
