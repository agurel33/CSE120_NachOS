package nachos.threads;

public class Pair {
    public Long wakeTime;
    public KThread thread;

    public Pair(Long wakeTime, KThread thread) {
        this.wakeTime = wakeTime;
        this.thread = thread;
    }
}
