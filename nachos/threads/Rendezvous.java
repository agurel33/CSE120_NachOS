package nachos.threads;

import java.util.HashMap;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */

    private Lock locky;
    private Condition2 condy;
    HashMap<Integer, Integer> valueMappy;
    HashMap<Integer, Integer> usedMappy;

    public Rendezvous () {
        locky = new Lock();
        condy = new Condition2(locky);
        valueMappy = new HashMap<>();
        usedMappy = new HashMap<>();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        if(valueMappy.containsKey(tag) && usedMappy.get(tag) > 0) {
            int to_return = valueMappy.get(tag);
            valueMappy.replace(tag, value);
            //usedMappy.replace(tag, usedMappy.get(tag));
            if(!locky.isHeldByCurrentThread()) {
                locky.acquire();
            }
            condy.wakeAll();
            locky.release();
            return to_return;
        }
        else {
            valueMappy.put(tag, value);
            if(usedMappy.get(tag) == null) {
                usedMappy.put(tag,1);
            }
            usedMappy.replace(tag,usedMappy.get(tag) + 1);
            if(!locky.isHeldByCurrentThread()) {
                locky.acquire();
            }
            condy.sleep();
            while(true) {
                if (usedMappy.get(tag) > 0) {
                    usedMappy.replace(tag, usedMappy.get(tag) - 1);
                    int to_return = valueMappy.get(tag);
                    valueMappy.remove(tag);
                    return to_return;
                } else {
                    if(!locky.isHeldByCurrentThread()) {
                        locky.acquire();
                    }
                    condy.sleep();
                }
            }
        }
    }

     // Place Rendezvous test code inside of the Rendezvous class.

     public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2");
    
        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
    }
    
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    private static void ourRendeTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2");
         KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 2;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == -2, "Was expecting " + -2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t3.setName("t3");
         KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -2;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t4.setName("t4");
    
        t1.fork(); t2.fork(); t3.fork(); t4.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join(); t3.join(); t4.join();
    }


    public static void selfTest() {
    // place calls to your Rendezvous tests that you implement here
        //rendezTest1();
        ourRendeTest1();
    }
}
