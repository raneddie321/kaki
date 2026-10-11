package androidx.core.app;

import android.os.Bundle;
import android.os.IBinder;

/** Stand-in for AndroidX's BundleCompat (API 18+ has Bundle.putBinder). */
public final class BundleCompat {
    private BundleCompat() {}

    public static void putBinder(Bundle bundle, String key, IBinder binder) {
        bundle.putBinder(key, binder);
    }

    public static IBinder getBinder(Bundle bundle, String key) {
        return bundle.getBinder(key);
    }
}
