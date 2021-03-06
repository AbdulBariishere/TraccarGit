/*
 * Copyright 2017 - 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.DataConverter;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Network;
import org.traccar.model.Position;
import org.traccar.model.WifiAccessPoint;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class SigfoxProtocolDecoder extends BaseHttpProtocolDecoder {

    public SigfoxProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        FullHttpRequest request = (FullHttpRequest) msg;
        String content = request.content().toString(StandardCharsets.UTF_8);
        if (!content.startsWith("{")) {
            content = URLDecoder.decode(content.split("=")[0], "UTF-8");
        }
        JsonObject json = Json.createReader(new StringReader(content)).readObject();

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, json.getString("device"));
        if (deviceSession == null) {
            sendResponse(channel, HttpResponseStatus.BAD_REQUEST);
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        position.setTime(new Date(json.getInt("time") * 1000L));

        String data = json.getString(json.containsKey("data") ? "data" : "payload");
        ByteBuf buf = Unpooled.wrappedBuffer(DataConverter.parseHex(data));
        try {
            int event = buf.readUnsignedByte();
            if (event >> 4 == 0) {

                position.setValid(true);
                position.setLatitude(buf.readIntLE() * 0.0000001);
                position.setLongitude(buf.readIntLE() * 0.0000001);
                position.setCourse(buf.readUnsignedByte() * 2);
                position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedByte()));

                position.set(Position.KEY_BATTERY, buf.readUnsignedByte() * 0.025);

            } else {

                position.set(Position.KEY_EVENT, event);

                while (buf.isReadable()) {
                    int type = buf.readUnsignedByte();
                    switch (type) {
                        case 0x01:
                            position.setValid(true);
                            position.setLatitude(buf.readMedium());
                            position.setLongitude(buf.readMedium());
                            break;
                        case 0x02:
                            position.setValid(true);
                            position.setLatitude(buf.readFloat());
                            position.setLongitude(buf.readFloat());
                            break;
                        case 0x03:
                            position.set(Position.PREFIX_TEMP + 1, buf.readByte() * 0.5);
                            break;
                        case 0x04:
                            position.set(Position.KEY_BATTERY, buf.readUnsignedByte() * 0.1);
                            break;
                        case 0x05:
                            position.set(Position.KEY_BATTERY_LEVEL, buf.readUnsignedByte());
                            break;
                        case 0x06:
                            String mac = ByteBufUtil.hexDump(buf.readSlice(6)).replaceAll("(..)", "$1:");
                            position.setNetwork(new Network(WifiAccessPoint.from(
                                    mac.substring(0, mac.length() - 1), buf.readUnsignedByte())));
                            break;
                        case 0x07:
                            buf.skipBytes(10); // wifi extended
                            break;
                        case 0x08:
                            buf.skipBytes(6); // accelerometer
                            break;
                        case 0x09:
                            position.setSpeed(UnitsConverter.knotsFromKph(buf.readUnsignedByte()));
                            break;
                        default:
                            buf.readUnsignedByte(); // fence number
                            break;
                    }
                }

            }
        } finally {
            buf.release();
        }

        if (position.getLatitude() == 0 && position.getLongitude() == 0) {
            getLastLocation(position, position.getDeviceTime());
        }

        if (json.containsKey("rssi")) {
            position.set(Position.KEY_RSSI, json.getJsonNumber("rssi").doubleValue());
        }
        if (json.containsKey("seqNumber")) {
            position.set(Position.KEY_INDEX, json.getInt("seqNumber"));
        }

        sendResponse(channel, HttpResponseStatus.OK);
        return position;
    }

}
