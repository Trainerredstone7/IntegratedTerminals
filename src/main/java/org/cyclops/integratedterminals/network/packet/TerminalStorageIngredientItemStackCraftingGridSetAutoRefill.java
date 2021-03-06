package org.cyclops.integratedterminals.network.packet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.network.CodecField;
import org.cyclops.cyclopscore.network.PacketCodec;
import org.cyclops.integratedterminals.api.terminalstorage.ITerminalStorageTabCommon;
import org.cyclops.integratedterminals.core.terminalstorage.TerminalStorageTabIngredientComponentItemStackCraftingCommon;
import org.cyclops.integratedterminals.core.terminalstorage.button.TerminalButtonItemStackCraftingGridAutoRefill;
import org.cyclops.integratedterminals.inventory.container.ContainerTerminalStorage;

/**
 * Packet for telling the server the new autoRefill value.
 * @author rubensworks
 *
 */
public class TerminalStorageIngredientItemStackCraftingGridSetAutoRefill extends PacketCodec {

    @CodecField
    private String tabId;
    @CodecField
    private int autoRefillType;

    public TerminalStorageIngredientItemStackCraftingGridSetAutoRefill() {

    }

    public TerminalStorageIngredientItemStackCraftingGridSetAutoRefill(String tabId,
                                                                       TerminalButtonItemStackCraftingGridAutoRefill.AutoRefillType autoRefill) {
        this.tabId = tabId;
        this.autoRefillType = autoRefill.ordinal();
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void actionClient(World world, EntityPlayer player) {

    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {
        if(player.openContainer instanceof ContainerTerminalStorage) {
            ContainerTerminalStorage container = ((ContainerTerminalStorage) player.openContainer);
            ITerminalStorageTabCommon tabCommon = container.getTabCommon(tabId);
            if (tabCommon instanceof TerminalStorageTabIngredientComponentItemStackCraftingCommon) {
                ((TerminalStorageTabIngredientComponentItemStackCraftingCommon) tabCommon)
                        .setAutoRefill(TerminalButtonItemStackCraftingGridAutoRefill.AutoRefillType.values()[this.autoRefillType]);
            }
        }
    }

}