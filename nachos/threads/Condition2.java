package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;


/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;

		waitQueue = new LinkedList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();
		conditionLock.release();

		KThread curr = KThread.currentThread();
		waitQueue.add(curr);
		KThread.sleep();

		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean intStatus = Machine.interrupt().disable();

		if(!waitQueue.isEmpty()) {
			KThread woken = waitQueue.pop();
			while(!waitQueue.isEmpty() && woken.isReady()) {
				woken = waitQueue.pop();
			}
			if(!woken.isReady()) {
				woken.ready();
				if(ThreadedKernel.alarm.isWaiting(woken)) {
					ThreadedKernel.alarm.cancel(woken);
				}
			}
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		while(!waitQueue.isEmpty()) {
			this.wake();
		}
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
        public void sleepFor(long timeout) {
			Lib.assertTrue(conditionLock.isHeldByCurrentThread());
			boolean intStatus = Machine.interrupt().disable();
			conditionLock.release();

			KThread curr = KThread.currentThread();
			waitQueue.add(curr);

			ThreadedKernel.alarm.waitUntil(timeout);

			conditionLock.acquire();
			Machine.interrupt().restore(intStatus);
		}

        private Lock conditionLock;

		private LinkedList<KThread> waitQueue;

		// Place Condition2 testing code in the Condition2 class.

    // Example of the "interlock" pattern where two threads strictly
    // alternate their execution with each other using a condition
    // variable.  (Also see the slide showing this pattern at the end
    // of Lecture 6.)

    private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   // signal
                    cv.sleep();  // wait
                }
                lock.release();
            }
        }

        public InterlockTest () {
            lock = new Lock();
            cv = new Condition2(lock);

            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");

            ping.fork();
            pong.fork();

            // We need to wait for ping to finish, and the proper way
            // to do so is to join on ping.  (Note that, when ping is
            // done, pong is sleeping on the condition variable; if we
            // were also to join on pong, we would block forever.)
            // For this to work, join must be implemented.  If you
            // have not implemented join yet, then comment out the
            // call to join and instead uncomment the loop with
            // yields; the loop has the same effect, but is a kludgy
            // way to do it.
            ping.join();
            // for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }
    }

	// Place Condition2 test code inside of the Condition2 class.

    // Test programs should have exactly the same behavior with the
    // Condition and Condition2 classes.  You can first try a test with
    // Condition, which is already provided for you, and then try it
    // with Condition2, which you are implementing, and compare their
    // behavior.

    // Do not use this test program as your first Condition2 test.
    // First test it with more basic test programs to verify specific
    // functionality.

    public static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }

    // Invoke Condition2.selfTest() from ThreadedKernel.selfTest()
	    // Place sleepFor test code inside of the Condition2 class.

	private static void sleepForTest1() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
					" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	private static void sleepForTest2() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread poopie = new KThread(new Runnable () {
			public void run() {
				System.out.println("poopie pants is working!");
				lock.acquire();
				cv.wake();
				lock.release();
			}
		});
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		poopie.fork();
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
					" woke up again, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	private static void sleepForTest3() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread dingus1 = new KThread(new Runnable () {
			public void run() {
				System.out.println("before dingus1 sleep: " + Machine.timer().getTime());
				lock.acquire();
				cv.sleepFor(10000);
				lock.release();
				System.out.println("dingus1 post: " + Machine.timer().getTime());
			}
		});
		KThread dingus2 = new KThread(new Runnable () {
			public void run() {
				System.out.println("before dingus2 sleep: " + Machine.timer().getTime());
				lock.acquire();
				cv.sleepFor(50000);
				lock.release();
				System.out.println("dingus2 post: " + Machine.timer().getTime());
			}
		});
		KThread dingus3 = new KThread(new Runnable () {
			public void run() {
				System.out.println("before dingus3 sleep: " + Machine.timer().getTime());
				lock.acquire();
				cv.sleepFor(100000);
				lock.release();
				System.out.println("dingus3post : " + Machine.timer().getTime());
			}
		});

		dingus1.fork();
		dingus2.fork();
		dingus3.fork();

		dingus1.join();
		dingus2.join();
		dingus3.join();
		System.out.println("test3 complete!");
	}

	private static void sleepForTest4() {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread dingus1 = new KThread(new Runnable () {
			public void run() {
				System.out.println("before dingus1 sleep: " + Machine.timer().getTime());
				lock.acquire();
				cv.sleepFor(10000);
				lock.release();
				System.out.println("dingus1 post: " + Machine.timer().getTime());
			}
		});
		KThread dingus2 = new KThread(new Runnable () {
			public void run() {
				System.out.println("before dingus2 sleep: " + Machine.timer().getTime());
				lock.acquire();
				cv.sleepFor(50000);
				lock.release();
				System.out.println("dingus2 post: " + Machine.timer().getTime());
			}
		});
		KThread dingus3 = new KThread(new Runnable () {
			public void run() {
				System.out.println("before dingus3 sleep: " + Machine.timer().getTime());
				lock.acquire();
				cv.sleepFor(100000);
				lock.release();
				System.out.println("dingus3post : " + Machine.timer().getTime());
			}
		});

		KThread dingus4 = new KThread(new Runnable () {
			public void run() {
				lock.acquire();
				cv.wakeAll();
				lock.release();
			}
		});

		dingus1.fork();
		dingus2.fork();
		dingus3.fork();
		dingus4.fork();

		dingus4.join();
		dingus1.join();
		dingus2.join();
		dingus3.join();

		System.out.println("test4 complete!");
	}
			
    public static void selfTest() {
        //new InterlockTest();
		//sleepForTest1();
		//sleepForTest2();
		//sleepForTest3();
		sleepForTest4();
		//cvTest5();
    }
}

