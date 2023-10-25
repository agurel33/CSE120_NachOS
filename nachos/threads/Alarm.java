package nachos.threads;
import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private ArrayList<MyPair> waitQueue;
	private ThreadComparator comp;


	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		waitQueue = new ArrayList<MyPair>();

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		ArrayList<MyPair> removal = new ArrayList<>();
		for (MyPair curr:waitQueue) {
			if (curr.getWakeTime() <= Machine.timer().getTime()){
				curr.getThread().ready();
				removal.add(curr);
			}
		}
		for(MyPair curr:removal) {
			waitQueue.remove(curr);
		}
		KThread.yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		if (x <=0){
			return;
		}
		boolean status = Machine.interrupt().disable();
		KThread toAdd = KThread.currentThread(); 
		long wakeTime = Machine.timer().getTime() + x;
		MyPair pToAdd = new MyPair(wakeTime, toAdd);
		waitQueue.add(pToAdd);
		KThread.sleep();
		Machine.interrupt().restore(status);
		// while (wakeTime > Machine.timer().getTime())
		// KThread.yield();
	}

    /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {
		for (MyPair t : waitQueue) {
			if (t.getThread() == thread) {
				t.getThread().ready();
				waitQueue.remove(t);
				return true;
			}
		}
		return false;
	}

	public boolean isWaiting(KThread thread) {
		for(MyPair t: waitQueue) {
			if(t.getThread() == thread) {
				return true;
			}
		}
		return false;
	}

	 // Add Alarm testing code to the Alarm class
    
	 public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
	
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	public static void alarmTest2() {
		long t0,t1;

		t0 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(-10);
		t1 = Machine.timer().getTime();
		System.out.println("alarmTest2 (is -10): waited for " + (t1-t0) + " ticks");
	}
 
	public static void alarmTest3() {
		long t0,t1;

		t0 = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(0);
		t1 = Machine.timer().getTime();
		System.out.println("alarmTest3 (is 0): waited for " + (t1-t0) + " ticks");
	}

	public static void alarmTest4() {
		KThread threadyy = new KThread(new Runnable () {
			public void run() {
				ThreadedKernel.alarm.waitUntil(50000);
				System.out.println("Threadyy printed! (3)");
			}
		});

		KThread kreadyy = new KThread(new Runnable () {
			public void run() {
				ThreadedKernel.alarm.waitUntil(5000);
				System.out.println("Kreadyy printed! (1)");
			}
		});
		threadyy.fork();
		kreadyy.fork();
		ThreadedKernel.alarm.waitUntil(10000);
		System.out.println("Main printed! (2)");
		kreadyy.join();
		threadyy.join();
	}

	public static void alarmTest5() {
		KThread trd = new KThread(new Runnable() {
			public void run() {
				long t2 = Machine.timer().getTime();
				ThreadedKernel.alarm.waitUntil(100);
				long t3 = Machine.timer().getTime();
				System.out.println("trd is printing: " + (t3-t2));
			}
		});

		long t0 = Machine.timer().getTime();
		trd.fork();
		ThreadedKernel.alarm.waitUntil(100);
		long t1 = Machine.timer().getTime();
		trd.join();

		System.out.println("time waited for main: " + (t1-t0));
	}
		// Implement more test methods here ...
	
		// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();
		alarmTest2();
		alarmTest3();
		alarmTest4();
		alarmTest5();
		// Invoke your other test methods here ...
	}
}
