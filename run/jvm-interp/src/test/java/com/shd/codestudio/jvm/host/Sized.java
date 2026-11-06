package com.shd.codestudio.jvm.host;

/** A real interface a real supertype implements, to check that an interpreted subclass is recognized as an
 *  instance of an interface reached transitively through its bridged super. */
public interface Sized {
    default boolean measurable() {
        return true;
    }
}
