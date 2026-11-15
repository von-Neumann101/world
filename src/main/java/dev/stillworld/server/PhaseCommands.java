package dev.stillworld.server;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.stillworld.ModContent;
import dev.stillworld.core.Rank;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.Arrays;

public final class PhaseCommands {
    public static void register(){NeoForge.EVENT_BUS.addListener(PhaseCommands::commands);}
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stillworld")
            .then(Commands.literal("toggle").executes(c->PhaseManager.toggle(c.getSource().getPlayerOrException())?1:0))
            .then(Commands.literal("exit").executes(c->PhaseManager.requestExit(c.getSource().getPlayerOrException())?1:0))
            .then(Commands.literal("status").executes(c->{var p=c.getSource().getPlayerOrException();var s=PhaseManager.get(p);var d=PsycheManager.data(p);c.getSource().sendSuccess(()->Component.literal((s==null?"Reality":"Stillworld rank"+s.rank.label())+" | psyche "+d.energy+"/100 | switch "+((PsycheManager.cooldown(p)+19)/20)+"s | max HP "+p.getMaxHealth()),false);return 1;}))
            .then(Commands.literal("psyche").requires(s->s.hasPermission(2))
                .then(Commands.literal("set").then(Commands.argument("value",IntegerArgumentType.integer(0,100))
                    .executes(c->{PsycheManager.setEnergy(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"value"));return 1;})
                    .then(Commands.argument("player",EntityArgument.player()).executes(c->{PsycheManager.setEnergy(EntityArgument.getPlayer(c,"player"),IntegerArgumentType.getInteger(c,"value"));return 1;})))))
            .then(Commands.literal("cooldown").requires(s->s.hasPermission(2)).then(Commands.literal("reset").executes(c->{var p=c.getSource().getPlayerOrException();PsycheManager.data(p).cooldownTicks=0;PsycheManager.sync(p);return 1;})))
            .then(Commands.literal("kit").requires(s->s.hasPermission(2)).executes(c->{var p=c.getSource().getPlayerOrException();for(var charm:ModContent.CHARMS.values())p.getInventory().placeItemBackInInventory(new ItemStack(charm.get()));p.getInventory().placeItemBackInInventory(new ItemStack(ModContent.BLADE.get()));return 1;}))
            .then(Commands.literal("rank").requires(s->s.hasPermission(2)).then(Commands.argument("rank",StringArgumentType.greedyString())
                .suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(Rank.values()).map(Rank::commandName),b))
                .executes(c->{var p=c.getSource().getPlayerOrException();String name=StringArgumentType.getString(c,"rank");var rank=Rank.fromCommand(name);if(rank.isEmpty())return 0;
                    var inv=CuriosApi.getCuriosInventory(p);if(inv.isEmpty())return 0;var slot=inv.get().getStacksHandler("stillworld");if(slot.isEmpty())return 0;
                    PhaseManager.exitWithCost(p);var old=slot.get().getStacks().getStackInSlot(0);if(!old.isEmpty())p.getInventory().placeItemBackInInventory(old.copy());slot.get().getStacks().setStackInSlot(0,new ItemStack(ModContent.CHARMS.get(rank.get()).get()));return 1;})))
            .then(Commands.literal("force_exit").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).executes(c->{PhaseManager.exit(EntityArgument.getPlayer(c,"player"));return 1;}))));
    }
    private PhaseCommands(){}
}
