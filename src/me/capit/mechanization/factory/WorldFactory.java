package me.capit.mechanization.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import me.capit.mechanization.Mechanization;
import me.capit.mechanization.Position3;
import me.capit.mechanization.exception.MechaException;
import me.capit.mechanization.recipe.MechaFactoryRecipe;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldFactory {
	public static boolean takeFurnaceFuel(Furnace f){
		return takeFurnaceFuel(f,1);
	}
	
	public static boolean takeFurnaceFuel(Furnace f, int fuel){
		FurnaceInventory inv = f.getInventory();
		if (inv.getFuel().getAmount()>fuel){
			ItemStack is = inv.getFuel();
			is.setAmount(is.getAmount()-fuel);
			return true;
		} else if (inv.getFuel().getAmount()==fuel){
			inv.setFuel(new ItemStack(Material.AIR));
			return true;
		}
		return false;
	}
	
	private final MechaFactory factory;
	private final Position3 origin;
	private final World world;
	private final Chest chest;
	private volatile boolean running = false;
	
	public static WorldFactory getFactoryAtChest(ItemStack activator, Chest chest){
		for (String f : Mechanization.factories.keySet()){
			try {
				return new WorldFactory(f,activator,chest);
			} catch (MechaException e){
				// Do nothing.
			}
		}
		return null;
	}
	
	public WorldFactory(String factory, ItemStack activator, Chest chest) throws MechaException {
		MechaFactory fac = Mechanization.factories.get(factory);
		try {
			if (!fac.validActivator(activator)) throw null;
			this.factory = fac;
			this.chest = chest;
			origin = new Position3(chest.getX(),chest.getY(),chest.getZ())
				.minus(fac.getMatrix().getChestLocation().times(getRelativity()));
			world = chest.getWorld();
			if (!valid()) throw null;
		} catch (NullPointerException e){
			throw new MechaException(e.getMessage());
		}
	}

	public void activate(){
		new BukkitRunnable(){
			@Override
			public void run() {
				MechaFactoryRecipe rec = getInputRecipe();
				if (rec==null || running) return;
				int fuelOffset = 0;
				running = true;
				while (rec!=null && valid() && validFurnacesForRecipe(rec, fuelOffset) && rec.getFuel()>fuelOffset){
					try {
						for (Furnace f : getFurnaces()){
							ItemStack fuelIS = f.getInventory().getFuel();
							if (fuelIS.getAmount()>1){
								fuelIS.setAmount(fuelIS.getAmount()-1);
							} else {
								fuelIS.setType(Material.AIR);
							}
							f.getInventory().setFuel(fuelIS);
							f.setBurnTime((short) (factory.getTimePerFuel()*20));
							f.update();
						}
						TimeUnit.SECONDS.sleep(factory.getTimePerFuel());
					} catch (InterruptedException e) {
						e.printStackTrace();
						break;
					}
					rec = getInputRecipe();
					fuelOffset++;
				}
				if (rec!=null && valid() && rec.getFuel()==fuelOffset) rec.setInventoryToOutput(chest.getBlockInventory());
				running = false;
			}
		}.runTaskLaterAsynchronously(Mechanization.plugin, 1L);
	}
	
	public void lightFurnaces(){
		for (Furnace f : getFurnaces()){
			f.getBlock().setType(Material.BURNING_FURNACE);
			f.update();
		}
	}
	
	public void extinguishFurnaces(){
		for (Furnace f : getFurnaces()){
			f.getBlock().setType(Material.FURNACE);
			f.update();
		}
	}
	
	public MechaFactoryRecipe getInputRecipe(){
		return factory.getRecipeFromInput(chest.getBlockInventory());
	}
	
	public List<Furnace> getFurnaces(){
		List<Furnace> furnaces = new ArrayList<Furnace>();
		for (Position3 loc : factory.getFurnaceLocations()){
			Position3 fur = origin.plus(loc.times(getRelativity()));
			Block b = world.getBlockAt((int) fur.getX(),(int) fur.getY(),(int) fur.getZ());
			if (b.getType()!=Material.FURNACE){
				Bukkit.getServer().getLogger().info("Failed to get furnace at "+b.getX()+","+b.getY()+","+b.getZ()+" from "+b.getType());
				Bukkit.getServer().getLogger().info("Furnace index "+loc.getX()+","+loc.getY()+","+loc.getZ()+" missing.");
				break;
			}
			Furnace f = (Furnace) b.getState();
			furnaces.add(f);
		}
		return furnaces;
	}
	
	public boolean validFurnacesForRecipe(MechaFactoryRecipe recipe, int fuelOffset){
		if (getFurnaces().size()==0) return false;
		for (Furnace fur : getFurnaces()){
			FurnaceInventory inv = fur.getInventory();
			if (!(inv.getSmelting()==null || inv.getSmelting().getType()==Material.AIR)) return false; 
			ItemStack fuelIS = fur.getInventory().getFuel();
			if (fuelIS==null || fuelIS.getType()!=Material.COAL || fuelIS.getAmount()<recipe.getFuel()-fuelOffset) return false;
		}
		return true;
	}
	
	public boolean validFurnacesForRecipe(MechaFactoryRecipe recipe){
		return validFurnacesForRecipe(recipe,0);
	}
	
	public Location getOriginLocation(){
		return new Location(world,origin.getX(),origin.getY(),origin.getZ());
	}
	
	public boolean valid(){
		return factory!=null && factory.validForLocation(getOriginLocation(), getRelativity());
	}
	
	public MechaFactory getFactory(){
		return factory;
	}
	
	public boolean isRunning(){
		return running;
	}
	
	public Position3 getRelativity(){
		Position3 rel = new Position3(0,1,0);
		MaterialData cdata = chest.getData();
		switch(((org.bukkit.material.Chest) cdata).getFacing()){
		case NORTH: case EAST:
			rel.setX(-1);
			rel.setZ(1);
			break;
		case SOUTH: case WEST:
			rel.setX(1);
			rel.setZ(-1);
			break;
		default:
			break;
		}
		return rel;
	}

}
