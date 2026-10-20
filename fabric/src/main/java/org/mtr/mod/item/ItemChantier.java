package org.mtr.mod.item;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.ItemExtension;
public final class ItemChantier extends ItemExtension {
    public ItemChantier(ItemSettings settings){super(settings.maxCount(1));}
    @Override public ActionResult useOnBlock2(ItemUsageContext c){if(c.getWorld().isClient())org.mtr.mod.client.ChantierClient.click(c.getBlockPos());return ActionResult.SUCCESS;}
    @Override public void useWithoutResult(World world,PlayerEntity user,Hand hand){if(world.isClient())org.mtr.mod.client.ChantierClient.airClick();}
}
