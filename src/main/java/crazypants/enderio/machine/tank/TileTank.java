package crazypants.enderio.machine.tank;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import crazypants.enderio.machine.AbstractMachineEntity;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.SlotDefinition;
import crazypants.enderio.network.PacketHandler;
import crazypants.util.BlockCoord;
import crazypants.util.FluidUtil;
import crazypants.util.ITankAccess;
import crazypants.util.ItemUtil;

public class TileTank extends AbstractMachineEntity implements IFluidHandler, ITankAccess {

  private static int IO_MB_TICK = 100;

  protected FluidTankEio tank;// = new FluidTankEio(16000);
  protected int lastUpdateLevel = -1;
  
  private boolean tankDirty = false;

  public TileTank(int meta) {
    super(new SlotDefinition(0, 1, 2, 3, -1, -1));
    if(meta == 1) {
      tank = new FluidTankEio(32000);
    } else {
      tank = new FluidTankEio(16000);
    }
  }

  public TileTank() {
    this(0);
  }

  @Override
  protected boolean doPush(ForgeDirection dir) {

    if(isSideDisabled(dir.ordinal())) {
      return false;
    }

    boolean res = super.doPush(dir);
    if(tank.getFluidAmount() > 0) {

      BlockCoord loc = getLocation().getLocation(dir);
      IFluidHandler target = FluidUtil.getFluidHandler(worldObj, loc);
      if(target != null) {
        if(target.canFill(dir.getOpposite(), tank.getFluid().getFluid())) {
          FluidStack push = tank.getFluid().copy();
          push.amount = Math.min(push.amount, IO_MB_TICK);
          int filled = target.fill(dir.getOpposite(), push, true);
          if(filled > 0) {
            tank.drain(filled, true);
            tankDirty = true;
            return res;
          }
        }
      }

    }
    return res;
  }

  @Override
  protected boolean doPull(ForgeDirection dir) {

    if(isSideDisabled(dir.ordinal())) {
      return false;
    }

    boolean res = super.doPull(dir);
    if(tank.getFluidAmount() < tank.getCapacity()) {
      BlockCoord loc = getLocation().getLocation(dir);
      IFluidHandler target = FluidUtil.getFluidHandler(worldObj, loc);
      if(target != null) {

        if(tank.getFluidAmount() > 0) {
          FluidStack canPull = tank.getFluid().copy();
          canPull.amount = tank.getCapacity() - tank.getFluidAmount();
          canPull.amount = Math.min(canPull.amount, IO_MB_TICK);
          FluidStack drained = target.drain(dir.getOpposite(), canPull, true);
          if(drained != null && drained.amount > 0) {
            tank.fill(drained, true);
            tankDirty = true;
            return res;
          }
        } else {

          FluidTankInfo[] infos = target.getTankInfo(dir.getOpposite());
          if(infos != null) {
            for (FluidTankInfo info : infos) {
              if(info.fluid != null && info.fluid.amount > 0) {
                if(canFill(dir, info.fluid.getFluid())) {
                  FluidStack canPull = info.fluid.copy();
                  canPull.amount = Math.min(IO_MB_TICK, canPull.amount);
                  FluidStack drained = target.drain(dir.getOpposite(), canPull, true);
                  if(drained != null && drained.amount > 0) {
                    tank.fill(drained, true);
                    tankDirty = true;
                    return res;
                  }
                }
              }
            }
          }
        }

      }
    }
    return res;
  }

  @Override
  public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
    if(!canFill(from)) {
      return 0;
    }
    return fillInternal(resource, doFill);
  }

  int fillInternal(FluidStack resource, boolean doFill) {
    int res = tank.fill(resource, doFill);
    if(res > 0 && doFill) {
      tankDirty = true;
    }
    return res;
  }

  @Override
  public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
    if(!canDrain(from)) {
      return null;
    }
    return drainInternal(resource, doDrain);
  }

  FluidStack drainInternal(FluidStack resource, boolean doDrain) {
    FluidStack res = tank.drain(resource, doDrain);
    if(res != null && res.amount > 0 && doDrain) {
      tankDirty = true;
    }
    return res;
  }

  @Override
  public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
    if(!canDrain(from)) {
      return null;
    }
    return drainInternal(maxDrain, doDrain);
  }

  FluidStack drainInternal(int maxDrain, boolean doDrain) {
    FluidStack res = tank.drain(maxDrain, doDrain);
    if(res != null && res.amount > 0 && doDrain) {
      tankDirty = true;
    }
    return res;
  }

  @Override
  public boolean canFill(ForgeDirection from, Fluid fluid) {
    return canFill(from) && fluid != null && (tank.getFluidAmount() > 0 && tank.getFluid().fluidID == fluid.getID() || tank.getFluidAmount() == 0);
  }

  private boolean canFill(ForgeDirection from) {
    IoMode mode = getIoMode(from);
    return mode != IoMode.PUSH && mode != IoMode.DISABLED;
  }

  @Override
  public boolean canDrain(ForgeDirection from, Fluid fluid) {
    return canDrain(from) && tank.canDrainFluidType(fluid);
  }

  private boolean canDrain(ForgeDirection from) {
    IoMode mode = getIoMode(from);
    return mode != IoMode.PULL && mode != IoMode.DISABLED;
  }
  
  @Override
  public FluidTankInfo[] getTankInfo(ForgeDirection from) {
    return new FluidTankInfo[] { new FluidTankInfo(tank) };
  }

  private int getFilledLevel() {
    int level = (int) Math.floor(16 * tank.getFilledRatio());
    if(level == 0 && tank.getFluidAmount() > 0) {
      level = 1;
    }
    return level;
  }

  @Override
  public String getMachineName() {
    return "tank";
  }

  @Override
  protected boolean isMachineItemValidForSlot(int i, ItemStack item) {
    if(i == 0) {
      FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(item);
      if(fluid != null) {
        return true;
      }
      if(item.getItem() == Items.water_bucket) {
        return true;
      }
      if(item.getItem() == Items.lava_bucket) {
        return true;
      }
      return false;
    } else if(i == 1) {
      return FluidContainerRegistry.isEmptyContainer(item) || item.getItem() == Items.bucket;
    }
    return false;
  }

  @Override
  public boolean isActive() {
    return false;
  }

  @Override
  public float getProgress() {
    return 0;
  }

  @Override
  protected boolean processTasks(boolean redstoneCheckPassed) {
    boolean res = processItems(redstoneCheckPassed);
    int filledLevel = getFilledLevel();
    if(lastUpdateLevel != filledLevel) {
      lastUpdateLevel = filledLevel;
      tankDirty = false;
      return true;
    }
    if(tankDirty && shouldDoWorkThisTick(10)) {
      PacketHandler.sendToAllAround(new PacketTank(this), this);
      worldObj.func_147453_f(xCoord, yCoord, zCoord, getBlockType());
      tankDirty = false;
    }
    return res;
  }
  
  public int getComparatorOutput() {
    FluidTankInfo info = getTankInfo(null)[0];
    return info == null || info.fluid == null ? 0 : (int) (((double) info.fluid.amount / (double) info.capacity) * 15);
  }

  private boolean processItems(boolean redstoneCheckPassed) {
    if(!redstoneCheckPassed) {
      return false;
    }
    if(!shouldDoWorkThisTick(20)) {
      return false;
    }
    return drainFullContainer() || fillEmptyContainer();
  }

  private boolean fillEmptyContainer() {
    ItemStack toFill = inventory[1];
    if(toFill == null) {
      return false;
    }
    if(tank.getFluidAmount() <= 0) {
      return false;
    }

    ItemStack filledContainer = FluidUtil.fillContainerFromInternalTank(this, toFill, false);
    if (filledContainer == null) {
      return false;
    }

    if (inventory[3] != null) {
      if (filledContainer.isStackable() && ItemUtil.areStackMergable(inventory[3], filledContainer)
          && inventory[3].stackSize < inventory[3].getMaxStackSize()) {
        filledContainer.stackSize += inventory[3].stackSize;
      } else {
        return false;
      }
    }

    FluidUtil.fillContainerFromInternalTank(this, toFill, true);

    toFill = toFill.copy();
    toFill.stackSize--;
    if (toFill.stackSize == 0) {
      setInventorySlotContents(1, null);
    } else {
      setInventorySlotContents(1, toFill);
    }

    setInventorySlotContents(3, filledContainer);

    markDirty();
    return false;
  }

  private boolean drainFullContainer() {
    ItemStack fillFrom = inventory[0];
    if(fillFrom == null) {
      return false;
    }
    FluidStack fluid = FluidUtil.getFluidFromItem(fillFrom);
    if(fluid == null) {
      return false;
    }
    ItemStack emptyItem = FluidUtil.getEmptyContainer(fillFrom);
    if(emptyItem != null && inventory[2] != null) {
      if(!inventory[2].isItemEqual(emptyItem) || inventory[2].getMaxStackSize() < inventory[2].stackSize + 1) {
        return false; //can't stack the empty container
      }
    }

    int filled = fillInternal(fluid, false);
    if(filled < fluid.amount) {
      return false; //can't empty the entire thing
    }
    fillInternal(fluid, true);

    fillFrom = fillFrom.copy();
    fillFrom.stackSize--;
    if(fillFrom.stackSize == 0) {
      setInventorySlotContents(0, null);
    } else {
      setInventorySlotContents(0, fillFrom);
    }

    if(emptyItem == null) {
        return true;
    }
    
    if(inventory[2] == null) {
      setInventorySlotContents(2, emptyItem);
    } else {
      ItemStack newStack = inventory[2].copy();
      newStack.stackSize++;
      setInventorySlotContents(2, newStack);
    }
    markDirty();
    return false;
  }

  @Override
  public void writeCommon(NBTTagCompound nbtRoot) {
    super.writeCommon(nbtRoot);
    nbtRoot.setInteger("tankType",getBlockMetadata());
    if(tank.getFluidAmount() > 0) {
      NBTTagCompound fluidRoot = new NBTTagCompound();
      tank.getFluid().writeToNBT(fluidRoot);
      nbtRoot.setTag("tankContents", fluidRoot);
    }
  }

  @Override
  public void readCommon(NBTTagCompound nbtRoot) {
    super.readCommon(nbtRoot);
    int tankType = nbtRoot.getInteger("tankType");
    tankType = MathHelper.clamp_int(tankType, 0, 1);
    if(tankType == 1) {
      tank = new FluidTankEio(32000);
    } else {
      tank = new FluidTankEio(16000);
    }
    
    if(nbtRoot.hasKey("tankContents")) {
      FluidStack fl = FluidStack.loadFluidStackFromNBT((NBTTagCompound) nbtRoot.getTag("tankContents"));
      tank.setFluid(fl);
    } else {
      tank.setFluid(null);
    }
  }

  @Override
  public FluidTank getInputTank() {
    return tank;
  }

  @Override
  public FluidTank getOutputTank() {
    return tank;
  }

  @Override
  public void setTanksDirty() {
    tankDirty = true;
  }

}
