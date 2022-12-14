/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
 *
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.graylog2.radio.transports.kafka;

import com.google.common.collect.Maps;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.RadioMessage;
import org.graylog2.radio.Radio;
import org.graylog2.radio.transports.RadioTransport;
import org.joda.time.DateTime;
import org.msgpack.MessagePack;
import org.msgpack.packer.Packer;
import org.msgpack.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.msgpack.template.Templates.TInteger;
import static org.msgpack.template.Templates.TString;
import static org.msgpack.template.Templates.tMap;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class KafkaProducer implements RadioTransport {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducer.class);

    public final static String KAFKA_TOPIC = "graylog2-radio-messages";

    private final Producer<byte[], byte[]> producer;

    private final MessagePack msgPack;

    public KafkaProducer(Radio radio) {
        msgPack = new MessagePack();

        Properties props = new Properties();
        props.put("metadata.broker.list", radio.getConfiguration().getKafkaBrokers());
        props.put("partitioner.class", "kafka.producer.DefaultPartitioner");
        props.put("serializer.class", "kafka.serializer.DefaultEncoder");
        props.put("request.required.acks", String.valueOf(radio.getConfiguration().getKafkaRequiredAcks()));
        props.put("client.id", "graylog2-radio-" + radio.getNodeId());
        props.put("producer.type", radio.getConfiguration().getKafkaProducerType());
        props.put("queue.buffering.max.ms", String.valueOf(radio.getConfiguration().getKafkaBatchMaxWaitMs()));
        props.put("batch.num.messages", String.valueOf(radio.getConfiguration().getKafkaBatchSize()));

        ProducerConfig config = new ProducerConfig(props);
        producer = new Producer<byte[], byte[]>(config);
    }

    @Override
    public void send(Message msg) {
        KeyedMessage<byte[], byte[]> data;

        try {
            data = new KeyedMessage<byte[], byte[]>(KAFKA_TOPIC, msg.getId().getBytes(), serialize(msg));
        } catch(IOException e) {
            LOG.error("Could not serialize message.");
            return;
        }

        producer.send(data);
    }

    public byte[] serialize(Message msg) throws IOException {
        Map<String, Long> longs = Maps.newHashMap();
        Map<String, String> strings = Maps.newHashMap();

        for(Map.Entry<String, Object> field : msg.getFields().entrySet()) {
            if (field.getValue() instanceof String) {
                strings.put(field.getKey(), (String) field.getValue());
            } else if (field.getValue() instanceof Long) {
                longs.put(field.getKey(), (Long) field.getValue());
            } else if (field.getValue() instanceof Boolean) {
                strings.put(field.getKey(), ((Boolean) field.getValue()).toString());
            }
        }

        RadioMessage radioMessage = new RadioMessage();
        radioMessage.strings = strings;
        radioMessage.longs = longs;
        radioMessage.timestamp = ((DateTime) msg.getField("timestamp")).getMillis();

        return msgPack.write(radioMessage);
    }

}
