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
    HashMap<Integer, Integer> valueMappy;
    HashMap<Integer, Integer> usedMappy;
    
    HashMap<Integer,Lock> groupLocky;
    HashMap<Integer, Condition2> groupCondy;

    public Rendezvous () {
        valueMappy = new HashMap<>();
        usedMappy = new HashMap<>();

        groupLocky = new HashMap<>();
        groupCondy = new HashMap<>();
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
            if(!groupLocky.get(tag).isHeldByCurrentThread()) {
                groupLocky.get(tag).acquire();
            }
            int to_return = valueMappy.get(tag);
            valueMappy.replace(tag, value);
            //usedMappy.replace(tag, usedMappy.get(tag));
            groupCondy.get(tag).wakeAll();
            groupLocky.get(tag).release();
            return to_return;
        }
        else {
            if(groupLocky.get(tag) == null) {
                Lock locky = new Lock();
                groupLocky.put(tag,locky);
            }
            if(groupCondy.get(tag) == null) {
                Condition2 condy = new Condition2(groupLocky.get(tag));
                groupCondy.put(tag,condy);
            }
            if(!groupLocky.get(tag).isHeldByCurrentThread()) {
                groupLocky.get(tag).acquire();
            }
            valueMappy.put(tag, value);
            if(usedMappy.get(tag) == null) {
                usedMappy.put(tag,0);
            }
            usedMappy.replace(tag,usedMappy.get(tag) + 1);
            
            groupCondy.get(tag).sleep();

            usedMappy.replace(tag, usedMappy.get(tag) - 1);
            int to_return = valueMappy.get(tag);
            valueMappy.remove(tag);
            return to_return;

            //while(true) {
                //if (usedMappy.get(tag) > 0) {
                    
                //} 
                // else {
                //     if(!groupLocky.get(tag).isHeldByCurrentThread()) {
                //         groupLocky.get(tag).acquire();
                //     }
                //      groupCondy.get(tag).sleep();
                // }
            //}
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

        System.out.println("One pair of threads works");
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
        System.out.println("Multiple thread works");

    }

    private static void superDopeTest() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
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
                int tag = 2;
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
                int tag = 1;
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
                int tag = 2;
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
        System.out.println("Super dope test works");
    }


    public static void selfTest() {
    // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        ourRendeTest1();
        superDopeTest();
    }
}
