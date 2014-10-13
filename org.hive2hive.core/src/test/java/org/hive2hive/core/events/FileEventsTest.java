package org.hive2hive.core.events;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;

import org.apache.commons.io.FileUtils;
import org.hive2hive.core.H2HJUnitTest;
import org.hive2hive.core.events.framework.interfaces.IFileEventListener;
import org.hive2hive.core.events.framework.interfaces.file.IFileDeleteEvent;
import org.hive2hive.core.events.framework.interfaces.file.IFileDownloadEvent;
import org.hive2hive.core.events.framework.interfaces.file.IFileEvent;
import org.hive2hive.core.events.framework.interfaces.file.IFileMoveEvent;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.file.FileTestUtil;
import org.hive2hive.core.network.NetworkManager;
import org.hive2hive.core.network.NetworkTestUtil;
import org.hive2hive.core.processes.util.UseCaseTestUtil;
import org.hive2hive.core.security.UserCredentials;
import org.hive2hive.processframework.util.H2HWaiter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class FileEventsTest extends H2HJUnitTest {
	
	protected static final int networkSize = 6;
	protected static ArrayList<NetworkManager> network;
	protected static UserCredentials userCredentials;
	protected final static int CHUNK_SIZE = 1024;
	protected static File rootA;
	protected static File rootB;
	protected static NetworkManager clientA;
	protected static NetworkManager clientB;
	
	protected static FileEventAggregatorStub listener; 

	@BeforeClass
	public static void initTest() throws Exception {
		testClass = FileEventsTest.class;
		beforeClass();
		
		network = NetworkTestUtil.createNetwork(networkSize);
		userCredentials = NetworkTestUtil.generateRandomCredentials();

		rootA = FileTestUtil.getTempDirectory();
		rootB = FileTestUtil.getTempDirectory();
		clientA = network.get(0);
		clientB = network.get(1);

		// register a user and login (twice)
		UseCaseTestUtil.registerAndLogin(userCredentials, clientA, rootA);
		UseCaseTestUtil.login(userCredentials, clientB, rootB);
		
		 listener = new FileEventAggregatorStub();
		// register file events on machine B
		clientB.getEventBus().subscribe(listener);
	}
	
	@AfterClass
	public static void endTest() {
		NetworkTestUtil.shutdownNetwork(network);
		try {
			FileUtils.deleteDirectory(rootA);
			FileUtils.deleteDirectory(rootB);
		} catch(IOException ioex) {
			logger.error("Could not cleanup folders.", ioex);
		}
		
		afterClass();
	}
	
	@Before
	public void beforeTest() {
		// clear events from previous test case
		listener.getEvents().clear();
	}
	
	@After
	public void afterTest() {
		try {
			FileUtils.cleanDirectory(rootA);
			FileUtils.cleanDirectory(rootB);
		} catch (IOException ioex) {
			logger.error("Could not cleanup directories.", ioex);
		}
	}
	
	@Test @Ignore
	public void testFileShareEvent() throws NoPeerConnectionException, IOException, NoSessionException {
		// TODO
		fail("not implemented yet");
	}
	
	/**
	 * Verify the equality of two absolute paths by looking at their relative parts (relative to
	 * the root folders)
	 * @param absA absolute path (client A)
	 * @param absB absolute path (client B)
	 */
	protected void assertEqualsRelativePaths(Path absA, Path absB) {
		Path relativeA = rootA.toPath().relativize(absA);
		Path relativeB = rootB.toPath().relativize(absB);
		logger.debug("Path comparison: '{}' vs '{}'", relativeA, relativeB);
		assertTrue(relativeA.equals(relativeB));
	}
	
	protected File createAndAddFile(File root, NetworkManager client) throws IOException, NoSessionException, NoPeerConnectionException {
		File file = FileTestUtil.createFileRandomContent(3, root, CHUNK_SIZE);
		UseCaseTestUtil.uploadNewFile(client, file);
		return file;
	}
	
	protected File createAndAddFolder(File root, NetworkManager client) throws NoSessionException, NoPeerConnectionException {
		File folder = new File(root, "folder-" + generateRandomString(5));
		assertTrue(folder.mkdirs());
		UseCaseTestUtil.uploadNewFile(client, folder);
		return folder;
	}
	
	protected List<File> createAndAddFolderWithFiles(File root, NetworkManager client) throws NoSessionException, NoPeerConnectionException, IOException {
		List<File> files = new ArrayList<File>();
		
		// create folder and upload
		File folder = createAndAddFolder(root, client);
		files.add(folder);
		
		// create files and upload them
		int numFiles = 10;
		for(int i = 0; i < numFiles; ++i) {
			File file = createAndAddFile(folder, client);
			files.add(file);
		}
				
		return files;
	}
	
	protected void assertEventType(List<?> events, Class<?> type) {
		for(Object e : events) {
			assertTrue(type.isInstance(e));
		}
	}
	
	protected void waitForNumberOfEvents(int numberOfEvents) {
		H2HWaiter waiter = new H2HWaiter(60);
		do {
			waiter.tickASecond();
			logger.debug("Number of events received: {}", listener.getEvents().size());
		} while(listener.getEvents().size() < numberOfEvents);
	}
	
	protected static class FileEventAggregatorStub implements IFileEventListener {
		private List<IFileEvent> events = new ArrayList<IFileEvent>();
		public List<IFileEvent> getEvents() { return events; }
		
		@Override @Handler
		public void onFileDelete(IFileDeleteEvent fileEvent) {
			events.add(fileEvent);
		}

		@Override @Handler
		public void onFileDownload(IFileDownloadEvent fileEvent) {
			events.add(fileEvent);
		}

		@Override @Handler
		public void onFileMove(IFileMoveEvent fileEvent) {
			events.add(fileEvent);
		}
	}

}
