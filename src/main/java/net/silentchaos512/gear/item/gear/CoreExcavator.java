/*
 * Silent Gear -- CoreExcavator
 * Copyright (C) 2018 SilentChaos512
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 3
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.silentchaos512.gear.item.gear;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.ToolType;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.util.IAOETool;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CoreExcavator extends CoreShovel implements IAOETool {
    @Override
    public GearType getGearType() {
        return GearType.EXCAVATOR;
    }

    @Nonnull
    @Override
    public ToolType getAOEToolClass() {
        return ToolType.SHOVEL;
    }

    @Nullable
    @Override
    public RayTraceResult rayTraceBlocks(World world, PlayerEntity player) {
        return rayTrace(world, player, RayTraceContext.FluidMode.NONE);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, PlayerEntity player) {
        return IAOETool.BreakHandler.onBlockStartBreak(itemstack, pos, player);
    }
}
