package com.zzynes.sarcophag.NET;

import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class PacketUtil {

    private PacketUtil() {
    }

    // enqueue в главный поток + отметка об обработке, чтобы не забывать вторую строку
    public static void run(Supplier<NetworkEvent.Context> ctx, Runnable work) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(work);
        context.setPacketHandled(true);
    }
}
