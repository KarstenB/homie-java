package io.github.dschanoeh.homie_java;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MainTest {

    private static final String FIRMWARE_NAME = "TestFirmware";
    private static final String FIRMWARE_VERSION = "1.0";
    private static final String DEVICE_ID = "my-test-device2";
    private static final String DEVICE_NAME = "My Device Name";
    private static final String TEST_BROKER_URL = "tcp://127.0.0.1:1883";
    private static final String TEST_NODE = "test-node";
    private static final String TEST_NODE_NAME = "My Test Node 3 %";
    private static final String TEST_PROPERTY = "test-property";
    private static final String TEST_UNIT = "test-unit";
    private static final String TEST_NODE_TYPE = "test-node-type";

    private final Homie homie;
    private MqttClient client;

    public MainTest() {
        Configuration c = new Configuration();
        c.setBrokerUrl(TEST_BROKER_URL);
        c.setDeviceID(DEVICE_ID);
        c.setDeviceName(DEVICE_NAME);
        homie = new Homie(c, FIRMWARE_NAME, FIRMWARE_VERSION);
    }

    @BeforeEach
    void initializeClient() throws MqttException {
        client = new MqttClient(TEST_BROKER_URL, "ClientID", new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        client.connect(options);
    }

    @AfterEach
    void shutdownClient() throws MqttException {
        client.disconnect();
        homie.shutdown();
    }

    @Test
    void disconnectConnectTest() throws InterruptedException {
        homie.setup();
        while(homie.getState() != Homie.State.READY) {
            Thread.sleep(50);
        }
        homie.shutdown();
        while(homie.getState() != Homie.State.INIT) {
            Thread.sleep(50);
        }
        homie.setup();
        while(homie.getState() != Homie.State.READY) {
            Thread.sleep(50);
        }
    }

    @Test
    void testDeviceName() throws MqttException {
        final Boolean[] wasReceived = {false};

        IMqttMessageListener listener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                if(payload.equals(DEVICE_NAME)) {
                    wasReceived[0] = true;
                }
            }
        };

        assert client.isConnected();

        String topic = "homie/" + DEVICE_ID + "/$name";

        client.subscribe(topic, listener);
        homie.setup();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assert wasReceived[0];
    }

    @Test
    void testNode() throws MqttException, InterruptedException {
        final Boolean[] wasReceived = {false, false, false, false};

        IMqttMessageListener unitListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals(TEST_UNIT)) {
                    wasReceived[0] = true;
                }
            }
        };

        IMqttMessageListener datatypeListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals(Property.DataType.FLOAT.toString().toLowerCase())) {
                    wasReceived[1] = true;
                }
            }
        };

        IMqttMessageListener settableListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals("false")) {
                    wasReceived[2] = true;
                }
            }
        };

        IMqttMessageListener nodeTypeListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals(TEST_NODE_TYPE)) {
                    wasReceived[3] = true;
                }
            }
        };

        assert client.isConnected();
        assert homie.getState() == Homie.State.INIT;

        String unitTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "/$unit";
        String datatypeTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "/$datatype";
        String settableTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "/$settable";
        String nodeTypeTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/$type";

        client.subscribe(unitTopic, unitListener);
        client.subscribe(datatypeTopic, datatypeListener);
        client.subscribe(settableTopic, settableListener);
        client.subscribe(nodeTypeTopic, nodeTypeListener);

        Node node = homie.createNode(TEST_NODE, TEST_NODE_TYPE);
        node.setName(TEST_NODE_NAME);
        Property property = node.getProperty(TEST_PROPERTY);
        property.setUnit(TEST_UNIT);
        property.setDataType(Property.DataType.FLOAT);

        homie.setup();

        while(homie.getState() != Homie.State.READY) {
            Thread.sleep(50);
        }

        assert wasReceived[0];
        assert wasReceived[1];
        assert wasReceived[2];
        assert wasReceived[3];
    }

    @Test
    void testSettableProperty() throws MqttException, InterruptedException {
        final Boolean[] wasSet = {false};
        final Boolean[] wasReceived = {false};

        PropertySetCallback callback = new PropertySetCallback() {
            @Override
            public void performSet(Property property, String value) {
                if(Boolean.valueOf(value)) {
                    wasSet[0] = true;
                }
            }
        };

        IMqttMessageListener settableListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals("true")) {
                    wasReceived[0] = true;
                }
            }
        };

        assert client.isConnected();

        String setTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "/set";
        String settableTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "/$settable";

        client.subscribe(settableTopic, settableListener);

        Node node = homie.createNode(TEST_NODE,"String");
        node.setName(TEST_NODE_NAME);
        Property property = node.getProperty(TEST_PROPERTY);
        property.setUnit(TEST_UNIT);
        property.setDataType(Property.DataType.BOOLEAN);
        property.makeSettable(callback);

        homie.setup();
        while(homie.getState() != Homie.State.READY) {
            Thread.sleep(50);
        }

        MqttMessage m = new MqttMessage();
        m.setPayload("true".getBytes());
        client.publish(setTopic, m);

        Thread.sleep(100);

        assert wasSet[0];
        assert wasReceived[0];
    }

    @Test
    void testPropertyTypes() throws MqttException, InterruptedException {
        final Boolean[] wasReceived = {false, false, false, false};

        Double TEST_VAL_1 = 0.1;
        Boolean TEST_VAL_2 = false;

        IMqttMessageListener val1Listener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                Double val = Double.valueOf(payload);
                if((val - TEST_VAL_1) < 0.0001) {
                    wasReceived[0] = true;
                }
            }
        };

        IMqttMessageListener val2Listener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                Boolean val = Boolean.valueOf(payload);
                if(val == TEST_VAL_2) {
                    wasReceived[1] = true;
                }
            }
        };

        IMqttMessageListener val1DatatypeListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals("float")) {
                    wasReceived[2] = true;
                }
            }
        };

        IMqttMessageListener val2DatatypeListener = new IMqttMessageListener() {
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                String payload = new String(message.getPayload());
                if(payload.equals("boolean")) {
                    wasReceived[3] = true;
                }
            }
        };

        assert client.isConnected();
        assert homie.getState() == Homie.State.INIT;

        String val1Topic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY;
        String val2Topic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "2";
        String val1DatatypeTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "/$datatype";
        String val2DatatypeTopic = "homie/" + DEVICE_ID + "/" + TEST_NODE + "/" + TEST_PROPERTY + "2" + "/$datatype";


        client.subscribe(val1Topic, val1Listener);
        client.subscribe(val2Topic, val2Listener);
        client.subscribe(val1DatatypeTopic, val1DatatypeListener);
        client.subscribe(val2DatatypeTopic, val2DatatypeListener);

        Node node = homie.createNode(TEST_NODE, TEST_NODE_TYPE);
        node.setName(TEST_NODE_NAME);

        Property property = node.getProperty(TEST_PROPERTY);
        property.setUnit(TEST_UNIT);
        property.setDataType(Property.DataType.FLOAT);

        Property property2 = node.getProperty(TEST_PROPERTY + "2");
        property2.setUnit(TEST_UNIT);
        property2.setDataType(Property.DataType.BOOLEAN);

        homie.setup();

        while(homie.getState() != Homie.State.READY) {
            Thread.sleep(50);
        }

        property.send(TEST_VAL_1);
        property2.send(TEST_VAL_2);

        /* Test sending with an illegal datatype */
        Boolean exceptionThrown = false;
        try {
            property2.send(TEST_VAL_1);
        } catch (Exception ex) {
            exceptionThrown = true;
        }
        assert exceptionThrown;

        Thread.sleep(100);

        assert wasReceived[0];
        assert wasReceived[1];
        assert wasReceived[2];
        assert wasReceived[3];
    }

    @Test
    void topicIDTest() {
        assert Homie.isValidTopicID("test-topic");
        assert Homie.isValidTopicID("test-topic2");
        assert Homie.isValidTopicID("test2topic");
        assert !Homie.isValidTopicID("-test-topic");
        assert !Homie.isValidTopicID("-test-topic-");
        assert !Homie.isValidTopicID("$test-topic");
        assert !Homie.isValidTopicID("test&-topic");
        assert !Homie.isValidTopicID("Test-topic");
    }
}
