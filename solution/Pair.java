package cp1.solution;

public class Pair<T1, T2> {
    private T1 first;
    private T2 second;

    Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    T1 getFirst() {
        return first;
    }

    T2 getSecond() {
        return second;
    }
}
