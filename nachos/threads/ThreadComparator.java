package nachos.threads;
import java.util.Comparator;

public class ThreadComparator implements Comparator<MyPair> {
    public int compare(MyPair t1, MyPair t2) {
        if(t1.getWakeTime() > t2.getWakeTime()) {
            return 1;
        }
        else {
            return -1;
        }
    }
}
