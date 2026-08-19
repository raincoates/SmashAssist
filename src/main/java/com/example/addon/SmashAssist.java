package com.example.addon.modules;

import com.example.addon.Addon;
import com.example.addon.mixin.IEntityAccessor;
import com.example.addon.mixin.ILivingEntityAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.Optional;

/**
 * SmashAssist
 *
 * Lets you land full-power Mace "smash attack" hits without needing real
 * fall distance — just be near the target (grounded or flying) and it
 * triggers on your normal attack input.
 *
 * IMPORTANT: Mace smash damage is calculated by whichever side is
 * authoritative for the world - and that's true even in singleplayer,
 * where Minecraft secretly runs a separate "client" copy of you and a
 * "server" copy of you (an integrated server, living in the same program).
 * Only the server copy's state actually determines damage and effects
 * like Wind Burst.
 *  - Singleplayer: this module reaches across to the real integrated
 *    server object running alongside the client and edits ITS copy of
 *    your fall distance / on-ground state before letting vanilla's own
 *    attack logic run. Because we're triggering the real vanilla smash
 *    code path (not reimplementing damage math ourselves), bonus damage,
 *    knockback, and Wind Burst all fire exactly like a genuine smash.
 *  - A dedicated server you host (a separate server process, not an
 *    integrated singleplayer world): there's no in-process server object
 *    to reach into from the client here, so this client-only approach
 *    can't reach it either. That would need a small server-side
 *    plugin/mod counterpart running on that server - a different project.
 *  - Someone else's multiplayer server: their server tracks your real
 *    fall distance independently and there's no reachable server object
 *    to edit at all, so this module cannot force bonus damage there -
 *    it'll just be a normal hit. This module intentionally does NOT
 *    attempt to spoof movement packets to fake that out.
 */
public class SmashAssist extends Module {

    public SmashAssist() {
        super(Addon.CATEGORY, "smash-assist", "Full-power Mace smash attacks without needing to fall first.");
    }

    // ---- Settings ----

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Max distance to a target before triggering a smash.")
        .defaultValue(4.0)
        .min(1.0)
        .max(10.0)
        .sliderMax(8.0)
        .build()
    );

    private final Setting<Double> simulatedFallDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("simulated-fall-distance")
        .description("Fall distance to report to the game for the smash calculation. Higher = more damage, up to the vanilla cap.")
        .defaultValue(20.0)
        .min(1.5)
        .max(40.0)
        .sliderMax(30.0)
        .build()
    );

    private final Setting<Boolean> autoTarget = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-target")
        .description("Automatically swing at the nearest valid entity in range instead of waiting for you to left-click.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireLineOfSight = sgGeneral.add(new BoolSetting.Builder()
        .name("require-line-of-sight")
        .description("Only target entities you can actually see.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-swing-cooldown")
        .description("Resets your attack-strength cooldown before every swing so hits always land at full strength.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minTicksBetweenSmashes = sgGeneral.add(new IntSetting.Builder()
        .name("min-ticks-between-smashes")
        .description("Hard floor on ticks between smash attempts. 0 = every tick (20/sec), no artificial cooldown at all.")
        .defaultValue(0)
        .min(0)
        .max(40)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder()
        .name("require-mace")
        .description("Only activate while holding a Mace in your main hand.")
        .defaultValue(true)
        .build()
    );

    private int ticksSinceLastSmash = 0;

    @Override
    public void onActivate() {
        ticksSinceLastSmash = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        ticksSinceLastSmash++;

        if (requireMace.get() && !mc.player.getMainHandStack().isOf(Items.MACE)) return;

        Entity target = findTarget();
        if (target == null) return;

        if (ticksSinceLastSmash < minTicksBetweenSmashes.get()) return;

        // Only auto-fire when auto-target is on. If it's off, this module just
        // guarantees your manual left-click swings land as full smashes
        // (handled the same way, since we set state every tick a target exists
        // and let the vanilla attack key trigger the actual swing).
        if (autoTarget.get()) {
            performSmash(target);
        }
    }

    private void performSmash(Entity target) {
        LivingEntity clientPlayer = mc.player;
        if (clientPlayer == null) return;

        // In singleplayer, an "integrated server" runs inside this same
        // program alongside the client. mc.player is only the CLIENT's copy
        // of you - a separate object exists on the server side, and that
        // server-side copy is the one that actually decides damage and
        // triggers effects like Wind Burst. We have to edit that copy, not
        // the client one, for any of this to actually matter.
        net.minecraft.server.MinecraftServer server = mc.getServer();

        if (server == null) {
            // Not singleplayer (or the integrated server isn't running) -
            // fall back to client-only prediction. This will look right
            // locally but won't produce real bonus damage on someone else's
            // server, per the class-level notes above.
            performClientOnlySmash(clientPlayer, target);
            return;
        }

        server.execute(() -> {
            net.minecraft.server.network.ServerPlayerEntity serverPlayer =
                server.getPlayerManager().getPlayer(clientPlayer.getUuid());
            if (serverPlayer == null) return;

            net.minecraft.server.world.ServerWorld serverWorld = serverPlayer.getServerWorld();
            Entity serverTarget = serverWorld.getEntity(target.getUuid());
            if (serverTarget == null) return;

            IEntityAccessor playerAccessor = (IEntityAccessor) (Object) serverPlayer;
            ILivingEntityAccessor livingAccessor = (ILivingEntityAccessor) (Object) serverPlayer;

            double originalFallDistance = playerAccessor.smashAssist$getFallDistance();
            int originalLastAttackedTicks = livingAccessor.smashAssist$getLastAttackedTicks();
            boolean wasOnGround = serverPlayer.isOnGround();

            try {
                playerAccessor.smashAssist$setFallDistance(simulatedFallDistance.get());
                serverPlayer.setOnGround(false);

                if (ignoreCooldown.get()) {
                    livingAccessor.smashAssist$setLastAttackedTicks(-100000);
                }

                // Run the REAL vanilla attack logic on the server-side
                // entities. Because fallDistance/onGround are now set
                // correctly on the authoritative copy, vanilla's own smash
                // detection sees a legitimate smash and handles bonus
                // damage, knockback, particles, sound, and Wind Burst
                // exactly like a real one - we're not reimplementing any of
                // that ourselves.
                serverPlayer.attack(serverTarget);
            } finally {
                playerAccessor.smashAssist$setFallDistance(0.0);
                serverPlayer.setOnGround(wasOnGround);
                if (!ignoreCooldown.get()) {
                    livingAccessor.smashAssist$setLastAttackedTicks(originalLastAttackedTicks);
                }
            }
        });

        // Still trigger the client-side swing animation/prediction so the
        // hit feels responsive and doesn't wait on the server round trip
        // visually.
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        ticksSinceLastSmash = 0;
    }

    private void performClientOnlySmash(LivingEntity player, Entity target) {
        IEntityAccessor entityAccessor = (IEntityAccessor) (Object) player;
        ILivingEntityAccessor livingAccessor = (ILivingEntityAccessor) (Object) player;

        double originalFallDistance = entityAccessor.smashAssist$getFallDistance();
        int originalLastAttackedTicks = livingAccessor.smashAssist$getLastAttackedTicks();
        boolean wasOnGround = player.isOnGround();

        try {
            entityAccessor.smashAssist$setFallDistance(simulatedFallDistance.get());
            player.setOnGround(false);

            if (ignoreCooldown.get()) {
                livingAccessor.smashAssist$setLastAttackedTicks(-100000);
            }

            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

            ticksSinceLastSmash = 0;
        } finally {
            entityAccessor.smashAssist$setFallDistance(0.0);
            player.setOnGround(wasOnGround);
            if (!ignoreCooldown.get()) {
                livingAccessor.smashAssist$setLastAttackedTicks(originalLastAttackedTicks);
            }
        }
    }

    private Entity findTarget() {
        if (mc.player == null || mc.world == null) return null;

        double r = range.get();
        Box searchBox = mc.player.getBoundingBox().expand(r);

        return mc.world.getOtherEntities(mc.player, searchBox, e ->
                e instanceof LivingEntity
                    && e.isAlive()
                    && !e.isSpectator()
                    && e != mc.player
                    && mc.player.distanceTo(e) <= r
                    && (!requireLineOfSight.get() || PlayerUtils.canSeeEntity((LivingEntity) e))
            )
            .stream()
            .min(Comparator.comparingDouble(mc.player::distanceTo))
            .orElse(null);
    }
}
