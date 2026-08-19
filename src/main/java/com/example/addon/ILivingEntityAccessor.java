package com.example.addon.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LivingEntity#lastAttackedTicks. Attack "cooldown" (the strength
 * multiplier applied to a swing, see LivingEntity#getAttackCooldownProgress)
 * is derived from (age - lastAttackedTicks) vs. the weapon's attack-speed
 * attribute. Rewinding lastAttackedTicks far enough guarantees the next
 * swing lands at 100% strength regardless of swing timing.
 *
 * As with IEntityAccessor, re-check the field name against current Yarn
 * mappings if this breaks on a Minecraft update.
 */
@Mixin(LivingEntity.class)
public interface ILivingEntityAccessor {

    @Accessor("lastAttackedTicks")
    int smashAssist$getLastAttackedTicks();

    @Accessor("lastAttackedTicks")
    void smashAssist$setLastAttackedTicks(int ticks);
}
