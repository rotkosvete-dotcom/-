package foxgirl;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

public class FoxGirlMod implements ModInitializer {
	public static final String MOD_ID = "foxgirl";

	public static final EntityType<FoxGirlEntity> FOX_GIRL = Registry.register(
		Registries.ENTITY_TYPE, new Identifier(MOD_ID, "fox_girl"),
		FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, FoxGirlEntity::new)
			.dimensions(EntityDimensions.fixed(0.6f, 1.9f))
			.trackRangeBlocks(10)
			.build());

	public static final Item SPAWN_EGG = Registry.register(
		Registries.ITEM, new Identifier(MOD_ID, "fox_girl_spawn_egg"),
		new SpawnEggItem(FOX_GIRL, 0x7A2E3C, 0xF0A030, new FabricItemSettings()));

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(FOX_GIRL, FoxGirlEntity.createAttributes());

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(SPAWN_EGG));

		SpawnRestriction.register(FOX_GIRL, SpawnRestriction.Location.ON_GROUND,
			Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);

		// Дикие лисички встречаются в лесах и тайге (редко, по одной)
		BiomeModifications.addSpawn(
			BiomeSelectors.includeByKey(BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.FLOWER_FOREST,
				BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA),
			SpawnGroup.CREATURE, FOX_GIRL, 3, 1, 1);
	}
}
