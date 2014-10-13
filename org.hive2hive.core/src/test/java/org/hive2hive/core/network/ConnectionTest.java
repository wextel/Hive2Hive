package org.hive2hive.core.network;

import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.api.configs.NetworkConfiguration;
import org.hive2hive.core.api.interfaces.INetworkConfiguration;
import org.hive2hive.core.events.EventBus;
import org.hive2hive.core.security.H2HDummyEncryption;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConnectionTest extends H2HJUnitTest {

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = ConnectionTest.class;
		beforeClass();
	}

	@Test
	public void testConnectAsInitial() {
		INetworkConfiguration netConfig = NetworkConfiguration.createInitial("initial node");
		NetworkManager initialNode = new NetworkManager(netConfig, new H2HDummyEncryption(), new EventBus());

		try {
			assertTrue(initialNode.connect());
		} finally {
			initialNode.disconnect();
		}
	}

	@Test
	public void testConnectToOtherPeer() throws UnknownHostException {
		INetworkConfiguration netConfigA = NetworkConfiguration.createInitial("nodeA");
		INetworkConfiguration netConfigB = NetworkConfiguration.create("nodeB", InetAddress.getLocalHost());

		NetworkManager nodeA = new NetworkManager(netConfigA, new H2HDummyEncryption(), new EventBus());
		NetworkManager nodeB = new NetworkManager(netConfigB, new H2HDummyEncryption(), new EventBus());

		try {
			assertTrue(nodeA.connect());
			assertTrue(nodeB.connect());
		} finally {
			nodeA.disconnect();
			nodeB.disconnect();
		}
	}

	@AfterClass
	public static void cleanAfterClass() {
		afterClass();
	}
}
