package com.shd.codestudio.jvm.host;

/** A real interface an interpreted object implements and returns across the bridge — the shape of Compose's
 *  {@code DisposableEffectResult} (an inlined {@code onDispose { }} builds an interpreted implementation of it
 *  that the real runtime then casts). Bridged (in {@code com.shd.codestudio.jvm.host}), so it is not interpreted. */
public interface Tagged {
    String tag();
}
