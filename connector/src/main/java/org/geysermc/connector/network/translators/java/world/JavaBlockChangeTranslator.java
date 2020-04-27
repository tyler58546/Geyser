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

package org.geysermc.connector.network.translators.java.world;

import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.data.SoundEvent;
import com.nukkitx.protocol.bedrock.packet.LevelSoundEventPacket;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;
import org.geysermc.connector.network.translators.block.BlockTranslator;
import org.geysermc.connector.utils.ChunkUtils;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerBlockChangePacket;

@Translator(packet = ServerBlockChangePacket.class)
public class JavaBlockChangeTranslator extends PacketTranslator<ServerBlockChangePacket> {

    @Override
    public void translate(ServerBlockChangePacket packet, GeyserSession session) {
        ChunkUtils.updateBlock(session, packet.getRecord().getBlock(), packet.getRecord().getPosition());
        Vector3i lastPlacePos = session.getLastBlockPlacePosition();
        if (lastPlacePos == null) {
            return;
        }
        if (lastPlacePos.getX() != packet.getRecord().getPosition().getX()
                || lastPlacePos.getY() != packet.getRecord().getPosition().getY()
                || lastPlacePos.getZ() != packet.getRecord().getPosition().getZ()) {
            return;
        }

        // We need to check if the identifier is the same, else a packet with the sound of what the
        // player has in their hand is played, despite if the block is being placed or not
        boolean contains = false;
        String identifier = BlockTranslator.getJavaIdBlockMap().inverse().get(packet.getRecord().getBlock()).split("\\[")[0];
        if (identifier.equals(session.getLastBlockPlacedId())) {
            contains = true;
        }

        if (!contains) {
            session.setLastBlockPlacePosition(null);
            session.setLastBlockPlacedId(null);
            return;
        }

        // This is not sent from the server, so we need to send it this way
        LevelSoundEventPacket placeBlockSoundPacket = new LevelSoundEventPacket();
        placeBlockSoundPacket.setSound(SoundEvent.PLACE);
        placeBlockSoundPacket.setPosition(lastPlacePos.toFloat());
        placeBlockSoundPacket.setBabySound(false);
        placeBlockSoundPacket.setExtraData(BlockTranslator.getBedrockBlockId(packet.getRecord().getBlock()));
        placeBlockSoundPacket.setIdentifier(":");
        session.getUpstream().sendPacket(placeBlockSoundPacket);
        session.setLastBlockPlacePosition(null);
        session.setLastBlockPlacedId(null);
    }
}
