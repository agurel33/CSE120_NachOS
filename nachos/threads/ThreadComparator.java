package nachos.threads;
import java.util.Comparator;

public class ThreadComparator implements Comparator<Pair> {
    public int compare(Pair t1, Pair t2) {
        if(t1.getWakeTime() > t2.getWakeTime()) {
            return 1;
        }
        else {
            return -1;
        }
    }
}
