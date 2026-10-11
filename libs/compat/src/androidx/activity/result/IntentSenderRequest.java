package androidx.activity.result;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;

public final class IntentSenderRequest {
    final IntentSender intentSender;
    final Intent fillInIntent;
    final int flagsMask, flagsValues;

    IntentSenderRequest(IntentSender s, Intent fill, int mask, int values) {
        intentSender = s;
        fillInIntent = fill;
        flagsMask = mask;
        flagsValues = values;
    }

    public IntentSender getIntentSender() {
        return intentSender;
    }

    public static final class Builder {
        private final IntentSender sender;
        private Intent fill;
        private int mask, values;

        public Builder(PendingIntent pendingIntent) {
            sender = pendingIntent.getIntentSender();
        }

        public Builder(IntentSender intentSender) {
            sender = intentSender;
        }

        public Builder setFillInIntent(Intent i) {
            fill = i;
            return this;
        }

        public Builder setFlags(int values, int mask) {
            this.values = values;
            this.mask = mask;
            return this;
        }

        public IntentSenderRequest build() {
            return new IntentSenderRequest(sender, fill, mask, values);
        }
    }
}
