package de.srendi.advancedperipherals.common.addons.computercraft.peripheral;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import de.srendi.advancedperipherals.common.addons.appliedenergistics.AppEngApi;
import de.srendi.advancedperipherals.common.addons.appliedenergistics.CraftJob;
import de.srendi.advancedperipherals.common.configuration.AdvancedPeripheralsConfig;
import de.srendi.advancedperipherals.common.util.ServerWorker;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MeBridgePeripheral implements IPeripheral {

    private final List<IComputerAccess> connectedComputers = new ArrayList<>();
    private String type;
    private IGridNode node;
    private IActionSource source;
    private TileEntity tileEntity;

    public MeBridgePeripheral(String type, IGridNode node, IActionSource source, TileEntity tileEntity) {
        this.type = type;
        this.node = node;
        this.source = source;
        this.tileEntity = tileEntity;
    }

    public void setNode(IGridNode node) {
        this.node = node;
    }

    @NotNull
    @Override
    public String getType() {
        return type;
    }

    public List<IComputerAccess> getConnectedComputers() {
        return connectedComputers;
    }

    @Override
    public void attach(@NotNull IComputerAccess computer) {
        connectedComputers.add(computer);
    }

    @Override
    public void detach(@NotNull IComputerAccess computer) {
        connectedComputers.remove(computer);
    }

    @Override
    public boolean equals(@Nullable IPeripheral iPeripheral) {
        return iPeripheral == this;
    }

    @LuaFunction(mainThread = false)
    public final MethodResult craftItem(IComputerAccess computer, String item, double itemAmount) {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            String itemName = item;
            int amount = (int) itemAmount;
            ItemStack itemToCraft = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName)));
            if (itemToCraft.isEmpty())
                throw new NullPointerException("Item " + itemName + " does not exists");
            CraftJob job = new CraftJob(tileEntity.getWorld(), computer, node, item, Optional.of(amount), source);
            ServerWorker.add(job::startCrafting);
            return MethodResult.pullEvent("crafting", job);
        } else {
            return MethodResult.of("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final double getEnergyUsage() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            return ((IEnergyGrid) node.getGrid().getCache(IEnergyGrid.class)).getAvgPowerUsage();
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final double getEnergyStorage() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            return ((IEnergyGrid) node.getGrid().getCache(IEnergyGrid.class)).getStoredPower();
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final double getMaxEnergyStorage() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            return ((IEnergyGrid) node.getGrid().getCache(IEnergyGrid.class)).getMaxStoredPower();
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final boolean isItemCrafting(String item) throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            IMEMonitor<IAEItemStack> monitor = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            ICraftingGrid grid = node.getGrid().getCache(ICraftingGrid.class);
            return grid.isRequesting(AppEngApi.getInstance().findAEStackFromItemStack(monitor, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)))));
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }


    @LuaFunction(mainThread = true)
    public final int exportItem(String item, int count, String directionString) throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            IMEMonitor<IAEItemStack> monitor = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)));
            stack.setCount(count);
            IAEItemStack aeStack = AppEngApi.getInstance().findAEStackFromItemStack(monitor, stack);
            if (aeStack == null)
                throw new LuaException("Item " + item + " does not exists in the ME system or the system is offline");
            Direction direction = Direction.valueOf(directionString.toUpperCase(Locale.ROOT));

            TileEntity targetEntity = tileEntity.getWorld().getTileEntity(tileEntity.getPos().offset(direction));
            IItemHandler inventory = targetEntity != null ? targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite()).resolve().orElse(null) : null;
            if (inventory == null)
                throw new LuaException("No valid inventory at " + direction);

            aeStack.setStackSize(count);
            IAEItemStack extracted = monitor.extractItems(aeStack, Actionable.SIMULATE, source);
            if (extracted == null)
                throw new LuaException("Item " + item + " does not exists in the ME system or the system is offline");

            int transferableAmount = (int) extracted.getStackSize();

            ItemStack remaining = ItemHandlerHelper.insertItemStacked(inventory, extracted.createItemStack(), true);
            if (!remaining.isEmpty()) {
                transferableAmount -= remaining.getCount();
            }

            aeStack.setStackSize(transferableAmount);
            extracted = monitor.extractItems(aeStack, Actionable.MODULATE, source);
            remaining = ItemHandlerHelper.insertItemStacked(inventory, extracted.createItemStack(), false);

            if (!remaining.isEmpty()) {
                aeStack.setStackSize(remaining.getCount());
                monitor.injectItems(aeStack, Actionable.MODULATE, source);
            }
            return transferableAmount;
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final int importItem(String item, int count, String directionString) throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            IMEMonitor<IAEItemStack> monitor = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)));
            if (count > 64 || count < 0)
                throw new LuaException("Count need to be in the range of 1-64");
            stack.setCount(count);
            IAEItemStack aeStack = AppEngApi.getInstance().findAEStackFromItemStack(monitor, stack);
            if (aeStack == null)
                throw new LuaException("Item " + item + " does not exists in the ME system or the system is offline");
            Direction direction = Direction.valueOf(directionString.toUpperCase(Locale.ROOT));

            TileEntity targetEntity = tileEntity.getWorld().getTileEntity(tileEntity.getPos().offset(direction));
            IItemHandler inventory = targetEntity != null ? targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction.getOpposite()).resolve().orElse(null) : null;
            if (inventory == null)
                throw new LuaException("No valid inventory at " + direction);

            aeStack.setStackSize(count);

            int amount = count;

            int transferableAmount = 0;

            for (int i = 0; i < inventory.getSlots(); i++) {
                if (inventory.getStackInSlot(i).isItemEqual(stack)) {
                    if (inventory.getStackInSlot(i).getCount() >= amount) {
                        transferableAmount += amount;
                        aeStack.setStackSize(amount);
                        monitor.injectItems(aeStack, Actionable.MODULATE, source);
                        inventory.extractItem(i, amount, false);
                        break;
                    } else {
                        amount = count - inventory.getStackInSlot(i).getCount();
                        transferableAmount += inventory.getStackInSlot(i).getCount();
                        aeStack.setStackSize(inventory.getStackInSlot(i).getCount());
                        monitor.injectItems(aeStack, Actionable.MODULATE, source);
                        inventory.extractItem(i, inventory.getStackInSlot(i).getCount(), false);
                    }
                }
            }

            return transferableAmount;
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final int exportItemToChest(IComputerAccess computer, String item, int count, String chestName) throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            IMEMonitor<IAEItemStack> monitor = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)));
            stack.setCount(count);
            IAEItemStack aeStack = AppEngApi.getInstance().findAEStackFromItemStack(monitor, stack);
            if (aeStack == null)
                throw new LuaException("Item " + item + " does not exists in the ME system or the system is offline");

            IPeripheral chest = computer.getAvailablePeripheral(chestName);
            if (chest == null)
                throw new LuaException("No valid chest for " + chestName);

            TileEntity targetEntity = (TileEntity) chest.getTarget();
            IItemHandler inventory = targetEntity != null ? targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).resolve().orElse(null) : null;
            if (inventory == null)
                throw new LuaException("No valid inventory for " + chestName);

            aeStack.setStackSize(count);
            IAEItemStack extracted = monitor.extractItems(aeStack, Actionable.SIMULATE, source);
            if (extracted == null)
                throw new LuaException("Item " + item + " does not exists in the ME system or the system is offline");

            int transferableAmount = (int) extracted.getStackSize();

            ItemStack remaining = ItemHandlerHelper.insertItemStacked(inventory, extracted.createItemStack(), true);
            if (!remaining.isEmpty()) {
                transferableAmount -= remaining.getCount();
            }

            aeStack.setStackSize(transferableAmount);
            extracted = monitor.extractItems(aeStack, Actionable.MODULATE, source);
            remaining = ItemHandlerHelper.insertItemStacked(inventory, extracted.createItemStack(), false);

            if (!remaining.isEmpty()) {
                aeStack.setStackSize(remaining.getCount());
                monitor.injectItems(aeStack, Actionable.MODULATE, source);
            }
            return transferableAmount;
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }


    @LuaFunction(mainThread = true)
    public final int importItemFromChest(IComputerAccess computer, String item, int count, String chestName) throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            IMEMonitor<IAEItemStack> monitor = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(item)));
            if (count > 64 || count < 0)
                throw new LuaException("Count need to be in the range of 1-64");
            stack.setCount(count);
            IAEItemStack aeStack = AppEngApi.getInstance().findAEStackFromItemStack(monitor, stack);
            if (aeStack == null)
                throw new LuaException("Item " + item + " does not exists in the ME system or the system is offline");

            IPeripheral chest = computer.getAvailablePeripheral(chestName);
            if (chest == null)
                throw new LuaException("No valid chest for " + chestName);

            TileEntity targetEntity = (TileEntity) chest.getTarget();
            IItemHandler inventory = targetEntity != null ? targetEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).resolve().orElse(null) : null;
            if (inventory == null)
                throw new LuaException("No valid inventory for " + chestName);

            aeStack.setStackSize(count);

            int amount = count;

            int transferableAmount = 0;

            for (int i = 0; i < inventory.getSlots(); i++) {
                if (inventory.getStackInSlot(i).isItemEqual(stack)) {
                    if (inventory.getStackInSlot(i).getCount() >= amount) {
                        transferableAmount += amount;
                        aeStack.setStackSize(amount);
                        monitor.injectItems(aeStack, Actionable.MODULATE, source);
                        inventory.extractItem(i, amount, false);
                        break;
                    } else {
                        amount = count - inventory.getStackInSlot(i).getCount();
                        transferableAmount += inventory.getStackInSlot(i).getCount();
                        aeStack.setStackSize(inventory.getStackInSlot(i).getCount());
                        monitor.injectItems(aeStack, Actionable.MODULATE, source);
                        inventory.extractItem(i, inventory.getStackInSlot(i).getCount(), false);
                    }
                }
            }

            return transferableAmount;
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final Object[] listItems() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            IMEMonitor<IAEItemStack> inventory = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            return new Object[]{AppEngApi.INSTANCE.iteratorToMapStack(inventory.getStorageList().iterator(), 0)};
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final Object[] listCraftableItems() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {

            IMEMonitor<IAEItemStack> inventory = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IItemStorageChannel.class));
            return new Object[]{AppEngApi.INSTANCE.iteratorToMapStack(inventory.getStorageList().iterator(), 2)};
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final Object[] listFluid() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {

            IMEMonitor<IAEFluidStack> inventory = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IFluidStorageChannel.class));
            return new Object[]{AppEngApi.INSTANCE.iteratorToMapFluid(inventory.getStorageList().iterator(), 0)};
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final Object[] listCraftableFluid() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {

            IMEMonitor<IAEFluidStack> inventory = ((IStorageGrid) node.getGrid().getCache(IStorageGrid.class)).getInventory(AppEngApi.getInstance().getApi().storage().getStorageChannel(IFluidStorageChannel.class));
            return new Object[]{AppEngApi.INSTANCE.iteratorToMapFluid(inventory.getStorageList().iterator(), 2)};
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }

    @LuaFunction(mainThread = true)
    public final Object[] getCraftingCPUs() throws LuaException {
        if (AdvancedPeripheralsConfig.enableMeBridge) {
            ICraftingGrid grid = node.getGrid().getCache(ICraftingGrid.class);
            if (grid == null)
                throw new LuaException("Not connected");
            Map<Integer, Object> map = new HashMap<>();
            Iterator<ICraftingCPU> iterator = grid.getCpus().iterator();
            if (!iterator.hasNext())
                throw new LuaException("The system has no crafting cpus");
            int i = 1;
            while (iterator.hasNext()) {
                Object o = AppEngApi.INSTANCE.getObjectFromCPU(iterator.next());
                if (o != null)
                    map.put(i++, o);
            }
            return new Object[]{map};
        } else {
            throw new LuaException("Me Bridge is not enabled, enable it in the config.");
        }
    }
}