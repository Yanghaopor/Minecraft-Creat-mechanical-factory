package com.example.examplemod.blockentity;

import com.example.examplemod.construction.ConstructionPortMode;
import com.example.examplemod.construction.ConstructionRoomManager;
import com.example.examplemod.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class ConstructionInterfaceBlockEntity extends KineticBlockEntity {
    private ConstructionPortMode portMode = ConstructionPortMode.DISABLED;
    private float bridgedSourceSpeed;
    private float bridgedCapacity;
    private float bridgedStress;
    private boolean bridgeSourceMode;

    public ConstructionInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_INTERFACE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (getLevel() instanceof ServerLevel) {
            ConstructionBlockEntity host = getHost();
            Direction direction = getPortDirection();
            if (host != null && direction != null && host.getPortMode(direction) == ConstructionPortMode.KINETIC_INPUT) {
                updateBridgeSource(host.getTheoreticalSpeed(), true);
            } else {
                updateBridgeSource(0, false);
            }
        }
    }

    public boolean isKineticActive() {
        Direction direction = getPortDirection();
        ConstructionBlockEntity host = getHost();
        return direction != null && host != null && host.getPortMode(direction).isKinetic();
    }

    @Override
    public float getGeneratedSpeed() {
        return bridgeSourceMode ? bridgedSourceSpeed : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!bridgeSourceMode) {
            return super.calculateAddedStressCapacity();
        }
        lastCapacityProvided = bridgedCapacity;
        return bridgedCapacity;
    }

    @Override
    public float calculateStressApplied() {
        if (portMode != ConstructionPortMode.KINETIC_OUTPUT) {
            return super.calculateStressApplied();
        }
        lastStressApplied = bridgedStress;
        return bridgedStress;
    }

    public float getExportedCapacity() {
        float speed = Math.abs(getTheoreticalSpeed());
        return speed < 0.001F || !hasNetwork() ? 0 : getOrCreateNetwork().calculateCapacity() / speed;
    }

    public float getExportedStress() {
        float speed = Math.abs(getTheoreticalSpeed());
        return speed < 0.001F || !hasNetwork() ? 0 : getOrCreateNetwork().calculateStress() / speed;
    }

    public void refreshKinetics() {
        detachKinetics();
        clearKineticInformation();
        bridgeSourceMode = false;
        bridgedSourceSpeed = 0;
        bridgedCapacity = 0;
        bridgedStress = 0;
        updateSpeed = true;
    }

    public void updateBridgeSource(float speed, boolean sourceMode) {
        if (bridgeSourceMode == sourceMode && Math.abs(bridgedSourceSpeed - speed) < 0.001F) {
            return;
        }
        detachKinetics();
        clearKineticInformation();
        bridgeSourceMode = sourceMode;
        bridgedSourceSpeed = sourceMode ? speed : 0;
        if (!sourceMode) {
            bridgedCapacity = 0;
        }
        if (sourceMode && Math.abs(speed) >= 0.001F) {
            setSpeed(speed);
            setNetwork(getBlockPos().asLong());
            attachKinetics();
        } else if (!sourceMode) {
            updateSpeed = true;
        }
        setChanged();
    }

    public void setBridgedCapacity(float capacity) {
        capacity = sanitizeStressValue(capacity);
        if (Math.abs(bridgedCapacity - capacity) < 0.001F) {
            return;
        }
        bridgedCapacity = capacity;
        lastCapacityProvided = capacity;
        if (bridgeSourceMode && hasNetwork()) {
            getOrCreateNetwork().updateCapacityFor(this, capacity);
        }
        setChanged();
    }

    public void setBridgedStress(float stress) {
        stress = sanitizeStressValue(stress);
        if (Math.abs(bridgedStress - stress) < 0.001F) {
            return;
        }
        bridgedStress = stress;
        lastStressApplied = stress;
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, stress);
        }
        setChanged();
    }

    private static float sanitizeStressValue(float value) {
        return Float.isFinite(value) && value > 0 ? value : 0;
    }

    public ConstructionPortMode getPortMode() {
        return portMode;
    }

    public void setPortMode(ConstructionPortMode portMode) {
        if (this.portMode == portMode) {
            return;
        }
        refreshKinetics();
        this.portMode = portMode;
        bridgedCapacity = 0;
        bridgedStress = 0;
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("PortMode", portMode.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        portMode = ConstructionPortMode.fromId(tag.getInt("PortMode"));
    }

    @Nullable
    private ConstructionBlockEntity getHost() {
        return getLevel() instanceof ServerLevel level ? ConstructionRoomManager.findHost(level, getBlockPos()) : null;
    }

    @Nullable
    private Direction getPortDirection() {
        return getLevel() instanceof ServerLevel level ? ConstructionRoomManager.getPortDirection(level, getBlockPos()) : null;
    }
}
