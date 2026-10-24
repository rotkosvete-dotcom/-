package foxgirl;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Лисичка-компаньон для режима выживания.
 *  - Приручается едой (шанс 1/3 за кормление).
 *  - Имеет "сытость": пока сыта — помогает, голодная — слабеет и не помогает.
 *  - Защищает хозяина, бьёт монстров (кроме криперов — от них убегает).
 *  - Лечит хозяина (регенерация), если ему плохо.
 *  - Ночью подсвечивает ближайших монстров (Glowing) — разведка.
 *  - ПКМ пустой рукой — сесть / идти следом.
 */
public class FoxGirlEntity extends TameableEntity {
	private static final int MAX_HUNGER = 20;
	private static final int HUNGER_TICKS = 1200; // -1 сытости в минуту
	private int hunger = 12;

	public FoxGirlEntity(EntityType<? extends FoxGirlEntity> type, World world) {
		super(type, world);
		this.setTamed(false);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return MobEntity.createMobAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(1, new SwimGoal(this));
		this.goalSelector.add(2, new SitGoal(this));
		this.goalSelector.add(3, new FleeEntityGoal<>(this, CreeperEntity.class, 8.0f, 1.1, 1.6));
		this.goalSelector.add(4, new MeleeAttackGoal(this, 1.2, true));
		this.goalSelector.add(5, new FollowOwnerGoal(this, 1.1, 8.0f, 2.0f, false));
		this.goalSelector.add(6, new TemptGoal(this, 1.1,
			Ingredient.ofItems(Items.SWEET_BERRIES, Items.COOKED_CHICKEN, Items.COOKED_BEEF, Items.COOKED_PORKCHOP), false));
		this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.9));
		this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
		this.goalSelector.add(8, new LookAroundGoal(this));

		this.targetSelector.add(1, new TrackOwnerAttackerGoal(this));
		this.targetSelector.add(2, new AttackWithOwnerGoal(this));
		this.targetSelector.add(3, new RevengeGoal(this).setGroupRevenge());
		this.targetSelector.add(4, new ActiveTargetGoal<>(this, HostileEntity.class, 5, false, false,
			e -> this.isTamed() && this.hunger > 0 && !(e instanceof CreeperEntity)));
	}

	@Override
	public void setTamed(boolean tamed) {
		super.setTamed(tamed);
		if (tamed) {
			this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(30.0);
			this.setHealth(30.0f);
		}
	}

	@Override
	public ActionResult interactMob(PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);

		if (stack.isFood()) {
			boolean needsFood = !this.isTamed() || this.hunger < MAX_HUNGER || this.getHealth() < this.getMaxHealth();
			if (needsFood) {
				if (!this.getWorld().isClient) {
					FoodComponent food = stack.getItem().getFoodComponent();
					int value = food != null ? food.getHunger() : 1;
					if (!player.getAbilities().creativeMode) stack.decrement(1);
					this.hunger = Math.min(MAX_HUNGER, this.hunger + value * 2);
					this.heal(value);
					if (!this.isTamed()) {
						if (this.random.nextInt(3) == 0) {
							this.setOwner(player);
							this.navigation.stop();
							this.setTarget(null);
							this.setSitting(false);
							this.setInSittingPose(false);
							this.getWorld().sendEntityStatus(this, (byte) 7); // сердечки
							player.sendMessage(Text.translatable("message.foxgirl.tamed"), true);
						} else {
							this.getWorld().sendEntityStatus(this, (byte) 6); // дымок
						}
					} else {
						this.getWorld().sendEntityStatus(this, (byte) 7);
					}
				}
				return ActionResult.success(this.getWorld().isClient);
			}
		}

		if (this.isTamed() && this.isOwner(player) && hand == Hand.MAIN_HAND && stack.isEmpty()) {
			if (!this.getWorld().isClient) {
				boolean sit = !this.isSitting();
				this.setSitting(sit);
				this.setInSittingPose(sit);
				this.jumping = false;
				this.navigation.stop();
				this.setTarget(null);
				player.sendMessage(Text.translatable(sit ? "message.foxgirl.sit" : "message.foxgirl.follow"), true);
			}
			return ActionResult.success(this.getWorld().isClient);
		}
		return ActionResult.PASS;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient || !this.isTamed()) return;

		// Голод
		if (this.age % HUNGER_TICKS == 0 && this.hunger > 0) {
			this.hunger--;
			if (this.hunger == 0 && this.getOwner() instanceof ServerPlayerEntity sp) {
				sp.sendMessage(Text.translatable("message.foxgirl.hungry"), true);
			}
		}
		if (this.hunger == 0) {
			if (this.age % 100 == 0) {
				this.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 140, 0, true, false));
			}
			return;
		}

		// Помощь хозяину
		if (this.age % 100 == 0) {
			LivingEntity owner = this.getOwner();
			if (owner == null || this.squaredDistanceTo(owner) > 12 * 12) return;

			if (owner.getHealth() <= owner.getMaxHealth() / 2.0f) {
				owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, true));
			}
			if (this.getWorld().isNight()) {
				for (HostileEntity h : this.getWorld().getEntitiesByClass(HostileEntity.class,
					this.getBoundingBox().expand(16.0), e -> true)) {
					h.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0, true, false));
				}
			}
		}
	}

	@Override
	public boolean canAttackWithOwner(LivingEntity target, LivingEntity owner) {
		if (target instanceof CreeperEntity || target instanceof GhastEntity) return false;
		if (target instanceof FoxGirlEntity other) return other.getOwner() != owner;
		if (target instanceof PlayerEntity p && owner instanceof PlayerEntity po && !po.shouldDamagePlayer(p)) return false;
		return !(target instanceof TameableEntity t && t.isTamed());
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) { return false; }

	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) { return null; }

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("FoxHunger", this.hunger);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("FoxHunger")) this.hunger = nbt.getInt("FoxHunger");
	}

	@Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_FOX_AMBIENT; }
	@Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_FOX_HURT; }
	@Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_FOX_DEATH; }
}
