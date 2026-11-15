package dev.stillworld.client.workshop;

import dev.stillworld.Stillworld;
import dev.stillworld.workshop.TreatmentBlock;
import dev.stillworld.workshop.WorkshopContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value=Stillworld.ID,dist=Dist.CLIENT)
public final class WorkshopClient {
    public WorkshopClient(IEventBus bus) {
        bus.addListener(WorkshopClient::screens);bus.addListener(WorkshopClient::renderers);
        NeoForge.EVENT_BUS.addListener(WorkshopClient::input);
    }
    private static void screens(RegisterMenuScreensEvent event) {event.register(WorkshopContent.STABILIZER_MENU.get(),StabilizerScreen::new);}
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {event.registerBlockEntityRenderer(WorkshopContent.AUXILIARY_ENTITY.get(),BiomeMiniatureRenderer::new);}
    private static void input(MovementInputUpdateEvent event) {
        if(TreatmentBlock.isTreating(event.getEntity()))event.getInput().shiftKeyDown=false;
    }
}
