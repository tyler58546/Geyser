/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.network.translators.java.entity;

import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntitySetPassengersPacket;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.data.EntityData;
import com.nukkitx.protocol.bedrock.data.EntityFlag;
import com.nukkitx.protocol.bedrock.data.EntityLink;
import com.nukkitx.protocol.bedrock.packet.SetEntityLinkPacket;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;

import java.util.Arrays;

@Translator(packet = ServerEntitySetPassengersPacket.class)
public class JavaEntitySetPassengersTranslator extends PacketTranslator<ServerEntitySetPassengersPacket> {

    @Override
    public void translate(ServerEntitySetPassengersPacket packet, GeyserSession session) {
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getEntityId());;
        if (entity == null) return;

        LongOpenHashSet passengers = entity.getPassengers().clone();
        boolean rider = true;
        for (long passengerId : packet.getPassengerIds()) {
            Entity passenger = session.getEntityCache().getEntityByJavaId(passengerId);
            if (passengerId == session.getPlayerEntity().getEntityId()) {
                passenger = session.getPlayerEntity();
            }
            // Passenger hasn't loaded in and entity link needs to be set later
            if (passenger == null && passengerId != 0) {
                session.getEntityCache().addCachedPlayerEntityLink(passengerId, packet.getEntityId());
            }
            if (passenger == null) {
                continue;
            }

            EntityLink.Type type = rider ? EntityLink.Type.RIDER : EntityLink.Type.PASSENGER;
            SetEntityLinkPacket linkPacket = new SetEntityLinkPacket();
            linkPacket.setEntityLink(new EntityLink(entity.getGeyserId(), passenger.getGeyserId(), type, false));
            session.getUpstream().sendPacket(linkPacket);
            passengers.add(passengerId);

            passenger.getMetadata().put(EntityData.RIDER_ROTATION_LOCKED, 1);
            passenger.getMetadata().put(EntityData.RIDER_MAX_ROTATION, 90);
            passenger.getMetadata().put(EntityData.RIDER_MIN_ROTATION, !passengers.isEmpty() ? -90 : 0);

            passenger.updateBedrockMetadata(session);
            this.updateOffset(passenger, entity.getEntityType(), session, rider, true);
            rider = false;
        }

        entity.setPassengers(passengers);

        for (long passengerId : entity.getPassengers()) {
            Entity passenger = session.getEntityCache().getEntityByJavaId(passengerId);
            if (passenger == null) {
                continue;
            }
            if (Arrays.stream(packet.getPassengerIds()).noneMatch(id -> id == passengerId)) {
                SetEntityLinkPacket linkPacket = new SetEntityLinkPacket();
                linkPacket.setEntityLink(new EntityLink(entity.getGeyserId(), passenger.getGeyserId(), EntityLink.Type.REMOVE, false));
                session.getUpstream().sendPacket(linkPacket);
                passengers.remove(passenger.getEntityId());

                this.updateOffset(passenger, entity.getEntityType(), session, false, false);
            }
        }
    }

    private void updateOffset(Entity passenger, EntityType mountType, GeyserSession session, boolean rider, boolean riding) {
        // Without these, Bedrock players will find themselves in the floor when mounting
        float yOffset = 0;
        switch (mountType) {
            case BOAT:
                yOffset = passenger.getEntityType() == EntityType.PLAYER ? 1.02001f : -0.2f;
                break;
            case MINECART:
                yOffset = passenger.getEntityType() == EntityType.PLAYER ? 1.02001f : 0f;
                break;
            case DONKEY:
                yOffset = 2.1f;
                break;
            case HORSE:
            case MULE:
                yOffset = 2.3f;
                break;
            case PIG:
                yOffset = 1.85001f;
                break;
        }
        Vector3f offset = Vector3f.from(0f, yOffset, 0f);
        if (rider) {
            offset.add(Vector3f.from(0.2, 0, 0));
        } else {
            offset.add(Vector3f.from(-0.6, 0, 0));
        }
        passenger.getMetadata().getFlags().setFlag(EntityFlag.RIDING, riding);
        if (riding) {
            passenger.getMetadata().put(EntityData.RIDER_SEAT_POSITION, offset);
        }
        passenger.updateBedrockMetadata(session);
    }
}
