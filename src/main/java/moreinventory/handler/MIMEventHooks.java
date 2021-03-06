package moreinventory.handler;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moreinventory.core.Config;
import moreinventory.core.MoreInventoryMod;
import moreinventory.entity.EntityMIMArrow;
import moreinventory.inventory.InventoryPouch;
import moreinventory.network.ConfigSyncMessage;
import moreinventory.network.PlayerNameCacheMessage;
import moreinventory.tileentity.storagebox.TileEntityEnderStorageBox;
import moreinventory.tileentity.storagebox.addon.TileEntityTeleporter;
import moreinventory.util.MIMItemBoxList;
import moreinventory.util.MIMItemInvList;
import moreinventory.util.MIMUtils;
import moreinventory.util.Version;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.event.ClickEvent;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeVersion.Status;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.util.Random;

public class MIMEventHooks
{
	public static final MIMEventHooks instance = new MIMEventHooks();

	protected static final Random eventRand = new Random();

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onConfigChanged(OnConfigChangedEvent event)
	{
		if (event.modID.equals(MoreInventoryMod.MODID))
		{
			Config.syncConfig();
		}
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void onClientConnected(ClientConnectedToServerEvent event)
	{
		if (Version.getStatus() == Status.PENDING || Version.getStatus() == Status.FAILED)
		{
			Version.versionCheck();
		}
		else if (Version.isDev() || Config.versionNotify && Version.isOutdated())
		{
			IChatComponent component = new ChatComponentTranslation("moreinv.version.message", EnumChatFormatting.GREEN + "MoreInventoryMod" + EnumChatFormatting.RESET);
			component.appendText(" : " + EnumChatFormatting.YELLOW + Version.getLatest());
			component.getChatStyle().setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, MoreInventoryMod.metadata.url));

			FMLClientHandler.instance().getClient().ingameGUI.getChatGUI().printChatMessage(component);
		}

		event.manager.scheduleOutboundPacket(MoreInventoryMod.network.getPacketFrom(new ConfigSyncMessage(Config.isCollectTorch.contains("client"), Config.isCollectArrow.contains("client"), Config.isFullAutoCollectPouch.contains("client"), Config.leftClickCatchall.contains("client"))));
	}

	@SubscribeEvent
	public void onPlayerLoggedIn(PlayerLoggedInEvent event)
	{
		EntityPlayer player = event.player;

		MoreInventoryMod.playerNameCache.refreshOwner(player);

		if (player instanceof EntityPlayerMP)
		{
			NBTTagCompound data = new NBTTagCompound();
			MoreInventoryMod.playerNameCache.writeToNBT(data);

			MoreInventoryMod.network.sendTo(new PlayerNameCacheMessage(data), (EntityPlayerMP)player);
		}
	}

	@SubscribeEvent
	public void onLivingHurt(LivingHurtEvent event)
	{
		if (event.entityLiving instanceof EntityPlayerMP)
		{
			EntityPlayerMP player = (EntityPlayerMP)event.entityLiving;

			if (event.ammount >= 3)
			{
				for (int i = 0; i < player.inventory.getSizeInventory(); i++)
				{
					ItemStack itemstack = player.inventory.getStackInSlot(i);

					if (MIMUtils.compareItems(itemstack, MoreInventoryMod.transporter) && itemstack.getItemDamage() != 0 && itemstack != player.inventory.getCurrentItem())
					{
						if (player.getFoodStats().getFoodLevel() < 10 || i > 9)
						{
							MIMUtils.dropItem(player.worldObj, itemstack, player.posX, player.posY + 1.5D, player.posZ);

							player.inventory.setInventorySlotContents(i, null);
						}
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void onItemPickup(EntityItemPickupEvent event)
	{
		if (event.entityPlayer instanceof EntityPlayerMP)
		{
			EntityPlayerMP player = (EntityPlayerMP)event.entityPlayer;
			ItemStack item = event.item.getEntityItem();
			InventoryPlayer inventory = player.inventory;

			for (int i = 0; i < inventory.getSizeInventory(); ++i)
			{
				ItemStack itemstack = inventory.getStackInSlot(i);

				if (itemstack != null)
				{
					String uuid = player.getUniqueID().toString();

					if (itemstack.getItem() == MoreInventoryMod.pouch)
					{
						InventoryPouch pouch = new InventoryPouch(itemstack);

						if (pouch.canAutoCollect(item))
						{
							MIMUtils.mergeItemStack(item, pouch);
						}

						if (Config.isFullAutoCollectPouch.contains(uuid))
						{
							pouch.collectAllItemStack(inventory, false);
						}
					}

					if (Config.isCollectTorch.contains(uuid) && item.getItem() == Item.getItemFromBlock(Blocks.torch) && itemstack.getItem() == MoreInventoryMod.torchHolder ||
						Config.isCollectArrow.contains(uuid) && item.getItem() == Items.arrow && itemstack.getItem() == MoreInventoryMod.arrowHolder)
					{
						int damage = itemstack.getItemDamage();
						int count = item.stackSize;

						if (damage >= count)
						{
							itemstack.setItemDamage(damage - count);
							item.stackSize = 0;
						}
						else
						{
							itemstack.setItemDamage(0);
							item.stackSize -= damage;
						}
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void onArrowLoose(ArrowLooseEvent event)
	{
		EntityPlayer player = event.entityPlayer;
		ItemStack bow = event.bow;

		if (bow != null && bow.getItem() == Items.bow && player.inventory.hasItem(MoreInventoryMod.arrowHolder))
		{
			World world = player.worldObj;
			float f = (float)event.charge / 20.0F;
			f = (f * f + f * 2.0F) / 3.0F;

			if ((double)f < 0.1D)
			{
				return;
			}

			if (f > 1.0F)
			{
				f = 1.0F;
			}

			EntityArrow entity = new EntityMIMArrow(world, player, f * 2.0F);

			if (f == 1.0F)
			{
				entity.setIsCritical(true);
			}

			int i = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, bow);

			if (i > 0)
			{
				entity.setDamage(entity.getDamage() + (double) i * 0.5D + 0.5D);
			}

			i = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, bow);

			if (i > 0)
			{
				entity.setKnockbackStrength(i);
			}

			if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, bow) > 0)
			{
				entity.setFire(100);
			}

			bow.damageItem(1, player);
			world.playSoundAtEntity(player, "random.bow", 1.0F, 1.0F / (eventRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);

			if (!player.capabilities.isCreativeMode)
			{
				player.inventory.mainInventory[MIMUtils.getFirstSlot(player.inventory.mainInventory, MoreInventoryMod.arrowHolder)].damageItem(1, player);
			}

			if (!world.isRemote)
			{
				world.spawnEntityInWorld(entity);
			}

			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onArrowNock(ArrowNockEvent event)
	{
		EntityPlayer player = event.entityPlayer;
		ItemStack result = event.result;

		if (result != null && result.getItem() == Items.bow && player.inventory.hasItem(MoreInventoryMod.arrowHolder))
		{
			player.setItemInUse(result, result.getMaxItemUseDuration());
		}
	}

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event)
	{
		World world = event.world;

		if (!world.isRemote)
		{
			if (MoreInventoryMod.saveHandler == null || !MoreInventoryMod.saveHandler.world.getSaveHandler().getWorldDirectoryName().equals(world.getSaveHandler().getWorldDirectoryName()))
			{
				TileEntityEnderStorageBox.itemList = new MIMItemInvList("EnderStorageBoxInv");
				TileEntityEnderStorageBox.enderBoxList = new MIMItemBoxList("EnderStorageBox");
				TileEntityTeleporter.teleporterList = new MIMItemBoxList("Teleporter");
				MoreInventoryMod.saveHandler = new MIMWorldSaveHelper(world, "MoreInvData", TileEntityEnderStorageBox.itemList, TileEntityEnderStorageBox.enderBoxList, TileEntityTeleporter.teleporterList, MoreInventoryMod.playerNameCache);
			}
		}
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event)
	{
		World world = event.world;

		if (!world.isRemote && world.provider.dimensionId == 0)
		{
			if (MoreInventoryMod.saveHandler != null)
			{
				MoreInventoryMod.saveHandler.saveData();
			}
		}
	}
}