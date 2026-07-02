package app.yukine.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

import app.yukine.playback.manager.PlaybackNoisyReceiverManager;

final class PlaybackNoisyReceiverRegistrarOwner implements PlaybackNoisyReceiverManager.Registrar {
    interface ReceiverRegistry {
        void registerReceiver(BroadcastReceiver receiver, IntentFilter filter);

        void registerReceiverNotExported(BroadcastReceiver receiver, IntentFilter filter);

        void unregisterReceiver(BroadcastReceiver receiver);
    }

    private final ReceiverRegistry receiverRegistry;
    private final int sdkInt;

    PlaybackNoisyReceiverRegistrarOwner(Context context) {
        this(new AndroidReceiverRegistry(context), Build.VERSION.SDK_INT);
    }

    PlaybackNoisyReceiverRegistrarOwner(ReceiverRegistry receiverRegistry, int sdkInt) {
        this.receiverRegistry = receiverRegistry;
        this.sdkInt = sdkInt;
    }

    @Override
    public void register(BroadcastReceiver receiver, IntentFilter filter) {
        if (sdkInt >= 33) {
            receiverRegistry.registerReceiverNotExported(receiver, filter);
            return;
        }
        receiverRegistry.registerReceiver(receiver, filter);
    }

    @Override
    public void unregister(BroadcastReceiver receiver) {
        receiverRegistry.unregisterReceiver(receiver);
    }

    private static final class AndroidReceiverRegistry implements ReceiverRegistry {
        private final Context context;

        private AndroidReceiverRegistry(Context context) {
            this.context = context;
        }

        @Override
        public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            context.registerReceiver(receiver, filter);
        }

        @Override
        public void registerReceiverNotExported(BroadcastReceiver receiver, IntentFilter filter) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
            context.unregisterReceiver(receiver);
        }
    }
}
