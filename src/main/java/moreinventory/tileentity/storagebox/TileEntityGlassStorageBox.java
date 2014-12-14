package moreinventory.tileentity.storagebox;

import moreinventory.util.MIMUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class TileEntityGlassStorageBox extends TileEntityStorageBox
{
	public TileEntityGlassStorageBox()
	{
		super(StorageBoxType.Glass);
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack itemstack)
	{
		if (itemstack != null)
		{
			getStorageBoxNetworkManager().linkedPutIn(itemstack, this, false);
			MIMUtils.dropItem(worldObj, itemstack, xCoord, yCoord, zCoord);

			if (itemstack.stackSize > getInventoryStackLimit())
			{
				itemstack.stackSize = getInventoryStackLimit();
			}
		}
		else
		{
			storageItems[slot] = null;
		}

		markDirty();
	}

	@Override
	public boolean rightClickEvent(World world, EntityPlayer player, int x, int y, int z)
	{
		switch (++clickCount)
		{
			case 1:
				clickTime = 16;

				return false;
			case 2:
				return false;
			case 3:
				clickCount = 0;

				getStorageBoxNetworkManager().linkedCollect(player.inventory);
				updatePlayerInventory(player);
				break;
			default:
				clickCount = 0;
				break;
		}

		return true;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack itemstack)
	{
		return getStorageBoxNetworkManager().canLinkedImport(itemstack, this);
	}

	@Override
	public void sendPacket() {}

	@Override
	public void sendContents() {}
}