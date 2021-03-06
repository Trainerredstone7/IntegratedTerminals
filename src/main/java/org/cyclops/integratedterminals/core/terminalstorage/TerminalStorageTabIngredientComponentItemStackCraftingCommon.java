package org.cyclops.integratedterminals.core.terminalstorage;

import com.google.common.collect.Lists;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.tuple.Triple;
import org.cyclops.commoncapabilities.api.ingredient.IngredientComponent;
import org.cyclops.cyclopscore.helper.CraftingHelpers;
import org.cyclops.cyclopscore.persist.IDirtyMarkListener;
import org.cyclops.integratedterminals.IntegratedTerminals;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabCommon;
import org.cyclops.integratedterminals.core.terminalstorage.button.TerminalButtonItemStackCraftingGridAutoRefill;
import org.cyclops.integratedterminals.inventory.InventoryCraftingDirtyable;
import org.cyclops.integratedterminals.inventory.SlotCraftingAutoRefill;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorage;
import org.cyclops.integratedterminals.network.packet.TerminalStorageIngredientItemStackCraftingGridSetResult;
import org.cyclops.integratedterminals.part.PartTypeTerminalStorage;

import java.util.List;

/**
 * A common-side storage terminal ingredient tab for crafting with {@link ItemStack} instances.
 * @author rubensworks
 */
public class TerminalStorageTabIngredientComponentItemStackCraftingCommon
        extends TerminalStorageTabIngredientComponentCommon<ItemStack, Integer> {

    private InventoryCrafting inventoryCrafting;
    private InventoryCraftResult inventoryCraftResult;
    private SlotCrafting slotCrafting;
    private List<Slot> slots;
    private TerminalButtonItemStackCraftingGridAutoRefill.AutoRefillType autoRefill = TerminalButtonItemStackCraftingGridAutoRefill.AutoRefillType.STORAGE;

    public TerminalStorageTabIngredientComponentItemStackCraftingCommon(ContainerTerminalStorage containerTerminalStorage,
                                                                        ResourceLocation name,
                                                                        IngredientComponent<ItemStack, Integer> ingredientComponent) {
        super(containerTerminalStorage, name, ingredientComponent);
    }

    public static int getCraftingResultSlotIndex(Container container, ResourceLocation name) {
        ITerminalStorageTabCommon tabCommon = ((ContainerTerminalStorage) container).getTabCommon(name.toString());
        TerminalStorageTabIngredientComponentItemStackCraftingCommon tabCommonCrafting =
                (TerminalStorageTabIngredientComponentItemStackCraftingCommon) tabCommon;
        return tabCommonCrafting.getSlotCrafting().slotNumber;
    }

    @Override
    public List<Slot> loadSlots(Container container, int startIndex, EntityPlayer player,
                                PartTypeTerminalStorage.State partState) {
        slots = Lists.newArrayListWithCapacity(10);

        // Reload the recipe when the input slots are updated
        IDirtyMarkListener dirtyListener = () -> updateCraftingResult(player, container, partState);

        this.inventoryCraftResult = new InventoryCraftResult() {
            @Override
            public void markDirty() {
                dirtyListener.onDirty();
                super.markDirty();
            }
        };
        this.inventoryCrafting = new InventoryCraftingDirtyable(container, 3, 3, dirtyListener);

        slots.add(slotCrafting = new SlotCraftingAutoRefill(player, this.inventoryCrafting, this.inventoryCraftResult,
                0, 115, 76, this, (TerminalStorageTabIngredientComponentServer<ItemStack, Integer>)
                ((ContainerTerminalStorage) container).getTabServer(getName().toString()),
                (ContainerTerminalStorage) container));
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 3; ++j) {
                slots.add(new Slot(this.inventoryCrafting, j + i * 3, 31 + j * 18, 58 + i * 18));
            }
        }

        // Load the items that were stored in the part state into the crafting grid slots
        NonNullList<ItemStack> tabItems = partState.getNamedInventory(this.getName().toString());
        if (tabItems != null) {
            int i = 0;
            for (ItemStack tabItem : tabItems) {
                if (i == 0) {
                    this.inventoryCraftResult.setInventorySlotContents(i++, tabItem);
                } else {
                    this.inventoryCrafting.setInventorySlotContents(i++ - 1, tabItem);
                }
            }
        }

        List<Slot> returnSlots = Lists.newArrayList(slots);
        for (Triple<Slot, Integer, Integer> slot : ((ContainerTerminalStorage) container).getTabSlots(ingredientComponent.getName().toString())) {
            returnSlots.add(slot.getLeft());
        }
        return returnSlots;
    }

    public InventoryCrafting getInventoryCrafting() {
        return inventoryCrafting;
    }

    public InventoryCraftResult getInventoryCraftResult() {
        return inventoryCraftResult;
    }

    public SlotCrafting getSlotCrafting() {
        return slotCrafting;
    }

    public TerminalButtonItemStackCraftingGridAutoRefill.AutoRefillType getAutoRefill() {
        return autoRefill;
    }

    public void setAutoRefill(TerminalButtonItemStackCraftingGridAutoRefill.AutoRefillType autoRefill) {
        this.autoRefill = autoRefill;
    }

    public void updateCraftingResult(EntityPlayer player, Container container, PartTypeTerminalStorage.State partState) {
        if (!player.world.isRemote) {
            EntityPlayerMP entityplayermp = (EntityPlayerMP)player;
            ItemStack itemstack = ItemStack.EMPTY;
            IRecipe recipe = CraftingHelpers.findMatchingRecipeCached(inventoryCrafting, player.world, false);

            if (recipe != null && (recipe.isDynamic()
                    || !player.world.getGameRules().getBoolean("doLimitedCrafting")
                    || entityplayermp.getRecipeBook().isUnlocked(recipe))) {
                inventoryCraftResult.setRecipeUsed(recipe);
                itemstack = recipe.getCraftingResult(inventoryCrafting);
            }

            inventoryCraftResult.setInventorySlotContents(0, itemstack);
            IntegratedTerminals._instance.getPacketHandler().sendToPlayer(
                    new TerminalStorageIngredientItemStackCraftingGridSetResult(getName().toString(), itemstack),
                    (EntityPlayerMP) player);
        }

        // Save changes into the part state
        NonNullList<ItemStack> latestItems = NonNullList.create();
        for (Slot slot : slots) {
            latestItems.add(slot.getStack());
        }
        partState.setNamedInventory(this.getName().toString(), latestItems);
    }
}
