package de.srendi.advancedperipherals.common.events;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import de.srendi.advancedperipherals.AdvancedPeripherals;
import de.srendi.advancedperipherals.common.addons.computercraft.turtles.TurtleChatBox;
import de.srendi.advancedperipherals.common.blocks.base.TileEntityList;
import de.srendi.advancedperipherals.common.blocks.tileentity.ChatBoxTileEntity;
import de.srendi.advancedperipherals.common.configuration.AdvancedPeripheralsConfig;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdvancedPeripherals.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Events {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerWorld world = event.getPlayer().getServerWorld();
        TileEntityList.get(world).getTileEntities(world).forEach(tileEntity -> { //Events for computers
            if (tileEntity instanceof ChatBoxTileEntity) {
                if (AdvancedPeripheralsConfig.enableChatBox) {
                    if (event.getMessage().startsWith("$")) {
                        event.setCanceled(true);
                        ChatBoxTileEntity entity = (ChatBoxTileEntity) tileEntity;
                        for (IComputerAccess computer : entity.getConnectedComputers()) {
                            computer.queueEvent("chat", event.getUsername(), event.getMessage().replace("$", ""));
                        }
                        return;
                    }
                    ChatBoxTileEntity entity = (ChatBoxTileEntity) tileEntity;
                    for (IComputerAccess computer : entity.getConnectedComputers()) {
                        computer.queueEvent("chat", event.getUsername(), event.getMessage());
                    }
                }
            } else if(tileEntity instanceof TileTurtle) { //Events for turtles
                TileTurtle tileTurtle = (TileTurtle) tileEntity;
                if(tileTurtle.getUpgrade(TurtleSide.RIGHT) instanceof TurtleChatBox || tileTurtle.getUpgrade(TurtleSide.LEFT) instanceof TurtleChatBox) {
                    if (event.getMessage().startsWith("$")) {
                        event.setCanceled(true);
                        tileTurtle.getServerComputer().queueEvent("chat", new Object[]{event.getUsername(), event.getMessage().replace("$", "")});
                        return;
                    }
                    tileTurtle.getServerComputer().queueEvent("chat", new Object[]{event.getUsername(), event.getMessage()});
                }
            }
        });
    }
}