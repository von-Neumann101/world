package dev.stillworld.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.stillworld.ModContent;
import dev.stillworld.Stillworld;
import dev.stillworld.core.Rank;
import dev.stillworld.server.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Test-source-only LAN smoke run. Each role is a separate real Minecraft client. */
@Mod(value = Stillworld.ID, dist = Dist.CLIENT)
public final class MultiplayerVisualSmoke {
    private static final String WORLD = "StillworldMultiplayerSmoke";
    private static final Vec3 HOST = new Vec3(.5, 100, 4.5), OBSERVER = new Vec3(.5, 100, .5);
    private final String role = System.getProperty("stillworld.multi.role", "");
    private final int port = Integer.getInteger("stillworld.multi.port", 25588);
    private final long started = System.nanoTime();
    private final List<String> checks = new ArrayList<>();
    private final boolean[] colors = new boolean[3];
    private final boolean[] insideColors = new boolean[3];
    private Path output;
    private String run, stage = "WORLD";
    private UUID hostId;
    private volatile UUID observerId;
    private volatile boolean serverDone = true;
    private volatile Throwable failure;
    private boolean finished, configured;
    private int age, ordinaryFrames, visualFrames, nameFrames, frameVisuals;
    private float frameMinX=Float.POSITIVE_INFINITY,frameMaxX=Float.NEGATIVE_INFINITY;
    private float previousMinX=Float.NaN,previousMaxX=Float.NaN;
    private int positionJumps,lastInsideSampleAge=-1;
    private boolean insideOriginalSeen,insideBlackWhiteSeen;
    private boolean opaqueBlockEntitiesReady;
    private long observerFrozenTime;

    public MultiplayerVisualSmoke(IEventBus bus) {
        if (!role.equals("host") && !role.equals("observer")) return;
        var game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        require(game.endsWith(Path.of("run", "multiplayer-" + role)), "Dedicated multiplayer gameDirectory required: " + game);
        var allowed = game.getParent().resolve("multiplayer-evidence");
        output = Path.of(System.getProperty("stillworld.multi.evidence", allowed.toString())).toAbsolutePath().normalize();
        require(output.equals(allowed), "Evidence must stay below run/multiplayer-evidence");
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve(role + "-result.txt"), "RUNNING\n");
            if (role.equals("host")) {
                run = UUID.randomUUID().toString();
                write("host-state.txt", "STARTING");
                write("observer-state.txt", "RESET");
            }
        } catch (Exception exception) { throw new IllegalStateException("Cannot initialize multiplayer evidence", exception); }
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::playerRender);
        NeoForge.EVENT_BUS.addListener(this::nameRender);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,this::renderStage);
        NeoForge.EVENT_BUS.addListener(this::guiRendered);
        System.out.println("STILLWORLD MULTIPLAYER " + role + " -> " + output);
    }

    private void tick(ClientTickEvent.Post event) {
        if (finished) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        try {
            if (failure != null) throw new AssertionError("Multiplayer asynchronous check failed", failure);
            require((System.nanoTime() - started) / 1_000_000_000L < 480, "Multiplayer smoke exceeded eight minutes at " + stage);
            require(++age < 2400, "Multiplayer stage exceeded two minutes: " + stage);
            if (mc.screen instanceof AccessibilityOnboardingScreen screen) { screen.onClose(); return; }
            if (role.equals("observer") && stage.equals("ACKNOWLEDGED")) {
                if (read("host-state.txt").equals("DONE")) finish(null);
                return;
            }
            if (mc.player == null || mc.level == null) return;
            if (!configured) {
                require(!Boolean.getBoolean("stillworld.hiddenTestWindow")||org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)==org.lwjgl.glfw.GLFW.GLFW_FALSE,
                        "Automated multiplayer clients must remain hidden");
                require(org.lwjgl.glfw.GLFW.glfwGetInputMode(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_CURSOR)==org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL,
                        "Automated multiplayer cursor must stay normal");
                mc.options.hideGui = false;
                mc.options.fov().set(70);
                mc.options.guiScale().set(2);
                mc.options.entityShadows().set(true);
                // Exercise residual layering after Fabulous' separate transparency composite.
                mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FABULOUS);
                mc.levelRenderer.allChanged();
                configured = true;
            }
            mc.setScreen(null);
            if (role.equals("host")) hostTick(); else observerTick();
        } catch (Throwable exception) { finish(exception); }
    }

    private void hostTick() throws Exception {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        require(server != null, "Host must use a real integrated server");
        var expected = mc.gameDirectory.toPath().resolve("saves/" + WORLD).toAbsolutePath().normalize();
        require(expected.equals(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()),
                "Refused to modify any world except the dedicated " + WORLD);
        switch (stage) {
            case "WORLD" -> {
                hostId = mc.player.getUUID();
                onServer(this::prepare);
                advance("PREPARE");
            }
            case "PREPARE" -> {
                if (!serverDone || ModContent.equippedRank(mc.player) != Rank.RANK1) return;
                require(server.publishServer(GameType.SURVIVAL, false, port), "Cannot publish local test server on " + port);
                checks.add("Dedicated integrated world published on local test port " + port);
                write("host-state.txt", "WAIT_OBSERVER");
                advance("JOIN");
            }
            case "JOIN" -> {
                if (observerId != null && serverDone) {
                    write("host-state.txt", "BASELINE"); advance("BASELINE"); return;
                }
                if (age % 20 == 0 && serverDone) onServer(host -> {
                    var observer = server.getPlayerList().getPlayerByName("Observer");
                    if (observer == null) return;
                    require(server.getPlayerList().getPlayerCount() == 2, "Test server must contain exactly host and Observer");
                    observer.getInventory().clearContent(); equip(observer, null);
                    observer.setData(ModContent.PSYCHE,new dev.stillworld.server.PsycheData());dev.stillworld.server.PsycheManager.applyHealth(observer);
                    observer.setGameMode(GameType.SURVIVAL);
                    observer.connection.teleport(OBSERVER.x, OBSERVER.y, OBSERVER.z, 0, 10);
                    observer.setHealth(observer.getMaxHealth()); observer.getFoodData().setFoodLevel(20);
                    observerId = observer.getUUID();
                    checks.add("Second real server player joined; Observer has no equipped charm");
                });
            }
            case "BASELINE" -> {
                if (!read("observer-state.txt").equals("BASELINE_DONE")) return;
                onServer(host -> {
                    require(PhaseManager.enter(host), "Host charm did not permit server session entry");
                    require(PhaseManager.isPhased(host), "Host server phase state did not become active");
                    require(ModContent.equippedRank(observer(host)) == null, "Observer must initially remain unequipped");
                });
                advance("ENTER");
            }
            case "ENTER" -> {
                if (!serverDone || !StillworldClient.active()) return;
                checks.add("Host entered a real server-backed phase; host client received its session");
                write("host-state.txt", "HIDDEN"); advance("HIDDEN");
            }
            case "HIDDEN" -> {
                if (!read("observer-state.txt").equals("HIDDEN_DONE")) return;
                onServer(host -> {
                    var observer = observer(host); equip(observer, Rank.RANK1);
                    require(ModContent.equippedRank(observer) == Rank.RANK1, "Server did not equip Observer's functional Curios slot");
                    require(!PhaseManager.isPhased(observer), "Observer must stay in reality while wearing the charm");
                });
                advance("EQUIP");
            }
            case "EQUIP" -> {
                if (!serverDone) return;
                write("host-state.txt", "WEARER"); advance("WEARER");
            }
            case "WEARER" -> {
                if (!read("observer-state.txt").equals("WEARER_DONE")) return;
                onServer(host -> {
                    require(PhaseManager.isPhased(host), "Host must remain phased while Observer enters");
                    require(PhaseManager.enter(observer(host)), "Equipped Observer could not enter its own phase");
                    require(PhaseManager.isPhased(observer(host)), "Observer server phase did not become active");
                });
                advance("OBSERVER_ENTER");
            }
            case "OBSERVER_ENTER" -> {
                if (!serverDone) return;
                write("host-state.txt", "INSIDE"); advance("INSIDE");
            }
            case "INSIDE" -> {
                if (!read("observer-state.txt").equals("INSIDE_DONE")) return;
                onServer(host -> {
                    PhaseManager.exit(observer(host));
                    require(!PhaseManager.isPhased(observer(host)), "Observer server phase did not clear");
                    require(PhaseManager.isPhased(host), "Host must stay phased through Observer exit");
                });
                advance("OBSERVER_EXIT");
            }
            case "OBSERVER_EXIT" -> {
                if (!serverDone) return;
                write("host-state.txt", "OBSERVER_OUT"); advance("OBSERVER_OUT");
            }
            case "OBSERVER_OUT" -> {
                if (!read("observer-state.txt").equals("OBSERVER_OUT_DONE")) return;
                onServer(host -> {
                    PhaseManager.exit(host);
                    require(!PhaseManager.isPhased(host), "Host server phase state did not clear");
                });
                advance("EXIT");
            }
            case "EXIT" -> {
                if (!serverDone || StillworldClient.active()) return;
                write("host-state.txt", "RESTORED"); advance("RESTORED");
            }
            case "RESTORED" -> {
                if (!read("observer-state.txt").equals("RESTORED_DONE")) return;
                checks.add("Observer acknowledged nonwearer hiding, outside and post-monochrome wearer passes, its own exit, then ordinary host exit rendering");
                write("host-state.txt", "DONE"); finish(null);
            }
            default -> { }
        }
    }

    private void observerTick() throws Exception {
        var mc = Minecraft.getInstance();
        require(mc.getSingleplayerServer() == null, "Observer must join the other real client over TCP");
        require(mc.player.getGameProfile().getName().equals("Observer"), "Observer launch username must be Observer");
        String remote = read("host-state.txt");
        if (hostId == null) return;
        var host = mc.level.getPlayerByUUID(hostId);
        if (host == null) return;
        aim(HOST.add(0, .9, 0));
        boolean mayBeInside = stage.equals("WAIT_INSIDE") || stage.equals("INSIDE_SAMPLE") || stage.equals("WAIT_OBSERVER_OUT");
        require(mayBeInside || !StillworldClient.active(), "Observer unexpectedly entered a phase session");
        switch (stage) {
            case "WORLD" -> {
                // The host's fixture-ready file can arrive before Curios' network update.
                if (!remote.equals("BASELINE") || mc.player.position().distanceTo(OBSERVER) > .2
                        || ModContent.equippedRank(mc.player) != null) return;
                advance("BASELINE_SAMPLE");
            }
            case "BASELINE_SAMPLE" -> {
                if (age < 30) return;
                require(ModContent.equippedRank(mc.player) == null, "Observer began with an equipped charm");
                require(!StillworldClient.phased(host) && !StillworldRendering.hideOriginal(host), "Baseline host should be ordinary and visible");
                require(ordinaryFrames > 0 && nameFrames > 0, "Baseline did not exercise real player and name rendering");
                shot("00-ordinary-host.png");
                checks.add("Two actual networked player entities rendered normally before entry");
                write("observer-state.txt", "BASELINE_DONE"); advance("WAIT_HIDDEN");
            }
            case "WAIT_HIDDEN" -> {
                if (!remote.equals("HIDDEN") || !StillworldClient.phased(host)) return;
                require(ModContent.equippedRank(mc.player) == null, "Observer received a charm before nonwearer check");
                advance("HIDDEN_SAMPLE");
            }
            case "HIDDEN_SAMPLE" -> {
                if (age < 30) return;
                require(StillworldClient.phased(host) && StillworldRendering.hideOriginal(host), "Nonwearer did not receive host phase state or dispatcher hiding");
                require(ordinaryFrames == 0 && visualFrames == 0 && nameFrames == 0,
                        "Nonwearer saw a body/name render: ordinary=" + ordinaryFrames + ", visual=" + visualFrames + ", names=" + nameFrames);
                require(mc.options.entityShadows().get(), "Shadow rendering must stay enabled during the test");
                shot("01-nonwearer-no-host.png");
                checks.add("Unequipped Observer received phase UUID; ordinary dispatcher/body/name paths were absent (shadow path shares canceled dispatcher)");
                write("observer-state.txt", "HIDDEN_DONE"); advance("WAIT_WEARER");
            }
            case "WAIT_WEARER" -> {
                if (!remote.equals("WEARER") || ModContent.equippedRank(mc.player) != Rank.RANK1) return;
                require(StillworldClient.phased(host), "Host phase state disappeared before wearer check");
                checks.add("Server-equipped functional Curios charm actually synchronized to Observer client");
                advance("WEARER_SAMPLE");
            }
            case "WEARER_SAMPLE" -> {
                if (age < 40 || !colors[0] || !colors[1] || !colors[2]) return;
                require(positionJumps>=3,"Stationary player's echoes did not jump between nearby positions: "+positionJumps);
                require(StillworldRendering.hideOriginal(host), "Equipped Observer leaked the host ordinary renderer");
                require(ordinaryFrames == 0 && nameFrames == 0 && visualFrames > 0,
                        "Wearer must see visual-pass body only; ordinary=" + ordinaryFrames + ", visual=" + visualFrames + ", names=" + nameFrames);
                checks.add("Equipped Observer sees exactly two displaced bodies per frame; stationary-player position jumps="+positionJumps+"; no ordinary name/shadow dispatcher");
                write("observer-state.txt", "WEARER_DONE"); advance("WAIT_INSIDE");
            }
            case "WAIT_INSIDE" -> {
                if (!remote.equals("INSIDE") || !StillworldClient.active() || !monochromeLoaded()) return;
                require(ModContent.equippedRank(mc.player) == Rank.RANK1, "Observer lost its synchronized charm on entry");
                require(StillworldClient.phased(host), "Host phase UUID disappeared while Observer entered");
                observerFrozenTime = mc.level.getGameTime();
                advance("INSIDE_SAMPLE");
            }
            case "INSIDE_SAMPLE" -> {
                if (age < 40 || !insideColors[0] || !insideColors[1] || !insideColors[2]
                        || !insideOriginalSeen || !insideBlackWhiteSeen) return;
                require(positionJumps>=3,"Inside echoes did not flicker between nearby positions: "+positionJumps);
                require(insideOriginalSeen&&insideBlackWhiteSeen,"Original skin must appear and disappear as the selected pair changes");
                require(StillworldClient.active() && monochromeLoaded(), "Observer did not remain inside with monochrome processing");
                require(mc.level.getGameTime() == observerFrozenTime, "Observer's own frozen environment time advanced");
                require(StillworldClient.phased(host) && StillworldRendering.hideOriginal(host), "Inside Observer leaked host ordinary renderer");
                require(ordinaryFrames == 0 && nameFrames == 0 && visualFrames > 0,
                        "Post-monochrome host body must use only visual pass; ordinary=" + ordinaryFrames + ", visual=" + visualFrames + ", names=" + nameFrames);
                require(mc.options.entityShadows().get(), "Shadow rendering must remain enabled during inside sample");
                checks.add("Observer entered its own real session: frozen monochrome background/ground, exactly two flickering residuals, original skin both present and absent; position jumps="+positionJumps);
                write("observer-state.txt", "INSIDE_DONE"); advance("WAIT_OBSERVER_OUT");
            }
            case "WAIT_OBSERVER_OUT" -> {
                if (!remote.equals("OBSERVER_OUT") || StillworldClient.active() || monochromeLoaded()) return;
                require(StillworldClient.phased(host), "Host exited before Observer completed its own exit");
                advance("OBSERVER_OUT_SAMPLE");
            }
            case "OBSERVER_OUT_SAMPLE" -> {
                if (age < 30) return;
                require(!StillworldClient.active() && !monochromeLoaded(), "Observer retained its own phase or monochrome effect after exit");
                require(StillworldClient.phased(host) && StillworldRendering.hideOriginal(host), "Host should still be phased after Observer leaves");
                require(ordinaryFrames == 0 && nameFrames == 0 && visualFrames > 0, "Observer exit did not return to outside wearer visual pass");
                shot("09-observer-out-host-still-phased.png");
                checks.add("Observer left its own session first and returned to colored reality while Host remained phased");
                write("observer-state.txt", "OBSERVER_OUT_DONE"); advance("WAIT_RESTORED");
            }
            case "WAIT_RESTORED" -> {
                if (!remote.equals("RESTORED") || StillworldClient.phased(host)) return;
                advance("RESTORED_SAMPLE");
            }
            case "RESTORED_SAMPLE" -> {
                if (age < 30) return;
                require(!StillworldRendering.hideOriginal(host), "Exit did not restore ordinary visibility");
                require(ordinaryFrames > 0 && nameFrames > 0 && visualFrames == 0, "Exit did not restore the ordinary body/name render paths");
                shot("05-exit-ordinary-host.png");
                checks.add("Host exit synchronized to Observer and restored ordinary body and name rendering");
                write("observer-state.txt", "RESTORED_DONE"); advance("ACKNOWLEDGED");
            }
            default -> { }
        }
    }

    private void playerRender(RenderPlayerEvent.Pre event) {
        if (!role.equals("observer") || hostId == null || !event.getEntity().getUUID().equals(hostId)) return;
        if (StillworldRendering.visualPass()) {
            if(!opaqueBlockEntitiesReady)failure=new AssertionError("Afterimages rendered before opaque block entities could supply the background/depth");
            visualFrames++;
            frameVisuals++;
            float x=event.getPoseStack().last().pose().m30();frameMinX=Math.min(frameMinX,x);frameMaxX=Math.max(frameMaxX,x);
        } else ordinaryFrames++;
    }

    private void nameRender(RenderNameTagEvent event) {
        if (role.equals("observer") && hostId != null && event.getEntity().getUUID().equals(hostId)) nameFrames++;
    }

    private void renderStage(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)opaqueBlockEntitiesReady=true;
    }

    private void guiRendered(RenderGuiEvent.Post event) {
        if (!role.equals("observer") || finished)return;
        try {
            if(stage.equals("WEARER_SAMPLE")||stage.equals("INSIDE_SAMPLE")) {
                require(frameVisuals==2,"Expected exactly two simultaneous afterimage bodies, got "+frameVisuals);
                require(frameMaxX-frameMinX>.06,"Afterimage bodies overlap without any visible lateral separation");
                if(Float.isFinite(previousMinX)&&Math.max(Math.abs(frameMinX-previousMinX),Math.abs(frameMaxX-previousMaxX))>.0375)positionJumps++;
                previousMinX=frameMinX;previousMaxX=frameMaxX;
            }
            int slot=Math.min(2,age/12);
            if(stage.equals("WEARER_SAMPLE")&&!colors[slot]) {
                shot("0"+(slot+2)+"-wearer-overlap.png");colors[slot]=true;
            }
            if(!stage.equals("INSIDE_SAMPLE"))return;
            require(StillworldClient.active() && monochromeLoaded(), "Post-effect capture requires Observer's own monochrome session");
            if (age!=lastInsideSampleAge && age%2==0) {
                lastInsideSampleAge=age;
                try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
                    // Fixed camera and scene: these side patches are the green/red wall;
                    // the lower patch is ground; the center patch is the Host's body.
                    double wall = (chroma(image, .38, .42, .42, .58) + chroma(image, .58, .42, .62, .58)) / 2;
                    double ground = chroma(image, .38, .75, .42, .82);
                    double coloredBody = coloredFraction(image, .48, .35, .52, .55);
                    require(wall < 4 && ground < 4, "Observer environment was not monochrome: wall=" + wall + ", ground=" + ground);
                    if(coloredBody>.1)insideOriginalSeen=true;
                    if(coloredBody<.02&&!insideBlackWhiteSeen) {
                        image.writeToFile(output.resolve("11-inside-black-white.png"));insideBlackWhiteSeen=true;
                    }
                    if(!insideColors[slot]) {
                        image.writeToFile(output.resolve("0" + (slot + 6) + "-inside-overlap.png"));
                        checks.add("Inside slot " + slot + " pixel evidence: wall chroma=" + wall + ", ground chroma=" + ground + ", colored body fraction=" + coloredBody);
                        insideColors[slot]=true;
                    }
                }
            }
        } catch (Throwable exception) { failure = exception; }
        finally {frameVisuals=0;frameMinX=Float.POSITIVE_INFINITY;frameMaxX=Float.NEGATIVE_INFINITY;opaqueBlockEntitiesReady=false;}
    }

    private static boolean monochromeLoaded() {
        return StillworldPostEffect.settled();
    }

    private static double chroma(NativeImage image, double x1, double y1, double x2, double y2) {
        long sum = 0; int count = 0;
        for (int y = (int)(image.getHeight() * y1); y < image.getHeight() * y2; y++)
            for (int x = (int)(image.getWidth() * x1); x < image.getWidth() * x2; x++) {
                sum += chroma(image.getPixelRGBA(x, y)); count++;
            }
        return (double)sum / count;
    }

    private static double coloredFraction(NativeImage image, double x1, double y1, double x2, double y2) {
        int colored = 0, count = 0;
        for (int y = (int)(image.getHeight() * y1); y < image.getHeight() * y2; y++)
            for (int x = (int)(image.getWidth() * x1); x < image.getWidth() * x2; x++) {
                if (chroma(image.getPixelRGBA(x, y)) > 12) colored++;
                count++;
            }
        return (double)colored / count;
    }

    private static int chroma(int pixel) {
        int r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private void prepare(ServerPlayer host) {
        var server = host.getServer(); var level = host.serverLevel();
        require(level == server.overworld(), "Dedicated smoke world must be overworld");
        // Gradle's named development clients have offline profiles, not account tokens.
        server.setUsesAuthentication(false);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.setDayTime(6000); level.setWeatherParameters(100000, 0, false, false);
        for (int x = -8; x <= 8; x++) for (int z = -5; z <= 10; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 100; y <= 105; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int x = -4; x <= 4; x++) for (int y = 100; y <= 104; y++)
            level.setBlockAndUpdate(new BlockPos(x, y, 7), (x < 0 ? Blocks.LIME_WOOL : Blocks.RED_WOOL).defaultBlockState());
        // Opaque block entities behind the body must remain visible through it, not overwrite it.
        level.setBlockAndUpdate(new BlockPos(0,100,6),Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(0,101,6),Blocks.CHEST.defaultBlockState());
        host.setGameMode(GameType.SURVIVAL); host.getInventory().clearContent(); equip(host, Rank.RANK1);
        host.setData(ModContent.PSYCHE,new dev.stillworld.server.PsycheData());dev.stillworld.server.PsycheManager.applyHealth(host);
        host.connection.teleport(HOST.x, HOST.y, HOST.z, 180, 0);
        host.setHealth(host.getMaxHealth()); host.getFoodData().setFoodLevel(20);
    }

    private static void equip(ServerPlayer player, Rank rank) {
        CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("stillworld").orElseThrow()
                .getStacks().setStackInSlot(0, rank == null ? ItemStack.EMPTY : new ItemStack(ModContent.CHARMS.get(rank).get()));
        player.containerMenu.broadcastChanges();
    }

    private ServerPlayer observer(ServerPlayer host) {
        var player = host.getServer().getPlayerList().getPlayer(observerId);
        require(player != null, "Observer disconnected during multiplayer test"); return player;
    }

    private void onServer(Consumer<ServerPlayer> action) {
        var server = Minecraft.getInstance().getSingleplayerServer(); serverDone = false;
        server.execute(() -> {
            try {
                var host = server.getPlayerList().getPlayer(hostId);
                require(host != null, "Host disconnected during multiplayer test"); action.accept(host);
            } catch (Throwable exception) { failure = exception; }
            finally { serverDone = true; }
        });
    }

    private void write(String name, String value) throws Exception {
        Files.writeString(output.resolve(name), run + "\n" + (hostId == null ? "pending" : hostId) + "\n" + value + "\n");
    }

    private String read(String name) throws Exception {
        var path = output.resolve(name);
        if (!Files.isRegularFile(path)) return "";
        var lines = Files.readAllLines(path);
        if (lines.size() < 3) return "";
        if (role.equals("observer") && run == null) {
            if (lines.get(1).equals("pending")) return "";
            run = lines.get(0); hostId = UUID.fromString(lines.get(1));
        }
        if (!lines.get(0).equals(run)) return "";
        if (lines.get(2).startsWith("FAIL")) throw new AssertionError("Other real client: " + lines.get(2));
        return lines.get(2);
    }

    private void advance(String next) {
        stage = next; age = ordinaryFrames = visualFrames = nameFrames = 0;
        positionJumps=0;previousMinX=previousMaxX=Float.NaN;
        System.out.println("STILLWORLD MULTIPLAYER " + role + " " + next);
    }

    private static void aim(Vec3 target) {
        var player = Minecraft.getInstance().player; var d = target.subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }

    private void shot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(output.resolve(name)); }
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private void finish(Throwable exception) {
        if (finished) return; finished = true;
        try {
            Files.writeString(output.resolve(role + "-result.txt"), (exception == null ? "PASS" : "FAIL at " + stage + ": " + exception)
                    + "\nrun=" + run + "\n" + String.join("\n", checks) + "\n");
            if (exception != null) {
                write(role + "-state.txt", "FAIL " + exception); shot(role + "-failure.png"); exception.printStackTrace();
            }
            System.out.println("STILLWORLD MULTIPLAYER " + role + (exception == null ? " PASS" : " FAIL"));
        } catch (Exception reportFailure) { reportFailure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
