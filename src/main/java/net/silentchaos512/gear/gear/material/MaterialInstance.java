package net.silentchaos512.gear.gear.material;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.silentchaos512.gear.SilentGear;
import net.silentchaos512.gear.api.event.GetMaterialStatsEvent;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.material.IMaterial;
import net.silentchaos512.gear.api.material.IMaterialCategory;
import net.silentchaos512.gear.api.material.IMaterialInstance;
import net.silentchaos512.gear.api.part.MaterialGrade;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.api.stats.ItemStat;
import net.silentchaos512.gear.api.stats.ItemStats;
import net.silentchaos512.gear.api.stats.StatInstance;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.StatGearKey;
import net.silentchaos512.gear.gear.part.RepairContext;
import net.silentchaos512.gear.util.DataResource;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.GearHelper;
import net.silentchaos512.lib.util.InventoryUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public final class MaterialInstance implements IMaterialInstance {
    private static final Map<ResourceLocation, MaterialInstance> QUICK_CACHE = new HashMap<>();

    private final IMaterial material;
    private final MaterialGrade grade;
    private final ItemStack item;

    private MaterialInstance(IMaterial material) {
        this(material, MaterialGrade.NONE, material.getDisplayItem(PartType.MAIN, 0));
    }

    private MaterialInstance(IMaterial material, MaterialGrade grade) {
        this(material, grade, material.getDisplayItem(PartType.MAIN, 0));
    }

    private MaterialInstance(IMaterial material, ItemStack craftingItem) {
        this(material, MaterialGrade.NONE, craftingItem);
    }

    private MaterialInstance(IMaterial material, MaterialGrade grade, ItemStack craftingItem) {
        this.material = material;
        this.grade = grade;
        this.item = craftingItem.copy();
        this.item.setCount(1);
    }

    public static MaterialInstance of(IMaterial material) {
        return QUICK_CACHE.computeIfAbsent(material.getId(), id -> new MaterialInstance(material));
    }

    public static MaterialInstance of(IMaterial material, MaterialGrade grade) {
        return new MaterialInstance(material, grade);
    }

    public static MaterialInstance of(IMaterial material, ItemStack craftingItem) {
        return new MaterialInstance(material, MaterialGrade.fromStack(craftingItem), craftingItem);
    }

    public static MaterialInstance of(IMaterial material, MaterialGrade grade, ItemStack craftingItem) {
        return new MaterialInstance(material, grade, craftingItem);
    }

    public static IMaterialInstance of(DataResource<IMaterial> material, ItemStack craftingItem) {
        if (material.isPresent())
            return of(material.get(), craftingItem);
        return LazyMaterialInstance.of(material, MaterialGrade.fromStack(craftingItem));
    }

    @Nullable
    public static MaterialInstance from(ItemStack stack) {
        IMaterial material = MaterialManager.from(stack);
        if (material != null) {
            return of(material, stack);
        }
        return null;
    }

    @Override
    public ResourceLocation getId() {
        return material.getId();
    }

    @Nonnull
    @Override
    public IMaterial get() {
        return material;
    }

    @Override
    public MaterialGrade getGrade() {
        return grade;
    }

    @Override
    public ItemStack getItem() {
        return item;
    }

    public Collection<IMaterialCategory> getCategories() {
        return material.getCategories(this);
    }

    public boolean hasAnyCategory(Collection<IMaterialCategory> others) {
        for (IMaterialCategory cat1 : this.getCategories()) {
            for (IMaterialCategory cat2 : others) {
                if (cat1.matches(cat2)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getTier(PartType partType) {
        return material.getTier(partType);
    }

    public Set<PartType> getPartTypes() {
        return material.getPartTypes(this);
    }

    @Override
    public Collection<StatInstance> getStatModifiers(PartType partType, StatGearKey key, ItemStack gear) {
        Collection<StatInstance> mods = material.getStatModifiers(this, partType, key, gear);

        ItemStat stat = ItemStats.get(key.getStat());
        if (stat == null) {
            SilentGear.LOGGER.warn("Unknown item stat: {}", key.getStat().getStatId());
            SilentGear.LOGGER.catching(new NullPointerException());
            return mods;
        }

        if (stat.isAffectedByGrades() && grade != MaterialGrade.NONE) {
            // Apply grade bonus to all modifiers. Makes it easier to see the effect on rods and such.
            float bonus = grade.bonusPercent / 100f;
            mods = mods.stream().map(m -> {
                float value = m.getValue();
                // Taking the abs of value times bonus makes negative mods become less negative
                return m.copySetValue(value + Math.abs(value) * bonus);
            }).collect(Collectors.toList());
        }

        GetMaterialStatsEvent event = new GetMaterialStatsEvent(this, stat, partType, mods);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getModifiers();
    }

    public Collection<TraitInstance> getTraits(PartType partType) {
        return material.getTraits(this, partType, ItemStack.EMPTY);
    }

    public Collection<TraitInstance> getTraits(PartType partType, ItemStack gear) {
        return material.getTraits(this, partType, gear);
    }

    @Override
    public float getStat(PartType partType, StatGearKey key, ItemStack gear) {
        ItemStat stat = ItemStats.get(key.getStat());
        if (stat == null) return key.getStat().getDefaultValue();

        return stat.compute(stat.getDefaultValue(), getStatModifiers(partType, key));
    }

    @Override
    public Collection<TraitInstance> getTraits(PartType partType, GearType gearType, ItemStack gear) {
        return material.getTraits(this, partType, gearType, gear);
    }

    public boolean canRepair(ItemStack gear) {
        return material.allowedInPart(this, PartType.MAIN) && GearData.getTier(gear) <= this.getTier(PartType.MAIN);
    }

    public int getRepairValue(ItemStack gear) {
        return this.getRepairValue(gear, RepairContext.Type.QUICK);
    }

    public int getRepairValue(ItemStack gear, RepairContext.Type type) {
        if (this.canRepair(gear)) {
            float durability = getStat(PartType.MAIN, GearHelper.getDurabilityStat(gear));
            float repairValueMulti = getStat(PartType.MAIN, ItemStats.REPAIR_VALUE);
            float itemRepairModifier = GearHelper.getRepairModifier(gear);
            float typeBonus = 1f + type.getBonusEfficiency();
            return Math.round(durability * repairValueMulti * itemRepairModifier * typeBonus) + 1;
        }
        return 0;
    }

    @Nullable
    public static MaterialInstance read(CompoundNBT nbt) {
        ResourceLocation id = ResourceLocation.tryCreate(nbt.getString("ID"));
        IMaterial material = MaterialManager.get(id);
        if (material == null) return null;

        ItemStack stack = readOrGetDefaultItem(material, nbt);
        return of(material, stack);
    }

    private static ItemStack readOrGetDefaultItem(IMaterial material, CompoundNBT nbt) {
        ItemStack stack = ItemStack.read(nbt.getCompound("Item"));
        if (stack.isEmpty()) {
            // Item is missing from NBT, so pick something from the ingredient
            ItemStack[] array = material.getIngredient().getMatchingStacks();
            if (array.length > 0) {
                return array[0].copy();
            }
        }
        return stack;
    }

    @Override
    public CompoundNBT write(CompoundNBT nbt) {
        nbt.putString("ID", material.getId().toString());
        nbt.put("Item", item.write(new CompoundNBT()));
        return nbt;
    }

    @Override
    public IFormattableTextComponent getDisplayName(PartType partType, ItemStack gear) {
        return material.getDisplayName(partType, gear);
    }

    public ITextComponent getDisplayNameWithGrade(PartType partType, ItemStack gear) {
        IFormattableTextComponent text = getDisplayName(partType, gear).copyRaw();
        if (this.grade != MaterialGrade.NONE) {
            text.appendString(" (").append(this.grade.getDisplayName()).appendString(")");
        }
        return text;
    }

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeResourceLocation(this.material.getId());
        buffer.writeEnumValue(this.grade);
    }

    @Nullable
    public static MaterialInstance readShorthand(String str) {
        if (str.contains("#")) {
            String[] parts = str.split("#");
            ResourceLocation id = SilentGear.getIdWithDefaultNamespace(parts[0]);
            IMaterial material = MaterialManager.get(id);
            if (material != null) {
                MaterialGrade grade = MaterialGrade.fromString(parts[1]);
                return new MaterialInstance(material, grade);
            }

            return null;
        }

        ResourceLocation id = SilentGear.getIdWithDefaultNamespace(str);
        IMaterial material = MaterialManager.get(id);
        if (material != null) {
            return new MaterialInstance(material);
        }

        return null;
    }

    public static String writeShorthand(MaterialInstance material) {
        if (material.grade != MaterialGrade.NONE) {
            return material.getId() + "#" + material.grade;
        }
        return material.getId().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaterialInstance that = (MaterialInstance) o;
        return material.equals(that.material) &&
                grade == that.grade &&
                InventoryUtils.canItemsStack(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material, grade, item);
    }
}
