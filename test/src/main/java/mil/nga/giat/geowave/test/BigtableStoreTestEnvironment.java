package mil.nga.giat.geowave.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.log4j.Logger;

import mil.nga.giat.geowave.core.store.DataStore;
import mil.nga.giat.geowave.core.store.GenericStoreFactory;
import mil.nga.giat.geowave.core.store.StoreFactoryOptions;
import mil.nga.giat.geowave.datastore.hbase.HBaseDataStoreFactory;
import mil.nga.giat.geowave.datastore.hbase.operations.config.HBaseOptions;
import mil.nga.giat.geowave.datastore.hbase.operations.config.HBaseRequiredOptions;
import mil.nga.giat.geowave.test.annotation.GeoWaveTestStore.GeoWaveStoreType;

public class BigtableStoreTestEnvironment extends
		StoreTestEnvironment
{
	private static final GenericStoreFactory<DataStore> STORE_FACTORY = new HBaseDataStoreFactory();
	private static BigtableStoreTestEnvironment singletonInstance = null;

	public static synchronized BigtableStoreTestEnvironment getInstance() {
		if (singletonInstance == null) {
			singletonInstance = new BigtableStoreTestEnvironment();
		}
		return singletonInstance;
	}

	private final static Logger LOGGER = Logger.getLogger(
			BigtableStoreTestEnvironment.class);

	protected String zookeeper;

	private BigtableStoreTestEnvironment() {}

	@Override
	protected void initOptions(
			final StoreFactoryOptions options ) {
		HBaseOptions hbaseOptions = ((HBaseRequiredOptions) options).getAdditionalOptions();
		hbaseOptions.setBigtable(
				true);
		hbaseOptions.setEnableCustomFilters(
				false);
		hbaseOptions.setEnableCoprocessors(
				false);

		((HBaseRequiredOptions) options).setZookeeper(
				zookeeper);
	}

	@Override
	protected GenericStoreFactory<DataStore> getDataStoreFactory() {
		return STORE_FACTORY;
	}

	@Override
	protected GeoWaveStoreType getStoreType() {
		return GeoWaveStoreType.BIGTABLE;
	}

	@Override
	public void setup() {
		if (!TestUtils.isSet(
				zookeeper)) {
			zookeeper = System.getProperty(
					ZookeeperTestEnvironment.ZK_PROPERTY_NAME);

			if (!TestUtils.isSet(
					zookeeper)) {
				zookeeper = ZookeeperTestEnvironment.getInstance().getZookeeper();
				LOGGER.debug(
						"Using local zookeeper URL: " + zookeeper);
			}
		}

		// Bigtable IT's rely on an external gcloud emulator
		initGcloud();
	}

	// Currently being run from travis externally
	private void initGcloud() {
		String processDir = System.getProperty(
				"user.dir");
		LOGGER.warn(
				"KAM >>> Running gcloud install in " + processDir);

		int rc = -1;
		
		try {
			rc = executeCommand(
					processDir + "/gcloud-init.sh");
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		LOGGER.warn(
				"KAM >>> exit code: " + rc );
	}

	/**
	 * Using apache commons exec for cmd line execution
	 * @param command
	 * @return exitCode
	 * @throws ExecuteException
	 * @throws IOException
	 */
	private int executeCommand(
			String command ) throws ExecuteException, IOException {
		CommandLine commandLine = CommandLine.parse(command);
		DefaultExecutor executor = new DefaultExecutor();
		
		ExecuteWatchdog watchdog = new ExecuteWatchdog(30000);
		executor.setWatchdog(watchdog);
		
		return executor.execute(commandLine);
	}

	@Override
	public void tearDown() {}

	@Override
	public TestEnvironment[] getDependentEnvironments() {
		return new TestEnvironment[] {
			ZookeeperTestEnvironment.getInstance()
		};
	}
}
