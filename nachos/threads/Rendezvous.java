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
    HashMap<Integer, Boolean> usedMappy;

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
        if(valueMappy.containsKey(tag) && !usedMappy.get(tag)) {
            int to_return = valueMappy.get(tag);
            valueMappy.replace(tag, value);
            condy.wakeAll();
            usedMappy.replace(tag, true);
            return to_return;
        }
        else {
            valueMappy.put(tag, value);
            usedMappy.put(tag,false);
            condy.sleep();
            if (usedMappy.get(tag)) {
                usedMappy.replace(tag, false);
                int to_return = valueMappy.get(tag);
                valueMappy.remove(tag);
                return to_return;
            } else {
                condy.sleep();
            }
        }
        return 0;
    }
}
