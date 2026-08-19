package com.example.addon.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Entity#fallDistance so we can temporarily report "we just fell"
 * for the purposes of the Mace smash-attack calculation.
 *
 * Field name below is the current Yarn mapping as of 1.21.x — if this addon
 * stops compiling after a Minecraft/Yarn update, search the new mappings
 * (e.g. via Linkie: https://linkie.shedaniel.dev/) for "fallDistance" and
 * update the string in @Accessor to match.
 */
@Mixin(Entity.class)
public interface IEntityAccessor {

    @Accessor("fallDistance")
    double smashAssist$getFallDistance();

    @Accessor("fallDistance")
    void smashAssist$setFallDistance(double fallDistance);
}
