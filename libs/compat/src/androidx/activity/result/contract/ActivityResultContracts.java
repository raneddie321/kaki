package androidx.activity.result.contract;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.IntentSenderRequest;

public final class ActivityResultContracts {
    private ActivityResultContracts() {}

    /** Launches an IntentSender; the result is an {@link ActivityResult}. */
    public static final class StartIntentSenderForResult extends ActivityResultContract<IntentSenderRequest, ActivityResult> {
        public StartIntentSenderForResult() {}
    }
}
