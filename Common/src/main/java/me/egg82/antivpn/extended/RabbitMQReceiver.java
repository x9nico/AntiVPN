package me.egg82.antivpn.extended;

import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import me.egg82.antivpn.services.InternalAPI;
import me.egg82.antivpn.services.RabbitMQ;
import me.egg82.antivpn.utils.RabbitMQUtil;
import me.egg82.antivpn.utils.ValidationUtil;
import ninja.egg82.json.JSONUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQReceiver {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Connection connection = null;
    private Channel channel = null;

    public RabbitMQReceiver(ConnectionFactory factory) {
        try {
            connection = RabbitMQUtil.getConnection(factory);
            channel = RabbitMQUtil.getChannel(connection);

            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("antivpn-result", "fanout");
            channel.exchangeDeclare("antivpn-consensus", "fanout");
            channel.exchangeDeclare("antivpn-delete", "fanout");

            String resultQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(resultQueueName, "antivpn-result", "");

            String consensusQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(consensusQueueName, "antivpn-consensus", "");

            String deleteQueueName = channel.queueDeclare().getQueue();
            channel.queueBind(deleteQueueName, "antivpn-delete", "");

            Consumer resultConsumer = new DefaultConsumer(channel) {
                public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");

                    try {
                        JSONObject obj = JSONUtil.parseObject(message);
                        String ip = (String) obj.get("ip");
                        boolean value = (Boolean) obj.get("value");
                        long created = ((Number) obj.get("created")).longValue();
                        UUID id = UUID.fromString((String) obj.get("id"));

                        if (!ValidationUtil.isValidIp(ip)) {
                            logger.warn("non-valid IP sent through RabbitMQ cascade");
                            return;
                        }

                        if (id.equals(RabbitMQ.getServerID())) {
                            logger.info("ignoring message sent from this server");
                            return;
                        }

                        CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        Configuration config = ServiceLocator.get(Configuration.class);

                        InternalAPI.set(ip, value, created, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(resultQueueName, true, resultConsumer);

            Consumer consensusConsumer = new DefaultConsumer(channel) {
                public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");

                    try {
                        JSONObject obj = JSONUtil.parseObject(message);
                        String ip = (String) obj.get("ip");
                        double value = ((Number) obj.get("value")).doubleValue();
                        long created = ((Number) obj.get("created")).longValue();
                        UUID id = UUID.fromString((String) obj.get("id"));

                        if (!ValidationUtil.isValidIp(ip)) {
                            logger.warn("non-valid IP sent through RabbitMQ consensus");
                            return;
                        }

                        if (id.equals(RabbitMQ.getServerID())) {
                            logger.info("ignoring message sent from this server");
                            return;
                        }

                        CachedConfigValues cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        Configuration config = ServiceLocator.get(Configuration.class);

                        InternalAPI.set(ip, value, created, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                    } catch (ParseException | ClassCastException | NullPointerException | IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                    }
                }
            };
            channel.basicConsume(consensusQueueName, true, consensusConsumer);

            Consumer deleteConsumer = new DefaultConsumer(channel) {
                public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties properies, byte[] body) throws IOException {
                    String message = new String(body, "UTF-8");

                    CachedConfigValues cachedConfig;
                    Configuration config;

                    try {
                        cachedConfig = ServiceLocator.get(CachedConfigValues.class);
                        config = ServiceLocator.get(Configuration.class);
                    } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
                        logger.error(ex.getMessage(), ex);
                        return;
                    }

                    // In this case, the message is the "IP"
                    InternalAPI.delete(message, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType());
                }
            };
            channel.basicConsume(deleteQueueName, true, deleteConsumer);
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public void close() throws IOException, TimeoutException {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (AlreadyClosedException ignored) {}
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (AlreadyClosedException ignored) {}
    }
}