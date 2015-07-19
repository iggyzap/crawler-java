package com.izapolsky.crawler;

/**
 * Pair class to hold 2 values
 * @param <F>
 * @param <S>
 */
public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }
}
