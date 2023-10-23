package nachos.threads;

public class MyPair {
    public Long wakeTime;
    public KThread thread;

    public MyPair(Long wakeTime, KThread thread) {
        this.wakeTime = wakeTime;
        this.thread = thread;
    }

    public Long getWakeTime() {
        return this.wakeTime;
    }

    public KThread getThread() {
        return this.thread;
    }
}
