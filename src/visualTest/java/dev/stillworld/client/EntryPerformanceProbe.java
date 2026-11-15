package dev.stillworld.client;

import dev.stillworld.Stillworld;
import dev.stillworld.network.PhaseNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Opt-in diagnostics in the development source set only; never shipped in the mod. */
@Mod(value=Stillworld.ID,dist=Dist.CLIENT)
public final class EntryPerformanceProbe {
    private final Path output=Path.of(System.getProperty("stillworld.visualTest.output"));
    private boolean installed;
    private long lastFrame,start,beginNanos,chunkNanos,chunkMax;
    private int entry,chunks;
    private final ArrayList<Double> frames=new ArrayList<>();
    private Recording recording;

    public EntryPerformanceProbe(IEventBus bus) {
        if(!Boolean.getBoolean("stillworld.entryProfile"))return;
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }

    private void tick(ClientTickEvent.Post event) {
        if(!installed&&Minecraft.getInstance().level!=null) {
            installed=true;
            try {
                recording=new Recording(Configuration.getConfiguration("profile"));
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));recording.start();
            } catch(Exception failure) {throw new IllegalStateException(failure);}
            var states=PhaseNetwork.clientHandler;
            PhaseNetwork.clientHandler=tag->{
                if(tag.getString("op").equals("end"))finish();
                if(!tag.getString("op").equals("begin")){states.accept(tag);return;}
                finish();entry++;chunks=0;chunkNanos=chunkMax=0;frames.clear();start=System.nanoTime();
                try {states.accept(tag);} finally {beginNanos=System.nanoTime()-start;}
            };
            var reality=PhaseNetwork.realityHandler;
            PhaseNetwork.realityHandler=(session,packet)->{
                long before=System.nanoTime();
                try {reality.accept(session,packet);} finally {
                    if(start!=0&&packet instanceof ClientboundLevelChunkWithLightPacket) {
                        long elapsed=System.nanoTime()-before;chunkNanos+=elapsed;chunkMax=Math.max(chunkMax,elapsed);chunks++;
                    }
                }
            };
        }
        if(start!=0&&System.nanoTime()-start>3_000_000_000L)finish();
    }

    private void frame(RenderGuiEvent.Post event) {
        long now=System.nanoTime();
        if(start!=0&&lastFrame!=0)frames.add((now-lastFrame)/1e6);
        lastFrame=now;
    }

    private void finish() {
        if(start==0)return;
        long end=System.nanoTime();start=0;
        try {
            Files.createDirectories(output);
            String text="entry="+entry+"\nbegin_ms="+beginNanos/1e6+"\nmirror_chunks="+chunks
                    +"\nmirror_chunks_total_ms="+chunkNanos/1e6+"\nmirror_chunk_max_ms="+chunkMax/1e6
                    +"\nmax_frame_ms="+frames.stream().mapToDouble(Double::doubleValue).max().orElse(0)
                    +"\nframes_ms="+frames+"\nend_nano="+end+"\n";
            Files.writeString(output.resolve("entry-"+entry+"-performance.txt"),text);
            if(recording!=null){recording.stop();recording.dump(output.resolve("entry-1.jfr"));recording.close();recording=null;}
        } catch(Exception failure){throw new IllegalStateException(failure);}
    }
}
