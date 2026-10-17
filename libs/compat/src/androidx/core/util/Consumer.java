package androidx.core.util;

/** Stand-in for AndroidX's Consumer (the only part of androidx.core the billing library uses). */
public interface Consumer<T> {
    void accept(T t);
}
