package androidx.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import java.util.ArrayList;
import java.util.List;

/**
 * Small stand-in for AndroidX's ComponentActivity: only what the Play Billing library's
 * ProxyBillingActivityV2 uses (registerForActivityResult with StartIntentSenderForResult).
 */
public class ComponentActivity extends Activity {
    private static final int BASE_CODE = 0x5b00;
    private final List<ActivityResultCallback<Object>> callbacks = new ArrayList<ActivityResultCallback<Object>>();

    @SuppressWarnings("unchecked")
    public final <I, O> ActivityResultLauncher<I> registerForActivityResult(ActivityResultContract<I, O> contract,
            final ActivityResultCallback<O> callback) {
        if (!(contract instanceof ActivityResultContracts.StartIntentSenderForResult)) {
            throw new UnsupportedOperationException("contract not supported: " + contract);
        }
        final int code = BASE_CODE + callbacks.size();
        callbacks.add((ActivityResultCallback<Object>) callback);
        return new ActivityResultLauncher<I>() {
            @Override
            public void launch(I input) {
                IntentSenderRequest r = (IntentSenderRequest) input;
                try {
                    startIntentSenderForResult(r.getIntentSender(), code, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    callback.onActivityResult((O) new ActivityResult(RESULT_CANCELED, null));
                }
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int i = requestCode - BASE_CODE;
        if (i >= 0 && i < callbacks.size()) {
            callbacks.get(i).onActivityResult(new ActivityResult(resultCode, data));
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
